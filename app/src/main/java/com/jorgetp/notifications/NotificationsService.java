package com.jorgetp.notifications;

import static com.jorgetp.notifications.MainActivity.ALWAYS;
import static com.jorgetp.notifications.MainActivity.IMPORTANT_SENDERS_PREFS;
import static com.jorgetp.notifications.MainActivity.IsInDndMode;
import static com.jorgetp.notifications.MainActivity.NEW_SILENCED_NOTIFICATIONS;
import static com.jorgetp.notifications.MainActivity.NON_BUSINESS;
import static com.jorgetp.notifications.MainActivity.NOTIFICATIONS_PREFS;
import static com.jorgetp.notifications.MainActivity.NOTIFICATION_CHANNEL_ID;
import static com.jorgetp.notifications.MainActivity.SILENCED_APPS_PREFS;

import android.app.Notification;
import android.app.NotificationManager;
import android.content.Context;
import android.content.SharedPreferences;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.Icon;
import android.os.Bundle;
import android.os.Handler;
import android.os.HandlerThread;
import android.service.notification.NotificationListenerService;
import android.service.notification.StatusBarNotification;
import android.util.Log;

import org.json.JSONException;
import org.json.JSONObject;

import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.Calendar;
import java.util.Random;
import java.util.UUID;
import java.util.concurrent.Executors;

public class NotificationsService extends NotificationListenerService {
    public static boolean NEW_NOTIFICATIONS;
    private NotificationManager notificationManager;

    private HandlerThread handlerThread;
    private Handler handler;
    private Runnable repeatingTask;

    private static String CreateKey(JSONObject json) {
        String packageName = json.optString("package", "");
        String title = json.optString("title", "");
        String textRaw = json.optString("text", "");
        String text = textRaw.substring(0, Math.min(300, textRaw.length()));

        long postTimeBlock = json.optLong("postTime") / 30000;
        return packageName + "|" + title + "|" + text + "|" + postTimeBlock;
    }

    @Override
    public void onListenerConnected() {
        super.onListenerConnected();
        notificationManager = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);

        // Start a background thread
        handlerThread = new HandlerThread("NotificationsBackgroundThread");
        handlerThread.start();

        // Create a handler attached to the background thread
        handler = new Handler(handlerThread.getLooper());

        // Define the task
        repeatingTask = new Runnable() {
            @Override
            public void run() {
                // Your background task
                postNewSilencedNotifications();
                // Schedule the next run after 10 minutes (600,000 ms)
                handler.postDelayed(this, 10 * 60 * 1000L);
            }
        };

        // Start the repeating task
        handler.post(repeatingTask);
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        // Clean up
        if (handler != null && repeatingTask != null) {
            handler.removeCallbacks(repeatingTask);
        }
        if (handlerThread != null) {
            handlerThread.quitSafely();
        }
        Log.d("NotificationsService", "Service destroyed, task stopped.");
    }

    public boolean isStandardNotification(StatusBarNotification sbn) {
        // 1. Must not be a self-notification
        if (sbn.getPackageName().equals(getPackageName()))
            return false;
        // 2. Must be user-clearable
        if (!sbn.isClearable())
            return false;
        // 3. Must NOT be an ongoing event
        if (sbn.isOngoing())
            return false;
        // 4. Must NOT be a system/background category
        String category = sbn.getNotification().category;
        if (category != null) {
            switch (category) {
                case Notification.CATEGORY_CALL:
                case Notification.CATEGORY_ALARM:
                case Notification.CATEGORY_PROGRESS:
                case Notification.CATEGORY_TRANSPORT:
                case Notification.CATEGORY_SERVICE:
                case Notification.CATEGORY_NAVIGATION:
                    return false;
            }
        }
        return true;
    }

    private boolean isImportantNotification(StatusBarNotification sbn) {
        String packageName = sbn.getPackageName();
        Bundle extras = sbn.getNotification().extras;
        if (extras != null) {
            CharSequence title = extras.getCharSequence(Notification.EXTRA_TITLE);
            if (title != null)
                return getSharedPreferences(IMPORTANT_SENDERS_PREFS, Context.MODE_PRIVATE)
                        .contains(packageName + "/" + title);
        }
        return false;
    }

    private boolean isSilentNotification(StatusBarNotification sbn) {
        if (IsInDndMode(this))
            return !isImportantNotification(sbn);

        switch (getSharedPreferences(SILENCED_APPS_PREFS,
                Context.MODE_PRIVATE).getInt(sbn.getPackageName(), 0)) {
            case ALWAYS:
                return !isImportantNotification(sbn);
            case NON_BUSINESS:
                return !isImportantNotification(sbn) && !isBusinessHour();
            default:
                return false;
        }
    }

    // true if weekend or weekdays 08:00-17:30
    private boolean isBusinessHour() {
        int day = Calendar.getInstance().get(Calendar.DAY_OF_WEEK);
        if (day == Calendar.SATURDAY || day == Calendar.SUNDAY)
            return false;

        int hour = Calendar.getInstance().get(Calendar.HOUR_OF_DAY);
        int minute = Calendar.getInstance().get(Calendar.MINUTE);
        return (hour >= 8 && hour <= 16) || (hour == 17 && minute <= 30);
    }

    @Override
    public void onNotificationPosted(StatusBarNotification sbn) {
        if (isSilentNotification(sbn)) {
            if (isStandardNotification(sbn)) {
                processNotification(sbn, true);
                cancelNotification(sbn.getKey());
            }
        } else {
            Executors.newSingleThreadExecutor().execute(() -> {
                if (isStandardNotification(sbn)) {
                    processNotification(sbn, false);
                }
            });
        }
    }

    private void postNewSilencedNotifications() {
        SharedPreferences prefs = getSharedPreferences(NEW_SILENCED_NOTIFICATIONS, Context.MODE_PRIVATE);
        for (String key : prefs.getAll().keySet()) {
            try {
                JSONObject notification = new JSONObject(prefs.getString(key, null));
                String packageName = notification.optString("package");
                String title = notification.optString("title");
                String notificationId = notification.optString("uuid");
                long postTime = notification.optLong("postTime");

                ApplicationInfo appInfo = getPackageManager().getApplicationInfo(packageName, 0);
                CharSequence appName = getPackageManager().getApplicationLabel(appInfo);

                Notification.Builder builder = new Notification.Builder(this, NOTIFICATION_CHANNEL_ID)
                        //.setSmallIcon(R.mipmap.ic_launcher)
                        .setSmallIcon(R.drawable.outline_notifications_off_24)
                        .setContentTitle(getString(R.string.silenced_notification))
                        .setContentText(String.format("%s: %s", appName, title))
                        .setAutoCancel(true)
                        .setShowWhen(true)
                        .setWhen(postTime);

                try (FileInputStream fis = openFileInput("notification_icon_" + notificationId + ".png")) {
                    Bitmap bitmap = BitmapFactory.decodeStream(fis);
                    builder.setLargeIcon(bitmap);
                } catch (Exception e) {
                    Log.e("NotificationsService", "Icon not found", e);
                }

                notificationManager.notify(new Random().nextInt(Integer.MAX_VALUE), builder.build());

            } catch (JSONException e) {
                Log.e("NotificationsService", "JSON error", e);

            } catch (PackageManager.NameNotFoundException e) {
                Log.e("NotificationsService", "App not found", e);
            }
        }
        prefs.edit().clear().apply();
    }

    // save a notification to shared preferences
    private void processNotification(StatusBarNotification sbn, boolean silenced) {
        Notification notification = sbn.getNotification();
        if (notification == null)
            return;
        if ((notification.flags & Notification.FLAG_GROUP_SUMMARY) != 0)
            return;

        Bundle extras = notification.extras;
        String title = extras.getString(Notification.EXTRA_TITLE);
        CharSequence text = extras.getCharSequence(Notification.EXTRA_TEXT);
        // CharSequence bigText = extras.getCharSequence(Notification.EXTRA_BIG_TEXT);

        // Build JSON
        JSONObject json = new JSONObject();
        String uuid = UUID.randomUUID().toString();
        try {
            json.put("package", sbn.getPackageName());
            json.put("postTime", sbn.getPostTime());
            json.put("title", title);
            json.put("uuid", uuid);
            json.put("text", text != null ? text.toString() : null);

        } catch (JSONException e) {
            Log.e("NotificationsService", "JSON error", e);
            return;
        }

        String notificationKey = CreateKey(json);
        try {
            json.put("key", notificationKey);
        } catch (JSONException e) {
            Log.e("NotificationsService", "JSON error", e);
            return;
        }

        // save notification
        getSharedPreferences(NOTIFICATIONS_PREFS, Context.MODE_PRIVATE)
                .edit()
                .putString(notificationKey, json.toString())
                .apply();

        // save notification to the new_silenced_notifications list
        if (silenced)
            getSharedPreferences(NEW_SILENCED_NOTIFICATIONS, Context.MODE_PRIVATE)
                    .edit().putString(notificationKey, json.toString()).apply();

        // asynchronously save notification icon
        Executors.newSingleThreadExecutor().execute(() -> {
            Icon iconObj = sbn.getNotification().getLargeIcon();
            Bitmap iconBitmap = null;
            if (iconObj != null) {
                try {
                    Drawable drawable = iconObj.loadDrawable(getApplicationContext());
                    if (drawable instanceof BitmapDrawable) {
                        iconBitmap = ((BitmapDrawable) drawable).getBitmap();
                    }
                } catch (Exception e) {
                    Log.e("NotificationsService", "Error converting icon to bitmap", e);
                }
            }
            if (iconBitmap != null) {
                try (FileOutputStream fos = openFileOutput("notification_icon_" + uuid + ".png",
                        Context.MODE_PRIVATE)) {
                    iconBitmap.compress(Bitmap.CompressFormat.PNG, 100, fos);
                } catch (IOException e) {
                    Log.e("NotificationsService", "Error saving notification icon", e);
                }
            }
        });

        NEW_NOTIFICATIONS = true;
        Log.d("NotificationsService", "Notification saved: " + json);
    }
}
