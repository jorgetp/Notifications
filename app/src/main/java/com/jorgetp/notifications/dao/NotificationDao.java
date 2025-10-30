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

    @Query(value = "SELECT * FROM notifications WHERE `key` = :key")
    StoredNotification getByKey(String key);

    @Query("SELECT * FROM notifications WHERE packageName LIKE :packageName ORDER BY postTime DESC LIMIT :limit")
    List<StoredNotification> getByPackage(String packageName, int limit);

    @Query("DELETE FROM notifications")
    void deleteAll();

    @Query("SELECT * FROM notifications ORDER BY postTime DESC LIMIT 1")
    LiveData<StoredNotification> observeLast();

    @Query("SELECT DISTINCT packageName FROM notifications")
    List<String> getPackages();
}