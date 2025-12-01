package com.jorgetp.notifications.dao;

import androidx.annotation.NonNull;
import androidx.room.ColumnInfo;
import androidx.room.Entity;
import androidx.room.PrimaryKey;

@Entity(tableName = "notifications")
public class StoredNotification {
    @NonNull
    @PrimaryKey()
    public String id;

    public long postTime;
    public String packageName;
    public String title;
    public String text;
    public String category;

    @ColumnInfo(defaultValue = "0")
    public boolean pinned;

    public StoredNotification(@NonNull String id, long postTime, String packageName, String title, String text, String category) {
        this.postTime = postTime;
        this.id = id;
        this.packageName = packageName;
        this.title = title;
        this.text = text;
        this.category = category;
        this.pinned = false;
    }

    // Static method to generate dedupeKey
    public static String generateDedupeKey(long postTime, String packageName, String title, String text) {
        long postTimeBlock = postTime / 60000; // 1-minute blocks
        // String shortText = text.substring(0, Math.min(300, text.length()));
        return postTimeBlock + "|" + packageName + "|" + title + "|" + /*shortText*/ text.hashCode();
    }
}