package com.jorgetp.notifications;

import android.annotation.SuppressLint;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.provider.Settings;
import android.text.TextUtils;
import android.util.Log;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.widget.Toast;

import androidx.activity.EdgeToEdge;
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

import com.google.android.material.floatingactionbutton.FloatingActionButton;
import com.jorgetp.notifications.adapter.FilterAdapter;
import com.jorgetp.notifications.adapter.ImportantSendersAdapter;
import com.jorgetp.notifications.adapter.NotificationsAdapter;
import com.jorgetp.notifications.adapter.SilencedAppsAdapter;

import org.json.JSONException;
import org.json.JSONObject;

import java.io.File;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.AbstractMap;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.Executors;

public class MainActivity extends AppCompatActivity implements FilterAdapter.OnAppFilterClickListener {
    public static final String CHANNEL_ID = "com.jorgetp.notifications";
    public static final String NOTIFICATIONS_PREFS = "Notifications-Items";
    public static final String SILENCED_APPS_PREFS = "Notifications-Silenced-Apps";
    public static final String IMPORTANT_SENDERS_PREFS = "Notifications-Important-Senders";
    public static final int ALWAYS = 1001;
    public static final int NON_BUSINESS = 1002;

    private int lastNotificationsCountInPrefs;
    private SharedPreferences notificationsPrefs;
    private SharedPreferences.OnSharedPreferenceChangeListener notificationsListener;

    private RecyclerView rvNotifications;
    private RecyclerView rvFilter;
    private FilterAdapter filterAdapter;
    private NotificationsAdapter notificationsAdapter;
    private AllNotifications allNotifications;
    private String selectedPackage = "all";

    public static Date ToDate(long timestamp) {
        try {
            return new Date(timestamp);
        } catch (NumberFormatException e) {
            return null;
        }
    }

    public static boolean IsToday(Date date) {
        LocalDate givenDate = date.toInstant().atZone(ZoneId.systemDefault()).toLocalDate();
        LocalDate today = LocalDate.now();
        return givenDate.equals(today);
    }

    public static boolean IsYesterday(Date date) {
        LocalDate givenDate = date.toInstant().atZone(ZoneId.systemDefault()).toLocalDate();
        LocalDate yesterday = LocalDate.now().minusDays(1);
        return givenDate.equals(yesterday);
    }

    public static boolean IsInDndMode(Context context) {
        if (context == null) {
            return false;
        }

        // Check if the device is running Android 12 or higher
        if (android.os.Build.VERSION.SDK_INT < android.os.Build.VERSION_CODES.S) {
            return false;
        }

        NotificationManager notificationManager = (NotificationManager) context
                .getSystemService(Context.NOTIFICATION_SERVICE);
        if (notificationManager == null) {
            return false;
        }

        // Check the current interruption filter
        int interruptionFilter = notificationManager.getCurrentInterruptionFilter();
        return interruptionFilter == NotificationManager.INTERRUPTION_FILTER_NONE ||
                interruptionFilter == NotificationManager.INTERRUPTION_FILTER_ALARMS ||
                interruptionFilter == NotificationManager.INTERRUPTION_FILTER_PRIORITY;
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

        notificationsPrefs = getSharedPreferences(NOTIFICATIONS_PREFS, Context.MODE_PRIVATE);
        notificationsListener = (sharedPreferences, key) -> refreshContent(true);

        rvNotifications = findViewById(R.id.rvNotifications);
        rvFilter = findViewById(R.id.rvFilter);
        setupNotificationsView();
        refreshContent(true);

        FloatingActionButton fabScrollToTop = findViewById(R.id.fabScroll);
        fabScrollToTop.setOnClickListener(view -> {
            rvNotifications.smoothScrollToPosition(0);
        });
    }

    @Override
    protected void onResume() {
        super.onResume();
        // to refresh UI on new notifications when app was paused
        int newCount = getSharedPreferences(NOTIFICATIONS_PREFS, Context.MODE_PRIVATE)
                .getAll().size();
        if (newCount != lastNotificationsCountInPrefs) {
            refreshContent(true);
            lastNotificationsCountInPrefs = newCount;
        }

        // to refresh UI on new notifications when app is active
        notificationsPrefs.registerOnSharedPreferenceChangeListener(notificationsListener);
    }

    @Override
    protected void onPause() {
        super.onPause();
        // unregister listener
        notificationsPrefs.unregisterOnSharedPreferenceChangeListener(notificationsListener);
    }

    @SuppressLint("RestrictedApi")
    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        MenuInflater inflater = getMenuInflater();
        inflater.inflate(R.menu.main_activity_menu, menu);
        MenuCompat.setGroupDividerEnabled(menu, true);
        if (menu instanceof MenuBuilder) {
            MenuBuilder m = (MenuBuilder) menu;
            m.setOptionalIconsVisible(true);
        }
        return super.onCreateOptionsMenu(menu);
    }

    @Override
    public boolean onOptionsItemSelected(@NonNull MenuItem item) {
        SharedPreferences importantSendersPrefs = getSharedPreferences(IMPORTANT_SENDERS_PREFS,
                Context.MODE_PRIVATE);

        int itemId = item.getItemId();
        if (itemId == R.id.menu_clear_all_except_today) {
            new AlertDialog.Builder(this)
                    .setMessage(R.string.menu_clear_all_except_today_confirmation)
                    .setPositiveButton(android.R.string.yes, (dialog, id) -> {
                        for (Map.Entry<String, ?> entry : notificationsPrefs.getAll().entrySet()) {
                            try {
                                JSONObject notification = new JSONObject(entry.getValue().toString());
                                long postTime = notification.optLong("postTime");
                                Date date = ToDate(postTime);
                                if (!IsToday(date)) {
                                    notificationsPrefs.edit().remove(entry.getKey()).apply();
                                }
                            } catch (JSONException e) {
                                Log.e("MainActivity", "JSON error", e);
                            }
                        }
                        refreshContent(true);

                        // asynchronously delete all icons whose UUID is not linked to
                        // (a) an important sender and (b) a still-stored notification
                        Executors.newSingleThreadExecutor().execute(() -> {
                            // get active UUIDs
                            HashSet<String> activeUUIDs = new HashSet<>(10);
                            for (Object n : notificationsPrefs.getAll().values()) {
                                try {
                                    JSONObject notification = new JSONObject(n.toString());
                                    activeUUIDs.add(notification.optString("uuid"));
                                } catch (JSONException e) {
                                    Log.e("MainActivity", "JSON error", e);
                                }
                            }

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

        } else if (itemId == R.id.menu_silenced_apps) {
            Intent intent = new Intent(this, ItemsActivity.class);
            intent.putExtra("adapter", SilencedAppsAdapter.class.getSimpleName());
            startActivity(intent);
            return true;

        } else if (itemId == R.id.menu_important_senders) {
            Intent intent = new Intent(this, ItemsActivity.class);
            intent.putExtra("adapter", ImportantSendersAdapter.class.getSimpleName());
            startActivity(intent);
            return true;

        }

        return false;
    }

    public void refreshContent(boolean loadAll) {
        Executors.newSingleThreadExecutor().execute(() -> {
            if (loadAll)
                loadAllNotifications();
            ArrayList<JSONObject> filteredNotifications = filterNotifications();
            runOnUiThread(() -> {
                notificationsAdapter.updateData(filteredNotifications);

                if ("all".equals(selectedPackage)) {
                    setupFilterView();
                    if (IsInDndMode(this))
                        Toast.makeText(this, R.string.in_dnd_mode, Toast.LENGTH_SHORT).show();
                }
            });
        });
    }

    private void setupFilterView() {
        filterAdapter = new FilterAdapter(this, allNotifications.packageCounts, this);
        rvFilter.setLayoutManager(new LinearLayoutManager(this, LinearLayoutManager.HORIZONTAL, false));
        rvFilter.setAdapter(filterAdapter);
    }

    private void setupNotificationsView() {
        LinearLayoutManager lm = new LinearLayoutManager(this);
        rvNotifications.setLayoutManager(lm);
        notificationsAdapter = new NotificationsAdapter(this);
        rvNotifications.setAdapter(notificationsAdapter);
    }

    @Override
    public void onAppFilterClick(String packageName, int position) {
        selectedPackage = packageName;
        filterAdapter.setSelectedPosition(position);
        refreshContent(position == 0);
    }

    private void loadAllNotifications() {
        ArrayList<JSONObject> notifications = new ArrayList<>(10);
        HashMap<String, Integer> counts = new HashMap<>();
        int totalCount = 0;

        for (Object n : notificationsPrefs.getAll().values()) {
            try {
                JSONObject notification = new JSONObject(n.toString());
                notifications.add(notification);

                String packageName = notification.optString("package");
                counts.put(packageName, counts.getOrDefault(packageName, 0) + 1);
                totalCount++;

                Log.d("NotificationsAdapter", "Notification loaded: " + notification);

            } catch (JSONException e) {
                Log.e("NotificationsAdapter", "JSON error", e);
            }
        }

        // sort notifications by timestamp in descending order
        notifications.sort((o1, o2) -> {
            try {
                return Math.toIntExact(o2.getLong("postTime") - o1.getLong("postTime"));
            } catch (JSONException e) {
                Log.e("NotificationsAdapter", "JSON error", e);
                return 0;
            }
        });

        // create sorted list of packages and their counts
        List<Map.Entry<String, Integer>> packageCounts = new ArrayList<>(counts.entrySet());
        Collections.sort(packageCounts, (o1, o2) -> o2.getValue().compareTo(o1.getValue()));
        // Add "all" filter at the beginning
        packageCounts.add(0, new AbstractMap.SimpleEntry<>("all", totalCount));

        allNotifications = new AllNotifications(notifications, packageCounts);
    }

    private ArrayList<JSONObject> filterNotifications() {
        ArrayList<JSONObject> selectedNotifications = new ArrayList<>();
        if ("all".equals(selectedPackage))
            return allNotifications.notifications;

        for (JSONObject notification : allNotifications.notifications) {
            try {
                if (notification.getString("package").equals(selectedPackage)) {
                    selectedNotifications.add(notification);
                }
            } catch (JSONException e) {
                Log.e("NotificationsAdapter", "JSON error", e);
            }
        }
        return selectedNotifications;
    }

    private static class AllNotifications {
        private final ArrayList<JSONObject> notifications;
        private final List<Map.Entry<String, Integer>> packageCounts;

        public AllNotifications(ArrayList<JSONObject> notifications,
                                List<Map.Entry<String, Integer>> packageCounts) {
            this.notifications = notifications;
            this.packageCounts = packageCounts;
        }
    }
}