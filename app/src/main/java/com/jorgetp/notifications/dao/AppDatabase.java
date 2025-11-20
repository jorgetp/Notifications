package com.jorgetp.notifications.dao;

import androidx.room.Database;
import androidx.room.RoomDatabase;

@Database(entities = {StoredNotification.class, StoredIcon.class}, version = 7)
public abstract class AppDatabase extends RoomDatabase {
    public abstract NotificationDao notificationDao();

    public abstract IconDao iconDao();
}