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
import android.util.Pair;
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
import java.util.List;
import java.util.Map;
import java.util.concurrent.Executors;

public class MainActivity extends AppCompatActivity implements FilterAdapter.OnAppFilterClickListener {
    public static final String NOTIFICATION_CHANNEL_ID = "Notifications";
    public static final String NOTIFICATIONS_PREFS = "Notifications-Items";
    public static final String SILENCED_APPS_PREFS = "Notifications-Silenced-Apps";
    public static final String IMPORTANT_SENDERS_PREFS = "Notifications-Important-Senders";
    public static final int ALWAYS = 1001;
    public static final int NON_BUSINESS = 1002;

    private RecyclerView rvNotifications;
    private RecyclerView rvFilter;
    private FilterAdapter filterAdapter;
    private NotificationsAdapter notificationsAdapter;
    private List<JSONObject> allNotifications;
    private List<Map.Entry<String, Integer>> packageCounts;

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
                    NOTIFICATION_CHANNEL_ID,
                    getString(R.string.app_name),
                    NotificationManager.IMPORTANCE_HIGH);
            notificationManager.createNotificationChannel(channel);

        } else {
            AlertDialog.Builder dialogBuilder = new AlertDialog.Builder(this)
                    .setTitle(R.string.app_name)
                    .setMessage(R.string.enable_service_message)
                    .setCancelable(false)
                    .setPositiveButton(android.R.string.ok,
                            (dialog, id) -> startActivity(
                                    new Intent("android.settings.ACTION_NOTIFICATION_LISTENER_SETTINGS")))
                    .setNegativeButton(android.R.string.cancel,
                            (dialog, id) -> MainActivity.this.finishAndRemoveTask());
            dialogBuilder.create().show();
        }

        rvNotifications = findViewById(R.id.rvNotifications);
        rvFilter = findViewById(R.id.rvFilter);
        setupNotificationsView();
        refreshContent("all");

        FloatingActionButton fabRefresh = findViewById(R.id.fabRefresh);
        fabRefresh.setOnClickListener(view -> refreshContent("all"));
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (NotificationsService.NEW_NOTIFICATIONS) {
            refreshContent("all");
            NotificationsService.NEW_NOTIFICATIONS = false;
        }
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
        SharedPreferences notificationPrefs = getApplicationContext().getSharedPreferences(NOTIFICATIONS_PREFS,
                Context.MODE_PRIVATE);
        SharedPreferences importantSendersPrefs = getApplicationContext().getSharedPreferences(IMPORTANT_SENDERS_PREFS,
                Context.MODE_PRIVATE);
        Map<String, ?> importantSenders = importantSendersPrefs.getAll();

        int itemId = item.getItemId();
        if (itemId == R.id.menu_clear_all_except_today) {
            new AlertDialog.Builder(this)
                    .setMessage(R.string.menu_clear_all_except_today_confirmation)
                    .setPositiveButton(android.R.string.yes, (dialog, id) -> {
                        for (String key : notificationPrefs.getAll().keySet()) {
                            try {
                                JSONObject notification = new JSONObject(notificationPrefs.getString(key, null));
                                long postTime = notification.optLong("postTime");
                                Date date = ToDate(postTime);
                                if (!IsToday(date)) {
                                    notificationPrefs.edit().remove(key).apply();
                                    // asynchronously delete notification icon from iternal storage
                                    Executors.newSingleThreadExecutor().execute(() -> {
                                        // delete only if notification UUID is not linked to an important sender
                                        String uuid = notification.optString("uuid");
                                        if (!importantSenders.containsValue(uuid)) {
                                            String iconFileName = "notification_icon_" + notification.optString("uuid")
                                                    + ".png";
                                            File iconFile = new File(getFilesDir(), iconFileName);
                                            if (iconFile.exists()) {
                                                try {
                                                    iconFile.delete();
                                                } catch (Exception e) {
                                                    Log.e("MainActivity",
                                                            "Failed to delete: " + iconFile.getAbsolutePath(), e);
                                                }
                                            }
                                        }
                                    });
                                }
                            } catch (JSONException e) {
                                Log.e("MainActivity", "JSON error", e);
                            }
                        }
                        refreshContent("all");
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

    public void refreshContent(String packageName) {
        Executors.newSingleThreadExecutor().execute(() -> {
            // Runs in the background
            List<JSONObject> selectedNotifications;
            if ("all".equals(packageName)) {
                Pair<List<JSONObject>, List<Map.Entry<String, Integer>>> result = loadAllNotifications();
                allNotifications = result.first;
                packageCounts = result.second;
                selectedNotifications = allNotifications;
            } else
                selectedNotifications = filteredNotifications(packageName);

            // Switch back to the main thread to update the UI
            List<JSONObject> finalSelectedNotifications = selectedNotifications;
            runOnUiThread(() -> {
                notificationsAdapter.updateData(finalSelectedNotifications);
                rvNotifications.smoothScrollToPosition(0); // rvNotifications.scrollToPosition(0);
                if ("all".equals(packageName))
                    setupFilterView();

                if ("all".equals(packageName) && IsInDndMode(this))
                    Toast.makeText(this, R.string.in_dnd_mode, Toast.LENGTH_SHORT).show();
            });
        });
    }

    private void setupFilterView() {
        filterAdapter = new FilterAdapter(this, packageCounts, this);
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
        refreshContent(packageName);
        filterAdapter.setSelectedPosition(position);
    }

    private Pair<List<JSONObject>, List<Map.Entry<String, Integer>>> loadAllNotifications() {
        SharedPreferences notificationsPrefs = getSharedPreferences(NOTIFICATIONS_PREFS,
                Context.MODE_PRIVATE);

        List<JSONObject> notifications = new ArrayList<>(10);
        Map<String, Integer> counts = new HashMap<>();
        int totalCount = 0;
        for (String key : notificationsPrefs.getAll().keySet()) {
            try {
                JSONObject notification = new JSONObject(notificationsPrefs.getString(key, "{}"));
                String packageName = notification.optString("package");

                notifications.add(notification);

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
        // Add "All" filter at the beginning
        packageCounts.add(0, new AbstractMap.SimpleEntry<>("all", totalCount));

        return new Pair<>(notifications, packageCounts);
    }

    private List<JSONObject> filteredNotifications(String filterPackage) {
        List<JSONObject> selectedNotifications = new ArrayList<>();
        for (JSONObject notification : allNotifications) {
            try {
                if (notification.getString("package").equals(filterPackage)) {
                    selectedNotifications.add(notification);
                }
            } catch (JSONException e) {
                Log.e("NotificationsAdapter", "JSON error", e);
            }
        }
        return selectedNotifications;
    }
}