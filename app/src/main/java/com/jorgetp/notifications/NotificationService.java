package com.jorgetp.notifications;

import static com.jorgetp.notifications.MainActivity.ALWAYS;
import static com.jorgetp.notifications.MainActivity.CHANNEL_ID;
import static com.jorgetp.notifications.MainActivity.IMPORTANT_SENDERS_PREFS;
import static com.jorgetp.notifications.MainActivity.NON_BUSINESS;
import static com.jorgetp.notifications.MainActivity.SETTINGS_PREFS;
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

import com.jorgetp.notifications.dao.DbProvider;
import com.jorgetp.notifications.dao.IconDao;
import com.jorgetp.notifications.dao.NotificationDao;
import com.jorgetp.notifications.dao.StoredIcon;
import com.jorgetp.notifications.dao.StoredNotification;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.Calendar;
import java.util.Map;
import java.util.Random;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;

public class NotificationService extends NotificationListenerService {
    private static final long CACHE_DURATION = 120000; // 2 minutes in milliseconds
    private static final int MAX_CACHE_SIZE = 1000; // Prevent memory issues
    private final Random random = new Random();
    private final Map<String, Long> recentNotifications = new ConcurrentHashMap<>();  // Memory cache
    private NotificationManager manager;

    @Override
    public void onNotificationPosted(StatusBarNotification sbn) {
        if (isSilentNotification(sbn)) {
            if (isStandardNotification(sbn)) processNotification(sbn, true);
        } else {
            // asynchronously process non-silenced notifications
            Executors.newSingleThreadExecutor().execute(() -> {
                if (isStandardNotification(sbn)) processNotification(sbn, false);
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

        // Get icons
        Icon smallIcon = notification.getSmallIcon();
        Icon largeIcon = notification.getLargeIcon();
        byte[] largeIconBytes = null;

        // Convert large icon to byte array
        if (largeIcon != null) {
            try {
                Drawable largeIconDrawable = largeIcon.loadDrawable(getApplicationContext());
                if (largeIconDrawable != null) {
                    Bitmap bitmap;
                    if (largeIconDrawable instanceof BitmapDrawable) {
                        bitmap = ((BitmapDrawable) largeIconDrawable).getBitmap();
                    } else {
                        // Convert non-BitmapDrawable to Bitmap
                        bitmap = Bitmap.createBitmap(
                                largeIconDrawable.getIntrinsicWidth(),
                                largeIconDrawable.getIntrinsicHeight(),
                                Bitmap.Config.ARGB_8888);
                        Canvas canvas = new Canvas(bitmap);
                        largeIconDrawable.setBounds(0, 0, canvas.getWidth(), canvas.getHeight());
                        largeIconDrawable.draw(canvas);
                    }

                    // Convert bitmap to byte array
                    largeIconBytes = bitmapToByteArray(bitmap);
                }
            } catch (Exception e) {
                Log.e("NotificationService", "Error converting large icon to bitmap", e);
            }
        }

        final byte[] largeIconBytesFinal = largeIconBytes;

        // Create StoredNotification (without icon data)
        StoredNotification sn = new StoredNotification(
                sbn.getPostTime(),
                UUID.randomUUID().toString(),
                sbn.getPackageName(),
                title != null ? title : "",
                text != null ? text.toString() : "",
                notification.category
        );

        if (isSilenced)
            cancelNotification(sbn.getKey());

        // Asynchronously continue processing
        Executors.newSingleThreadExecutor().execute(() -> {
            NotificationDao notificationDao = DbProvider.get(getApplicationContext()).notificationDao();
            IconDao iconDao = DbProvider.get(getApplicationContext()).iconDao();
            SharedPreferences importantSenders = MainActivity.getPrefs(NotificationService.this, IMPORTANT_SENDERS_PREFS);
            SharedPreferences settingsPrefs = MainActivity.getPrefs(NotificationService.this, SETTINGS_PREFS);

            // Clean expired cache entries
            cleanExpiredCache();

            // Check memory cache only for deduplication
            boolean isRecentDuplicate = checkMemoryCache(sn);

            if (isRecentDuplicate) {
                // Skip processing this notification - it's a recent duplicate
                Log.d("NotificationService", "Skipping duplicate notification: " + sn.dedupeKey);
                return;
            }

            // Add to memory cache
            addToMemoryCache(sn);

            // Set pinned
            String importantSenderKey = sbn.getPackageName() + "/" + title;
            sn.pinned = settingsPrefs.getBoolean("pin_important_senders", false) &&
                    importantSenders.contains(importantSenderKey);

            // Always insert new notification (no database deduplication)
            notificationDao.insert(sn);

            // Save large icon if present
            if (largeIconBytesFinal != null && title != null) {
                StoredIcon icon = new StoredIcon(
                        sbn.getPackageName(),
                        title,
                        largeIconBytesFinal,
                        System.currentTimeMillis()
                );
                iconDao.insertOrUpdate(icon);

                // Update important sender if applicable
                if (importantSenders.contains(importantSenderKey))
                    importantSenders.edit().putString(importantSenderKey, sn.uuid).apply();
            }

            // Post silenced notification
            if (isSilenced) {
                Bitmap largeIconBitmap = largeIconBytesFinal != null ? byteArrayToBitmap(largeIconBytesFinal) : null;
                postSilencedNotification(sn, smallIcon, largeIconBitmap);
            }

            Log.d("NotificationService", "Notification processed: " + sn.uuid);
        });
    }

    // Helper method to convert Bitmap to byte array
    private byte[] bitmapToByteArray(Bitmap bitmap) {
        try (ByteArrayOutputStream stream = new ByteArrayOutputStream()) {
            bitmap.compress(Bitmap.CompressFormat.PNG, 100, stream);
            return stream.toByteArray();
        } catch (IOException e) {
            Log.e("NotificationService", "Error converting bitmap to bytes", e);
            return null;
        }
    }

    // Helper method to convert byte array back to Bitmap
    private Bitmap byteArrayToBitmap(byte[] bytes) {
        if (bytes == null || bytes.length == 0) return null;
        return android.graphics.BitmapFactory.decodeByteArray(bytes, 0, bytes.length);
    }

    public boolean isStandardNotification(StatusBarNotification sbn) {
        // 1. Must not be a self-notification
        if (sbn.getPackageName().equals(getPackageName()))
            return false;
        // 2. Must be user-clearable
        if (!sbn.isClearable())
            return false;
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

    private void postSilencedNotification(StoredNotification notification, Icon smallIcon, Bitmap largeIcon) {
        // Create notification builder
        Notification.Builder builder = new Notification.Builder(getApplicationContext(), CHANNEL_ID)
                .setSmallIcon(R.drawable.outline_notifications_off_24)
                .setContentTitle(getString(R.string.silenced_notification, notification.title))
                .setContentText(notification.text)
                .setAutoCancel(true)
                .setShowWhen(true)
                .setWhen(notification.postTime);

        if (largeIcon != null) {
            builder.setLargeIcon(largeIcon);
        } else {
            // Set large icon as the original app icon (fallback)
            Pair<CharSequence, Drawable> appInfo = MainActivity.getAppInfo(getApplicationContext(), notification.packageName);
            if (appInfo.second != null) {
                Bitmap iconBitmap;
                if (appInfo.second instanceof BitmapDrawable) {
                    iconBitmap = ((BitmapDrawable) appInfo.second).getBitmap();
                } else {
                    // Convert non-BitmapDrawable to Bitmap
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

        // Set tap action to open notification's original activity
        Intent launchIntent = getPackageManager().getLaunchIntentForPackage(notification.packageName);
        if (launchIntent != null) {
            builder.setContentIntent(PendingIntent.getActivity(
                    getApplicationContext(), 0, launchIntent,
                    PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE));
        }

        // Finally notify
        manager.notify(random.nextInt(Integer.MAX_VALUE), builder.build());
    }


    private void cleanExpiredCache() {
        long currentTime = System.currentTimeMillis();
        recentNotifications.entrySet().removeIf(entry ->
                currentTime - entry.getValue() > CACHE_DURATION);
    }

    private boolean checkMemoryCache(StoredNotification notification) {
        Long lastSeen = recentNotifications.get(notification.dedupeKey);
        if (lastSeen != null) {
            long timeDiff = System.currentTimeMillis() - lastSeen;
            return timeDiff <= CACHE_DURATION;
        }
        return false;
    }

    private void addToMemoryCache(StoredNotification notification) {
        // Prevent unbounded growth
        if (recentNotifications.size() >= MAX_CACHE_SIZE) {
            cleanExpiredCache();

            // If still too large, remove oldest entries
            if (recentNotifications.size() >= MAX_CACHE_SIZE) {
                String oldestKey = recentNotifications.entrySet().stream()
                        .min(Map.Entry.comparingByValue())
                        .map(Map.Entry::getKey)
                        .orElse(null);
                if (oldestKey != null) {
                    recentNotifications.remove(oldestKey);
                }
            }
        }
        recentNotifications.put(notification.dedupeKey, System.currentTimeMillis());
    }
}