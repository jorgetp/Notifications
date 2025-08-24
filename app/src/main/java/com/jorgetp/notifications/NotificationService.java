package com.jorgetp.notifications;

import static com.jorgetp.notifications.MainActivity.ALWAYS;
import static com.jorgetp.notifications.MainActivity.CHANNEL_ID;
import static com.jorgetp.notifications.MainActivity.IMPORTANT_SENDERS_PREFS;
import static com.jorgetp.notifications.MainActivity.IsInDndMode;
import static com.jorgetp.notifications.MainActivity.NON_BUSINESS;
import static com.jorgetp.notifications.MainActivity.NOTIFICATIONS_PREFS;
import static com.jorgetp.notifications.MainActivity.SILENCED_APPS_PREFS;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.content.Context;
import android.content.SharedPreferences;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.Icon;
import android.os.Bundle;
import android.service.notification.NotificationListenerService;
import android.service.notification.StatusBarNotification;
import android.util.Log;

import org.json.JSONException;
import org.json.JSONObject;

import java.io.FileOutputStream;
import java.io.IOException;
import java.util.Calendar;
import java.util.Random;
import java.util.UUID;
import java.util.concurrent.Executors;

public class NotificationService extends NotificationListenerService {
    private final Random random = new Random();
    private NotificationManager manager;

    private static String CreateKey(JSONObject json) {
        long postTimeBlock = json.optLong("postTime") / 30000;
        String packageName = json.optString("package", "");
        String title = json.optString("title", "");
        String textRaw = json.optString("text", "");
        String text = textRaw.substring(0, Math.min(300, textRaw.length()));

        return postTimeBlock + "|" + packageName + "|" + title + "|" + text;
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

        } catch (JSONException e) {
            Log.e("NotificationService", "JSON error", e);
            return;
        }

        // get icons
        Icon smallIcon = notification.getSmallIcon();
        Icon largeIcon = notification.getLargeIcon();
        Drawable largeIconDrawable = null;
        if (largeIcon != null) {
            try {
                largeIconDrawable = largeIcon.loadDrawable(getApplicationContext());
            } catch (Exception e) {
                Log.e("NotificationService", "Error converting large icon to bitmap", e);
            }
        }

        if (isSilenced)
            cancelNotification(sbn.getKey());

        String notificationKey = CreateKey(json);
        SharedPreferences prefs = getSharedPreferences(NOTIFICATIONS_PREFS, Context.MODE_PRIVATE);
        boolean postNotification = isSilenced && !prefs.contains(notificationKey);

        // save notification
        prefs.edit().putString(notificationKey, json.toString()).apply();

        // convert and save large icon
        Bitmap[] largeIconBitmap = {null};
        if (largeIconDrawable == null) {
            // notification has no large icon, so create it from app's icon if to be posted
            if (postNotification) {
                try {
                    ApplicationInfo appInfo = getPackageManager().getApplicationInfo(sbn.getPackageName(), 0);
                    Drawable appIconDrawable = getPackageManager().getApplicationIcon(appInfo);

                    if (appIconDrawable instanceof BitmapDrawable) {
                        largeIconBitmap[0] = ((BitmapDrawable) appIconDrawable).getBitmap();
                    } else {
                        // convert non-BitmapDrawable to Bitmap
                        largeIconBitmap[0] = Bitmap.createBitmap(
                                appIconDrawable.getIntrinsicWidth(),
                                appIconDrawable.getIntrinsicHeight(),
                                Bitmap.Config.ARGB_8888
                        );
                        Canvas canvas = new Canvas(largeIconBitmap[0]);
                        appIconDrawable.setBounds(0, 0, canvas.getWidth(), canvas.getHeight());
                        appIconDrawable.draw(canvas);
                    }

                } catch (Exception e) {
                    Log.e("NotificationService", "App not found", e);
                }
            }

        } else {
            // notification has large icon
            if (largeIconDrawable instanceof BitmapDrawable) {
                largeIconBitmap[0] = ((BitmapDrawable) largeIconDrawable).getBitmap();

                // asynchronously save icon to storage
                Executors.newSingleThreadExecutor().execute(() -> {
                    try (FileOutputStream fos = openFileOutput("notification_icon_" + uuid + ".png",
                            Context.MODE_PRIVATE)) {
                        largeIconBitmap[0].compress(Bitmap.CompressFormat.PNG, 100, fos);

                        // update important sender icon if applicable
                        SharedPreferences importantSenders = getSharedPreferences(IMPORTANT_SENDERS_PREFS, Context.MODE_PRIVATE);
                        String key = sbn.getPackageName() + "/" + title;
                        if (importantSenders.contains(key))
                            importantSenders.edit().putString(key, uuid).apply();

                    } catch (IOException e) {
                        Log.e("NotificationService", "Error saving notification icon", e);
                    }
                });
            }
        }

        if (postNotification)
            postSilencedNotification(json, smallIcon, largeIconBitmap[0]);

        // notify MainActivity for onResume
        MainActivity.REFRESH_CONTENT_ON_RESUME = true;
        Log.d("NotificationService", "Notification processed: " + json);
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

    // true if weekend or weekdays 08:00-17:30
    private boolean isBusinessHour() {
        int day = Calendar.getInstance().get(Calendar.DAY_OF_WEEK);
        if (day == Calendar.SATURDAY || day == Calendar.SUNDAY)
            return false;

        int hour = Calendar.getInstance().get(Calendar.HOUR_OF_DAY);
        int minute = Calendar.getInstance().get(Calendar.MINUTE);
        return (hour >= 8 && hour <= 16) || (hour == 17 && minute <= 30);
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

    private void postSilencedNotification(JSONObject notification, Icon smallIcon, Bitmap largeIcon) {
        if (manager == null) {
            manager = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
            if (manager != null) {
                manager.createNotificationChannel(new NotificationChannel(
                        CHANNEL_ID,
                        "Notifications",
                        NotificationManager.IMPORTANCE_DEFAULT
                ));
            }
        }

        try {
            String packageName = notification.optString("package");
            String title = notification.optString("title");
            String text = notification.optString("text");
            long postTime = notification.optLong("postTime");

            ApplicationInfo appInfo = getPackageManager().getApplicationInfo(packageName, 0);
            CharSequence appName = getPackageManager().getApplicationLabel(appInfo);

            Notification.Builder builder = new Notification.Builder(this, CHANNEL_ID)
                    .setSmallIcon(smallIcon)
                    .setContentTitle(getString(R.string.silenced_notification, appName))
                    .setContentText(String.format("%s\n%s", title, text))
                    .setAutoCancel(true)
                    .setShowWhen(true)
                    .setWhen(postTime);

            if (largeIcon != null)
                builder.setLargeIcon(largeIcon);

            manager.notify(random.nextInt(Integer.MAX_VALUE), builder.build());

        } catch (PackageManager.NameNotFoundException e) {
            Log.e("NotificationService", "App not found", e);
        }
    }
}
