package com.jorgetp.notifications;

import android.annotation.SuppressLint;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.graphics.drawable.Drawable;
import android.os.Bundle;
import android.provider.Settings;
import android.text.TextUtils;
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
import com.jorgetp.notifications.dao.NotificationDao;
import com.jorgetp.notifications.dao.StoredNotification;

import java.io.File;
import java.io.Serializable;
import java.text.SimpleDateFormat;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Comparator;
import java.util.Date;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.TreeSet;
import java.util.concurrent.Executors;

public class MainActivity extends AppCompatActivity {
    public static final String CHANNEL_ID = "com.jorgetp.notifications";
    public static final String SILENCED_APPS_PREFS = "Notifications-Silenced-Apps";
    public static final String IMPORTANT_SENDERS_PREFS = "Notifications-Important-Senders";
    public static final int ALWAYS = 1001;
    public static final int NON_BUSINESS = 1002;

    private long lastPauseTimestamp = Long.MAX_VALUE;
    private NotificationDao dao;

    private NotificationsAdapter notificationsAdapter;
    private String selectedPackage = "all";

    private final ActivityResultLauncher<Intent> launcher = registerForActivityResult(
            new ActivityResultContracts.StartActivityForResult(), result -> {
                if (result.getResultCode() == RESULT_OK && result.getData() != null) {
                    Serializable editedItems = result.getData().getSerializableExtra("edited_items");
                    if (editedItems instanceof TreeSet)
                        refreshDataAndUI();
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

    private boolean isNotificationServiceEnabled() {
        String pkgName = getPackageName();
        String enabledListeners = Settings.Secure.getString(getContentResolver(),
                "enabled_notification_listeners");
        if (!TextUtils.isEmpty(enabledListeners)) {
            String[] listeners = enabledListeners.split(":");
            for (String listener : listeners) {
                ComponentName cn = ComponentName.unflattenFromString(listener);
                if (cn != null && TextUtils.equals(pkgName, cn.getPackageName())) {
                    return true;
                }
            }
        }
        return false;
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

        if (isNotificationServiceEnabled()) {
            NotificationManager notificationManager = (NotificationManager) getSystemService(
                    Context.NOTIFICATION_SERVICE);
            NotificationChannel channel = new NotificationChannel(
                    CHANNEL_ID,
                    getString(R.string.app_name),
                    NotificationManager.IMPORTANCE_HIGH);
            notificationManager.createNotificationChannel(channel);
        } else {
            AlertDialog.Builder dialogBuilder = new AlertDialog.Builder(this)
                    .setTitle(R.string.app_name)
                    .setMessage(R.string.enable_as_nsl)
                    .setCancelable(false)
                    .setPositiveButton(android.R.string.ok,
                            (dialog, id) -> startActivity(
                                    new Intent("android.settings.ACTION_NOTIFICATION_LISTENER_SETTINGS")))
                    .setNegativeButton(android.R.string.cancel,
                            (dialog, id) -> MainActivity.this.finishAndRemoveTask());
            dialogBuilder.create().show();
        }

        RecyclerView rvNotifications = findViewById(R.id.rvNotifications);
        LinearLayoutManager lm = new LinearLayoutManager(this);
        rvNotifications.setLayoutManager(lm);
        rvNotifications.setAdapter(notificationsAdapter = new NotificationsAdapter(this));

        dao = DbProvider.get(getApplicationContext()).notificationDao();
        dao.observeLast().observe(this, last -> {
            if (last != null) {
                refreshDataAndUI();
            }
        });
    }

    @Override
    protected void onResume() {
        super.onResume();

        Executors.newSingleThreadExecutor().execute(() -> {
            try {
                if (dao.getLast().postTime > lastPauseTimestamp) {
                    // refresh if new notifications were posted when app was paused
                    refreshDataAndUI();
                }
            } catch (Exception e) {
                Log.e("Here", "Error getting latest notification", e);
            }

        });

        if (System.currentTimeMillis() - lastPauseTimestamp > 10 * 60 * 1000) {
            // refresh if 10 mins have elapsed from last pause
            selectedPackage = "all";
            refreshDataAndUI();
        }
    }

    @Override
    protected void onPause() {
        super.onPause();
        // save timestamp
        lastPauseTimestamp = System.currentTimeMillis();
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
        SharedPreferences importantSendersPrefs = getPrefs(this,
                IMPORTANT_SENDERS_PREFS);

        int itemId = item.getItemId();
        if (itemId == R.id.menu_filter) {
            Executors.newSingleThreadExecutor().execute(() -> {
                // add a popup menu with the app names as filters
                List<String> packages = dao.getPackages();
                ArrayList<Pair<String, String>> apps = new ArrayList<>(packages.size());
                for (String packageName : packages) {
                    Pair<CharSequence, Drawable> appInfo = getAppInfo(this, packageName);
                    apps.add(new Pair<>(packageName, appInfo.first.toString()));
                }
                apps.add(new Pair<>("all", getString(R.string.all)));
                apps.sort(Comparator.comparing(o -> o.second.toLowerCase()));

                runOnUiThread(() -> {
                    PopupMenu popup = new PopupMenu(MainActivity.this, findViewById(R.id.menu_filter));
                    for (int i = 0; i < apps.size(); i++)
                        popup.getMenu().add(Menu.NONE, 54321 + i, Menu.NONE, apps.get(i).second);

                    popup.setOnMenuItemClickListener(menuItem -> {
                        int position = menuItem.getItemId() - 54321;
                        selectedPackage = apps.get(position).first;
                        refreshDataAndUI();
                        return true;
                    });
                    popup.show();
                });
            });
            return true;

        } else if (itemId == R.id.menu_clear_all_except_today) {
            new AlertDialog.Builder(this)
                    .setMessage(R.string.menu_delete_all)
                    .setPositiveButton(android.R.string.yes, (dialog, id) -> {
                        Executors.newSingleThreadExecutor().execute(() -> {
                            dao.deleteAll();
                            // no need to refresh notifications view because it is already
                            // refreshed by the notificationsListener
                            // refreshDataAndUI();

                            // delete icons whose UUID is not linked to an important sender
                            HashSet<String> activeUUIDs = new HashSet<>(10);
                            for (Object uuid : importantSendersPrefs.getAll().values())
                                activeUUIDs.add(uuid.toString());

                            // delete files
                            for (File file : Objects.requireNonNull(getFilesDir().listFiles())) {
                                String fileName = file.getName();
                                if (!fileName.startsWith("notification_icon_"))
                                    continue;

                                String uuid = fileName.substring("notification_icon_".length(), fileName.length() - 4);
                                if (!activeUUIDs.contains(uuid)) {
                                    if (file.delete())
                                        Log.d("MainActivity", "Icon deleted: " + fileName);
                                }
                            }
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

    public void refreshDataAndUI() {
        Executors.newSingleThreadExecutor().execute(() -> {
            List<StoredNotification> notifications = "all".equals(selectedPackage)
                    ? dao.getAll() : dao.getForPackage(selectedPackage);

            ArrayList<Object> items = new ArrayList<>(1000);
            Date previousDate = null;
            for (StoredNotification notification : notifications) {
                boolean addHeader = true;
                Date currentDate = toDate(notification.postTime);
                if (previousDate != null) {
                    addHeader = !isSameDay(previousDate, currentDate);
                }
                if (addHeader)
                    items.add(dateToHeader(currentDate));
                items.add(notification);
                previousDate = currentDate;
            }

            runOnUiThread(() -> notificationsAdapter.updateData(items));
        });
    }

    public String dateToHeader(Date date) {
        if (date != null) {
            if (isToday(date)) {
                return getString(R.string.today);
            } else if (isYesterday(date)) {
                return getString(R.string.yesterday);
            } else {
                SimpleDateFormat sdf = new SimpleDateFormat("EEE, dd MMM yyyy", Locale.getDefault());
                return sdf.format(date);
            }
        }
        return "";
    }
}