package com.jorgetp.notifications.dao;

import androidx.room.Database;
import androidx.room.RoomDatabase;

@Database(entities = {StoredNotification.class}, version = 1)
public abstract class AppDatabase extends RoomDatabase {
    public abstract NotificationDao notificationDao();
}
