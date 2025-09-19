package com.jorgetp.notifications.dao;

import androidx.annotation.NonNull;
import androidx.room.Entity;
import androidx.room.PrimaryKey;

@Entity(tableName = "notifications")
public class StoredNotification {
    @NonNull
    @PrimaryKey()
    public String key;

    public long postTime;
    public String uuid;
    public String packageName;
    public String title;
    public String text;
    public String category;

    public StoredNotification(long postTime, String uuid, String packageName, String title, String text, String category) {
        this.postTime = postTime;
        this.uuid = uuid;
        this.packageName = packageName;
        this.title = title;
        this.text = text;
        this.category = category;

        long postTimeBlock = postTime / 30000;
        String shortText = text.substring(0, Math.min(300, text.length()));
        key = postTimeBlock + "|" + packageName + "|" + title + "|" + shortText;
    }
}

