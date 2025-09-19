package com.jorgetp.notifications.dao;

import android.content.Context;

import androidx.room.Room;

public class DbProvider {
    private static AppDatabase INSTANCE;

    public static AppDatabase get(Context context) {
        if (INSTANCE == null) {
            INSTANCE = Room.databaseBuilder(
                    context.getApplicationContext(),
                    AppDatabase.class,
                    "notification_store.db"
            ).build();
        }
        return INSTANCE;
    }
}
