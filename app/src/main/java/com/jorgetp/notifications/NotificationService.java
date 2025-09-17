package com.jorgetp.notifications;

import static com.jorgetp.notifications.MainActivity.ALWAYS;
import static com.jorgetp.notifications.MainActivity.CHANNEL_ID;
import static com.jorgetp.notifications.MainActivity.IMPORTANT_SENDERS_PREFS;
import static com.jorgetp.notifications.MainActivity.NON_BUSINESS;
import static com.jorgetp.notifications.MainActivity.NOTIFICATIONS_PREFS;
import static com.jorgetp.notifications.MainActivity.SILENCED_APPS_PREFS;

import android.app.Notification;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.Icon;
import android.os.Bundle;
import android.service.notification.NotificationListenerService;
import android.service.notification.StatusBarNotification;
import android.util.Log;
import android.util.Pair;

import com.jorgetp.notifications.adapter.NotificationsAdapter;

import org.json.JSONException;
import org.json.JSONObject;

import java.io.FileOutputStream;
import java.io.IOException;
import java.util.Calendar;
import java.util.Date;
import java.util.Random;
import java.util.UUID;
import java.util.concurrent.Executors;

public class NotificationService extends NotificationListenerService {
    private final Random random = new Random();
    private NotificationManager manager;

    private static String createKey(JSONObject json) {
        long postTimeBlock = json.optLong("postTime") / 30000;
        String packageName = json.optString("package", "");
        String title = json.optString("title", "");
        String textRaw = json.optString("text", "");
        String text = textRaw.substring(0, Math.min(300, textRaw.length()));

        return postTimeBlock + "|" + packageName + "|" + title + "|" + text;
    }

    public void saveNotification(JSONObject notification) {
        SharedPreferences prefs = MainActivity.getPrefs(this, NOTIFICATIONS_PREFS);

        try {
            SharedPreferences.Editor editor = prefs.edit();

            int allCount = prefs.getInt("all_count", 0);
            if (allCount > 0) {
                long lastFromAll = new JSONObject(prefs
                        .getString("all_notification_" + (allCount - 1), "{}"))
                        .optLong("postTime");

                Date now = new Date(notification.optLong("postTime"));
                Date last = new Date(lastFromAll);
                if (!NotificationsAdapter.isSameDay(now, last)) {
                    JSONObject j = new JSONObject();
                    j.put("isHeader", true);
                    j.put("postTime", lastFromAll);
                    editor.putString("all_notification_" + allCount, j.toString());
                    allCount += 1;
                }
            }

            String packageName = notification.optString("package");
            int packageCount = prefs.getInt(packageName + "_count", 0);
            if (packageCount > 0) {
                long lastFromPackage = new JSONObject(prefs
                        .getString(packageName + "_notification_" + (packageCount - 1), "{}"))
                        .optLong("postTime");

                Date now = new Date(notification.optLong("postTime"));
                Date last = new Date(lastFromPackage);
                if (!NotificationsAdapter.isSameDay(now, last)) {
                    JSONObject j = new JSONObject();
                    j.put("isHeader", true);
                    j.put("postTime", lastFromPackage);
                    editor.putString(packageName + "_notification_" + packageCount, j.toString());
                    packageCount += 1;
                }
            }

            editor
                    .putString("all_notification_" + allCount, notification.toString())
                    .putInt("all_count", allCount + 1)
                    .putString(packageName + "_notification_" + packageCount, notification.toString())
                    .putInt(packageName + "_count", packageCount + 1)
                    .apply();

        } catch (Exception e) {
            // Log.e("NotificationService", "Error saving notification", e);
        }
    }

    @Override
    public void onNotificationPosted(StatusBarNotification sbn) {
        if (isSilentNotification(sbn)) {
            if (isStandardNotification(sbn)) {
                processNotification(sbn, true);
            }
        } else {
            Executors.newSingleThreadExecutor().execute(() -> {
                if (isStandardNotification(sbn)) {
                    processNotification(sbn, false);
                }
            });
        }
    }

    @Override
    public void onCreate() {
        super.onCreate();
        manager = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
    }

    private void processNotification(StatusBarNotification sbn, boolean isSilenced) {
        Notification notification = sbn.getNotification();
        if (notification == null)
            return;
        if ((notification.flags & Notification.FLAG_GROUP_SUMMARY) != 0)
            return;

        Bundle extras = notification.extras;
        String title = extras.getString(Notification.EXTRA_TITLE);
        CharSequence text = extras.getCharSequence(Notification.EXTRA_TEXT);

        // build JSON
        JSONObject json = new JSONObject();
        String uuid = UUID.randomUUID().toString();
        try {
            json.put("uuid", uuid);
            json.put("package", sbn.getPackageName());
            json.put("postTime", sbn.getPostTime());
            json.put("title", title);
            json.put("text", text != null ? text.toString() : null);
            json.put("category", notification.category);

        } catch (JSONException e) {
            Log.e("NotificationService", "JSON error", e);
            return;
        }

        // get icons
        Icon smallIcon = notification.getSmallIcon();
        Icon largeIcon = notification.getLargeIcon();
        Drawable[] largeIconDrawable = {null};
        if (largeIcon != null) {
            try {
                largeIconDrawable[0] = largeIcon.loadDrawable(getApplicationContext());
            } catch (Exception e) {
                Log.e("NotificationService", "Error converting large icon to bitmap", e);
            }
        }

        if (isSilenced)
            cancelNotification(sbn.getKey());

        // asynchronously continue processing, i.e. save notification, icons, etc...
        Executors.newSingleThreadExecutor().execute(() -> {
            String notificationKey = createKey(json);
            SharedPreferences notificationsPrefs = MainActivity.getPrefs(this, NOTIFICATIONS_PREFS);
            SharedPreferences importantSenders = MainActivity.getPrefs(this, IMPORTANT_SENDERS_PREFS);
            boolean postNotification = isSilenced && !notificationsPrefs.getBoolean(notificationKey, false);

            // save notification
            saveNotification(json);
            notificationsPrefs.edit().putBoolean(notificationKey, true).apply();

            // save icon to storage
            Bitmap[] largeIconBitmap = {null};
            if (largeIconDrawable[0] != null && largeIconDrawable[0] instanceof BitmapDrawable) {
                largeIconBitmap[0] = ((BitmapDrawable) largeIconDrawable[0]).getBitmap();

                Executors.newSingleThreadExecutor().execute(() -> {
                    try (FileOutputStream fos = openFileOutput("notification_icon_" + uuid + ".png",
                            Context.MODE_PRIVATE)) {
                        largeIconBitmap[0].compress(Bitmap.CompressFormat.PNG, 100, fos);

                        // update important sender icon if applicable
                        String key = sbn.getPackageName() + "/" + title;
                        if (importantSenders.contains(key))
                            importantSenders.edit().putString(key, uuid).apply();

                    } catch (IOException e) {
                        Log.e("NotificationService", "Error saving notification icon", e);
                    }
                });
            }

            // post silenced notification
            if (postNotification)
                postSilencedNotification(json, smallIcon, largeIconBitmap[0]);

            Log.d("NotificationService", "Notification processed: " + json);
        });
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
                return MainActivity.getPrefs(this, IMPORTANT_SENDERS_PREFS).contains(packageName + "/" + title);
        }
        return false;
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

    public boolean isInDndMode() {
        // Check if the device is running Android 12 or higher
        if (android.os.Build.VERSION.SDK_INT < android.os.Build.VERSION_CODES.S) {
            return false;
        }

        if (manager == null) {
            return false;
        }

        // Check the current interruption filter
        int interruptionFilter = manager.getCurrentInterruptionFilter();
        return interruptionFilter == NotificationManager.INTERRUPTION_FILTER_NONE ||
                interruptionFilter == NotificationManager.INTERRUPTION_FILTER_ALARMS ||
                interruptionFilter == NotificationManager.INTERRUPTION_FILTER_PRIORITY;
    }

    private boolean isSilentNotification(StatusBarNotification sbn) {
        if (isInDndMode())
            return !isImportantNotification(sbn);

        switch (MainActivity.getPrefs(this, SILENCED_APPS_PREFS).getInt(sbn.getPackageName(), 0)) {
            case ALWAYS:
                return !isImportantNotification(sbn);
            case NON_BUSINESS:
                return !isImportantNotification(sbn) && !isBusinessHour();
            default:
                return false;
        }
    }

    private void postSilencedNotification(JSONObject notification, Icon smallIcon, Bitmap largeIcon) {
        String packageName = notification.optString("package");
        String title = notification.optString("title");
        String text = notification.optString("text");
        long postTime = notification.optLong("postTime");

        // create notification builder
        Notification.Builder builder = new Notification.Builder(getApplicationContext(), CHANNEL_ID)
                .setSmallIcon(R.drawable.outline_notifications_off_24)
                .setContentTitle(getString(R.string.silenced_notification, title))
                .setContentText(text)
                .setAutoCancel(true)
                .setShowWhen(true)
                .setWhen(postTime);

        /*if (smallIcon != null)
            builder.setSmallIcon(smallIcon);*/

        if (largeIcon != null)
            builder.setLargeIcon(largeIcon);
        else {
            // set large icon as the original app icon
            Pair<CharSequence, Drawable> appInfo = MainActivity.getAppInfo(getApplicationContext(), packageName);
            if (appInfo.second != null) {
                Bitmap iconBitmap;
                if (appInfo.second instanceof BitmapDrawable) {
                    iconBitmap = ((BitmapDrawable) appInfo.second).getBitmap();
                } else {
                    // convert non-BitmapDrawable to Bitmap
                    iconBitmap = Bitmap.createBitmap(
                            appInfo.second.getIntrinsicWidth(),
                            appInfo.second.getIntrinsicHeight(),
                            Bitmap.Config.ARGB_8888);
                    Canvas canvas = new Canvas(iconBitmap);
                    appInfo.second.setBounds(0, 0, canvas.getWidth(), canvas.getHeight());
                    appInfo.second.draw(canvas);
                }

                builder.setLargeIcon(iconBitmap);
            }
        }

        // set tap action to open notification's original activity
        Intent launchIntent = getPackageManager().getLaunchIntentForPackage(packageName);
        if (launchIntent != null) {
            builder.setContentIntent(PendingIntent.getActivity(
                    getApplicationContext(), 0, launchIntent,
                    PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE));
        }

        // finally notify
        manager.notify(random.nextInt(Integer.MAX_VALUE), builder.build());
    }
}
