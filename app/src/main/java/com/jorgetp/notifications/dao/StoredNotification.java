package com.jorgetp.notifications.dao;

import androidx.annotation.NonNull;
import androidx.room.ColumnInfo;
import androidx.room.Entity;
import androidx.room.PrimaryKey;

@Entity(tableName = "notifications")
public class StoredNotification {
    @NonNull
    @PrimaryKey()
    public String uuid;

    public long postTime;
    public String dedupeKey;
    public String packageName;
    public String title;
    public String text;
    public String category;

    @ColumnInfo(defaultValue = "0")
    public boolean pinned = false;

    public StoredNotification(long postTime, @NonNull String uuid, String packageName, String title, String text, String category) {
        this.postTime = postTime;
        this.uuid = uuid;
        this.packageName = packageName;
        this.title = title;
        this.text = text;
        this.category = category;
        this.pinned = false;

        long postTimeBlock = postTime / 30000;
        String shortText = text.substring(0, Math.min(300, text.length()));
        dedupeKey = postTimeBlock + "|" + packageName + "|" + title + "|" + shortText;
    }
}