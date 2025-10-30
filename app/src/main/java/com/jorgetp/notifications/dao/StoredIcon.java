package com.jorgetp.notifications.dao;

import androidx.annotation.NonNull;
import androidx.room.Entity;

@Entity(tableName = "icons", primaryKeys = {"packageName", "senderName"})
public class StoredIcon {
    @NonNull
    public String packageName;

    @NonNull
    public String senderName;

    public byte[] iconData;

    public long lastUpdated;

    public StoredIcon(@NonNull String packageName, @NonNull String senderName, byte[] iconData, long lastUpdated) {
        this.packageName = packageName;
        this.senderName = senderName;
        this.iconData = iconData;
        this.lastUpdated = lastUpdated;
    }
}