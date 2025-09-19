package com.jorgetp.notifications.dao;

import androidx.lifecycle.LiveData;
import androidx.room.Dao;
import androidx.room.Insert;
import androidx.room.Query;

import java.util.List;

@Dao
public interface NotificationDao {
    @Insert
    void insert(StoredNotification notification);

    @Query("SELECT * FROM notifications WHERE packageName = :packageName ORDER BY postTime DESC LIMIT :limit")
    List<StoredNotification> getForPackage(String packageName, int limit);

    @Query("SELECT * FROM notifications ORDER BY postTime DESC LIMIT :limit")
    List<StoredNotification> getAll(int limit);

    @Query("DELETE FROM notifications")
    void deleteAll();

    @Query("SELECT * FROM notifications ORDER BY postTime DESC LIMIT 1")
    LiveData<StoredNotification> observeLast();

    @Query("SELECT * FROM notifications ORDER BY postTime DESC LIMIT 1")
    StoredNotification getLast();

    @Query("SELECT DISTINCT packageName FROM notifications")
    List<String> getPackages();
}
