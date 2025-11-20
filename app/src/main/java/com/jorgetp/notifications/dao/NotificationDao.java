package com.jorgetp.notifications.dao;

import androidx.lifecycle.LiveData;
import androidx.room.Dao;
import androidx.room.Insert;
import androidx.room.Query;
import androidx.room.Update;

import java.util.List;

@Dao
public interface NotificationDao {
    @Insert
    void insert(StoredNotification notification);

    @Update
    void update(StoredNotification notification);

    @Query("SELECT * FROM notifications WHERE packageName LIKE :packageName AND pinned = :pinned ORDER BY postTime DESC LIMIT :limit")
    List<StoredNotification> getByPackageAndPinned(String packageName, int pinned, int limit);

    @Query("DELETE FROM notifications WHERE uuid = :uuid")
    void delete(String uuid);

    @Query("DELETE FROM notifications")
    void deleteAll();

    @Query("SELECT * FROM notifications ORDER BY postTime DESC LIMIT 1")
    LiveData<StoredNotification> observeLast();

    @Query("SELECT DISTINCT packageName FROM notifications")
    List<String> getPackages();
}