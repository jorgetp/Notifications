package com.jorgetp.notifications.dao;

import androidx.room.Database;
import androidx.room.RoomDatabase;

@Database(entities = {StoredNotification.class}, version = 2)
public abstract class AppDatabase extends RoomDatabase {
    public abstract NotificationDao notificationDao();
}