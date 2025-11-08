package com.jorgetp.notifications;

import android.annotation.SuppressLint;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.graphics.drawable.Drawable;
import android.os.Bundle;
import android.util.Log;
import android.util.Pair;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.widget.PopupMenu;

import androidx.activity.EdgeToEdge;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.view.menu.MenuBuilder;
import androidx.core.graphics.Insets;
import androidx.core.view.MenuCompat;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.jorgetp.notifications.adapter.NotificationsAdapter;
import com.jorgetp.notifications.dao.DbProvider;
import com.jorgetp.notifications.dao.IconDao;
import com.jorgetp.notifications.dao.NotificationDao;
import com.jorgetp.notifications.dao.StoredNotification;

import java.io.Serializable;
import java.text.SimpleDateFormat;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Comparator;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.TreeSet;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class MainActivity extends AppCompatActivity {
    public static final String CHANNEL_ID = "com.jorgetp.notifications";
    public static final String SILENCED_APPS_PREFS = "Notifications-Silenced-Apps";
    public static final String IMPORTANT_SENDERS_PREFS = "Notifications-Important-Senders";
    public static final int ALWAYS = 1001;
    public static final int NON_BUSINESS = 1002;

    private ExecutorService executor;
    private long lastPauseTimestamp = Long.MAX_VALUE;
    private NotificationDao notificationDao;

    private NotificationsAdapter notificationsAdapter;
    private String selectedPackage = "all";

    private final ActivityResultLauncher<Intent> launcher = registerForActivityResult(
            new ActivityResultContracts.StartActivityForResult(), result -> {
                if (result.getResultCode() == RESULT_OK && result.getData() != null) {
                    Serializable editedItems = result.getData().getSerializableExtra("edited_items");
                    if (editedItems instanceof TreeSet)
                        getNotificationsAndRefreshUI();
                }
            });

    public static SharedPreferences getPrefs(Context context, String name) {
        return context.getApplicationContext().getSharedPreferences(name, Context.MODE_PRIVATE);
    }

    public static Pair<CharSequence, Drawable> getAppInfo(Context context, String packageName) {
        try {
            PackageManager pm = context.getApplicationContext().getPackageManager();
            ApplicationInfo appInfo = pm.getApplicationInfo(packageName, 0);
            CharSequence appName = pm.getApplicationLabel(appInfo);
            return Pair.create(appName, pm.getApplicationIcon(appInfo));

        } catch (PackageManager.NameNotFoundException e) {
            Log.e("NotificationService", "App not found", e);
        }
        return Pair.create(packageName, null);
    }

    public static boolean isSameDay(Date date1, Date date2) {
        Calendar cal1 = Calendar.getInstance();
        cal1.setTime(date1);
        Calendar cal2 = Calendar.getInstance();
        cal2.setTime(date2);
        return cal1.get(Calendar.YEAR) == cal2.get(Calendar.YEAR) &&
                cal1.get(Calendar.DAY_OF_YEAR) == cal2.get(Calendar.DAY_OF_YEAR);
    }

    public static Date toDate(long timestamp) {
        try {
            return new Date(timestamp);
        } catch (NumberFormatException e) {
            return null;
        }
    }

    public static boolean isToday(Date date) {
        LocalDate givenDate = date.toInstant().atZone(ZoneId.systemDefault()).toLocalDate();
        LocalDate today = LocalDate.now();
        return givenDate.equals(today);
    }

    public static boolean isYesterday(Date date) {
        LocalDate givenDate = date.toInstant().atZone(ZoneId.systemDefault()).toLocalDate();
        LocalDate yesterday = LocalDate.now().minusDays(1);
        return givenDate.equals(yesterday);
    }

    // Helper method to clean up orphaned icons
    private void cleanupOrphanedIcons() {
        IconDao iconDao = DbProvider.get(getApplicationContext()).notificationIconDao();
        SharedPreferences importantSendersPrefs = getPrefs(this, IMPORTANT_SENDERS_PREFS);

        // Get all important sender keys
        List<String> importantSenderKeys = new ArrayList<>(importantSendersPrefs.getAll().keySet());

        // If no important senders exist, delete all icons
        if (importantSenderKeys.isEmpty()) {
            iconDao.deleteAll();
        } else {
            // Delete icons that don't match any important sender
            iconDao.deleteOrphanedIcons(importantSenderKeys);
        }
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_main);
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main), (v, insets) -> {
            Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom);
            return insets;
        });
        androidx.appcompat.widget.Toolbar toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);

        executor = Executors.newSingleThreadExecutor();

        RecyclerView rvNotifications = findViewById(R.id.rvNotifications);
        rvNotifications.setLayoutManager(new LinearLayoutManager(this));
        rvNotifications.setAdapter(notificationsAdapter = new NotificationsAdapter(this));

        notificationDao = DbProvider.get(getApplicationContext()).notificationDao();
        notificationDao.observeLast().observe(this, last -> {
            if (last != null && (selectedPackage.equals("all") || selectedPackage.equals(last.packageName)))
                getNotificationsAndRefreshUI();
        });
    }

    @Override
    protected void onResume() {
        super.onResume();
        // refresh if 10 mins have elapsed from last pause
        if (System.currentTimeMillis() - lastPauseTimestamp > 10 * 60 * 1000) {
            selectedPackage = "all";
            getNotificationsAndRefreshUI();
        }
    }

    @Override
    protected void onPause() {
        super.onPause();
        // save timestamp
        lastPauseTimestamp = System.currentTimeMillis();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        executor.shutdown();
    }

    @SuppressLint("RestrictedApi")
    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        MenuInflater inflater = getMenuInflater();
        inflater.inflate(R.menu.menu_main_activity, menu);
        MenuCompat.setGroupDividerEnabled(menu, true);
        if (menu instanceof MenuBuilder) {
            MenuBuilder m = (MenuBuilder) menu;
            m.setOptionalIconsVisible(true);
        }
        return super.onCreateOptionsMenu(menu);
    }

    @Override
    public boolean onOptionsItemSelected(@NonNull MenuItem item) {
        int itemId = item.getItemId();
        if (itemId == R.id.menu_filter) {
            Executors.newSingleThreadExecutor().execute(() -> {
                // Add a popup menu with the app names as filters
                class AppPack {
                    final String packageName;
                    final CharSequence displayName;
                    final Drawable icon;

                    public AppPack(String packageName, CharSequence displayName, Drawable icon) {
                        this.packageName = packageName;
                        this.displayName = displayName;
                        this.icon = icon;
                    }

                    public String getPackageName() {
                        return packageName;
                    }

                    public CharSequence getDisplayName() {
                        return displayName;
                    }

                    public Drawable getIcon() {
                        return icon;
                    }
                }
                List<String> packages = notificationDao.getPackages();
                ArrayList<AppPack> appPacks = new ArrayList<>(packages.size());

                for (String packageName : packages) {
                    Pair<CharSequence, Drawable> appInfo = getAppInfo(MainActivity.this, packageName);
                    appPacks.add(new AppPack(packageName, appInfo.first, appInfo.second));
                }
                appPacks.sort(Comparator.comparing(o -> o.getDisplayName().toString().toLowerCase()));

                // Add "All" option at the beginning
                appPacks.add(0, new AppPack(
                        "all",
                        getString(R.string.all),
                        getDrawable(R.drawable.outline_apps_24)));

                final int idOffset = 504321;

                runOnUiThread(() -> {
                    PopupMenu popup = new PopupMenu(MainActivity.this, findViewById(R.id.menu_filter));

                    for (int i = 0; i < appPacks.size(); i++) {
                        MenuItem menuItem = popup.getMenu().add(
                                Menu.NONE,
                                idOffset + i,
                                Menu.NONE,
                                appPacks.get(i).getDisplayName());

                        // Set icon for each menu item
                        Drawable icon = appPacks.get(i).getIcon();
                        if (icon == null)
                            icon = getDrawable(android.R.drawable.sym_def_app_icon);

                        // Resize icon to appropriate size
                        icon.setBounds(0, 0, 64, 64); // 32dp in pixels approximately
                        menuItem.setIcon(icon);
                    }

                    // Force show icons in popup menu
                    try {
                        java.lang.reflect.Field field = popup.getClass().getDeclaredField("mPopup");
                        field.setAccessible(true);
                        Object menuPopupHelper = field.get(popup);
                        Class<?> classPopupHelper = Class.forName(menuPopupHelper.getClass().getName());
                        java.lang.reflect.Method setForceIcons = classPopupHelper.getMethod("setForceShowIcon", boolean.class);
                        setForceIcons.invoke(menuPopupHelper, true);
                    } catch (Exception e) {
                        // Ignore reflection errors - icons just won't show
                    }

                    popup.setOnMenuItemClickListener(menuItem -> {
                        int position = menuItem.getItemId() - idOffset;
                        selectedPackage = appPacks.get(position).getPackageName();
                        getNotificationsAndRefreshUI();
                        return true;
                    });
                    popup.show();
                });
            });
            return true;

        } else if (itemId == R.id.menu_delete_all) {
            new AlertDialog.Builder(this)
                    .setMessage(R.string.menu_delete_all_confirmation)
                    .setPositiveButton(android.R.string.yes, (dialog, id) -> {
                        Executors.newSingleThreadExecutor().execute(() -> {
                            notificationDao.deleteAll();
                            cleanupOrphanedIcons();
                            getNotificationsAndRefreshUI();
                        });
                    })
                    .setNegativeButton(android.R.string.cancel, null)
                    .create()
                    .show();
            return true;

        } else if (itemId == R.id.menu_settings) {
            launcher.launch(new Intent(this, SettingsActivity.class));
            return true;

        }
        return false;
    }

    public void getNotificationsAndRefreshUI() {
        executor.submit(() -> {
            int limit = 50;
            List<StoredNotification> notifications = notificationDao.getByPackage(
                    "all".equals(selectedPackage) ? "%" : selectedPackage, limit);

            ArrayList<Object> items = new ArrayList<>(limit + 10);
            if ("all".equals(selectedPackage)) {
                // add pinned first
                List<StoredNotification> pinned = notificationDao.getPinned();
                if (!pinned.isEmpty()) {
                    items.add(getString(R.string.pinned));
                    items.addAll(pinned);
                }
            }

            Date previousDate = null;
            for (StoredNotification notification : notifications) {
                // if pinned and not filter, then skip as it's shown before
                if ("all".equals(selectedPackage) && notification.pinned)
                    continue;
                boolean addHeader = true;
                Date currentDate = toDate(notification.postTime);
                if (previousDate != null)
                    addHeader = !isSameDay(previousDate, currentDate);
                if (addHeader)
                    items.add(dateToHeader(currentDate));
                items.add(notification);
                previousDate = currentDate;
            }

            notificationsAdapter.setItems(items);
            runOnUiThread(() -> notificationsAdapter.notifyDataSetChanged());
        });
    }

    public String dateToHeader(Date date) {
        if (date != null) {
            if (isToday(date))
                return getString(R.string.today);
            else if (isYesterday(date))
                return getString(R.string.yesterday);
            else {
                SimpleDateFormat sdf = new SimpleDateFormat("EEE, d MMM yyyy", Locale.getDefault());
                // capitalize first letter
                char[] chars = sdf.format(date).toCharArray();
                chars[0] = Character.toUpperCase(chars[0]);
                return new String(chars);
            }
        }
        return "";
    }
}