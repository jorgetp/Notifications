package com.jorgetp.notifications;

import static com.jorgetp.notifications.MainActivity.ALWAYS;
import static com.jorgetp.notifications.MainActivity.IMPORTANT_SENDERS_PREFS;
import static com.jorgetp.notifications.MainActivity.IsInDndMode;
import static com.jorgetp.notifications.MainActivity.NON_BUSINESS;
import static com.jorgetp.notifications.MainActivity.NOTIFICATIONS_PREFS;
import static com.jorgetp.notifications.MainActivity.NOTIFICATION_CHANNEL_ID;
import static com.jorgetp.notifications.MainActivity.SILENCED_APPS_PREFS;

import android.app.Notification;
import android.app.NotificationManager;
import android.content.Context;
import android.graphics.Bitmap;
import android.os.Bundle;
import android.service.notification.NotificationListenerService;
import android.service.notification.StatusBarNotification;
import android.util.Log;

import org.json.JSONException;
import org.json.JSONObject;

import java.io.FileOutputStream;
import java.io.IOException;
import java.util.Calendar;
import java.util.UUID;
import java.util.concurrent.Executors;

public class NotificationsService extends NotificationListenerService {
    public static boolean NEW_NOTIFICATIONS;

    private NotificationManager mNotificationManager;

    /*
     * @Override
     * public int onStartCommand(Intent intent, int flags, int startId) {
     * return START_REDELIVER_INTENT;
     * }
     */

    private static String CreateKey(JSONObject json) {
        String packageName = json.optString("package", "");
        String title = json.optString("title", "");
        String textRaw = json.optString("text", "");
        String text = textRaw.substring(0, Math.min(300, textRaw.length()));

        long postTimeBlock = json.optLong("postTime") / 60000;
        return packageName + "|" + title + "|" + text + "|" + postTimeBlock;
    }

    public static boolean IsStandardNotification(StatusBarNotification sbn) {
        // 1. Must not be a notification from this app
        if (sbn.getPackageName().equals("com.jorgetp.notifications"))
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

    @Override
    public void onCreate() {
        super.onCreate();
        mNotificationManager = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
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
            if (IsStandardNotification(sbn)) {

                // show a notification that a notification has been silenced
                mNotificationManager.notify(sbn.getPackageName().hashCode() + sbn.getId(),
                        new Notification.Builder(this, NOTIFICATION_CHANNEL_ID)
                                .setSmallIcon(sbn.getNotification().getSmallIcon())
                                //.setSmallIcon(R.mipmap.ic_launcher)
                                .setLargeIcon(sbn.getNotification().getLargeIcon())
                                //.setLargeIcon(sbn.getNotification().getSmallIcon())
                                .setContentTitle(getString(R.string.silenced_notification))
                                .setContentText(sbn.getNotification()
                                        .extras.getString(Notification.EXTRA_TITLE))
                                .setAutoCancel(true).build());

                cancelNotification(sbn.getKey());
            }
        } else {
            Executors.newSingleThreadExecutor().execute(() -> {
                if (IsStandardNotification(sbn)) {
                    saveNotification(sbn);
                }
            });
        }
    }

    // save a notification to shared preferences
    private void saveNotification(StatusBarNotification sbn) {
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

        getSharedPreferences(NOTIFICATIONS_PREFS, Context.MODE_PRIVATE)
                .edit()
                .putString(notificationKey, json.toString())
                .apply();

        // asynchronously save notification icon
        Executors.newSingleThreadExecutor().execute(() -> {
            android.graphics.drawable.Icon iconObj = sbn.getNotification().getLargeIcon();
            Bitmap iconBitmap = null;
            if (iconObj != null) {
                try {
                    android.graphics.drawable.Drawable drawable = iconObj.loadDrawable(getApplicationContext());
                    if (drawable instanceof android.graphics.drawable.BitmapDrawable) {
                        iconBitmap = ((android.graphics.drawable.BitmapDrawable) drawable).getBitmap();
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
