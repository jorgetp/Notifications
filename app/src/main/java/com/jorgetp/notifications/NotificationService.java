package com.jorgetp.notifications;

import static com.jorgetp.notifications.MainActivity.ALWAYS;
import static com.jorgetp.notifications.MainActivity.IMPORTANT_SENDERS_PREFS;
import static com.jorgetp.notifications.MainActivity.NON_BUSINESS;
import static com.jorgetp.notifications.MainActivity.SILENCED_APPS_PREFS;

import android.app.Notification;
import android.app.NotificationManager;
import android.content.Context;
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

import com.jorgetp.notifications.dao.DbProvider;
import com.jorgetp.notifications.dao.IconDao;
import com.jorgetp.notifications.dao.NotificationDao;
import com.jorgetp.notifications.dao.StoredIcon;
import com.jorgetp.notifications.dao.StoredNotification;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.Calendar;
import java.util.concurrent.Executors;

public class NotificationService extends NotificationListenerService {
    private static final String[] FINANCIAL_APPS = {
            "com.bbva.bbvacontigo",
            "ch.viseca.visecaone",
            "com.revolut.revolut",
            "com.transferwise.android",
            "ch.zkb.twint"
    };
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
        if (notification == null || (notification.flags & Notification.FLAG_GROUP_SUMMARY) != 0)
            return;

        Bundle extras = notification.extras;
        String title = extras.getString(Notification.EXTRA_TITLE);
        CharSequence text = extras.getCharSequence(Notification.EXTRA_TEXT);

        // Get icons
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

        // Generate dedupeKey to use as primary key
        String dedupeKey = StoredNotification.generateDedupeKey(
                sbn.getPostTime(),
                sbn.getPackageName(),
                title != null ? title : "",
                text != null ? text.toString() : ""
        );

        // Create StoredNotification with dedupeKey as id (primary key)
        StoredNotification sn = new StoredNotification(
                dedupeKey,
                sbn.getPostTime(),
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

            // Set pinned
            String importantSenderKey = sbn.getPackageName() + "/" + title;
            //sn.pinned = importantSenders.contains(importantSenderKey) || isImportantFinancialNotification(sbn);
            sn.pinned = isImportantFinancialNotification(sbn);

            // Insert or update - duplicates will be automatically handled by database
            notificationDao.insertOrUpdate(sn);

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
                    importantSenders.edit().putString(importantSenderKey, sn.id).apply();
            }

            Log.d("NotificationService", "Notification processed: " + sn.id);
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

    public boolean isStandardNotification(StatusBarNotification sbn) {
        // 1. Must not be a self-notification
        if (sbn.getPackageName().equals(getPackageName()))
            return false;
        // 2. Must be user-clearable
        if (!sbn.isClearable())
            return false;
        // 3. Must not be ongoing
        if ((sbn.getNotification().flags & Notification.FLAG_ONGOING_EVENT) != 0)
            return false;
        return true;
    }

    private boolean isImportantNotification(StatusBarNotification sbn) {
        String packageName = sbn.getPackageName();
        Bundle extras = sbn.getNotification().extras;
        if (extras != null) {
            CharSequence title = extras.getCharSequence(Notification.EXTRA_TITLE);
            if (title != null)
                return MainActivity.getPrefs(this, IMPORTANT_SENDERS_PREFS)
                        .contains(packageName + "/" + title);
        }
        return false;
    }

    private boolean isFinancialNotification(StatusBarNotification sbn) {
        String packageName = sbn.getPackageName();
        for (String financialApp : FINANCIAL_APPS) {
            if (packageName.equals(financialApp)) {
                return true;
            }
        }
        return false;
    }

    private boolean containsCurrency(StatusBarNotification sbn) {
        Bundle extras = sbn.getNotification().extras;
        if (extras != null) {
            CharSequence text = extras.getCharSequence(Notification.EXTRA_TEXT);
            if (text != null) {
                try {
                    String currencySymbols = "[$€£¥₹₩₽₺₪₫฿₴₦₲₵₡₭₸៛]";
                    String currencyCodes = "(USD|EUR|GBP|CHF|JPY|INR|KRW|RUB|TRY|ILS|VND|THB|UAH|NGN|PYG|GHS|CRC|LAK|KZT|KHR)";
                    // return true if text contains either a currency symbol or code
                    return text.toString().matches(".*(" + currencySymbols + "|" + currencyCodes + ").*");
                } catch (Exception e) {
                    Log.e("NotificationService", "Error parsing amount", e);
                }
            }
        }
        return false;
    }

    private boolean isImportantFinancialNotification(StatusBarNotification sbn) {
        return isFinancialNotification(sbn) && containsCurrency(sbn);
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
}