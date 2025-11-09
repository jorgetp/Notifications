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

    @Query(value = "SELECT * FROM notifications WHERE uuid = :uuid")
    StoredNotification get(String uuid);

    @Query(value = "SELECT * FROM notifications WHERE dedupeKey = :dedupeKey")
    StoredNotification getByDedupeKey(String dedupeKey);

    @Query("SELECT * FROM notifications WHERE packageName LIKE :packageName AND pinned = 1 ORDER BY postTime DESC LIMIT :limit")
    List<StoredNotification> getPinnedByPackage(String packageName, int limit);

    @Query("SELECT * FROM notifications WHERE packageName LIKE :packageName AND pinned = 0 ORDER BY postTime DESC LIMIT :limit")
    List<StoredNotification> getUnpinnedByPackage(String packageName, int limit);

    @Query("DELETE FROM notifications WHERE uuid = :uuid")
    void delete(String uuid);

    @Query("DELETE FROM notifications")
    void deleteAll();

    @Query("SELECT * FROM notifications ORDER BY postTime DESC LIMIT 1")
    LiveData<StoredNotification> observeLast();

    @Query("SELECT DISTINCT packageName FROM notifications")
    List<String> getPackages();

    @Query("UPDATE notifications SET pinned = :pinned WHERE uuid = :uuid")
    void updatePinned(String uuid, boolean pinned);

    @Query("SELECT * FROM notifications WHERE pinned = 1 ORDER BY postTime DESC")
    List<StoredNotification> getPinned();
}