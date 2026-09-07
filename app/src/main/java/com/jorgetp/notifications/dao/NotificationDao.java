package com.jorgetp.notifications.dao;

import androidx.lifecycle.LiveData;
import androidx.room.Dao;
import androidx.room.Insert;
import androidx.room.OnConflictStrategy;
import androidx.room.Query;

import java.util.List;

@Dao
public interface NotificationDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    void insertOrUpdate(StoredNotification notification);

    @Query("SELECT * FROM notifications WHERE packageName NOT IN (:hiddenApps) AND pinned = :pinned ORDER BY postTime DESC LIMIT :limit")
    List<StoredNotification> getByPinned(List<String> hiddenApps, int pinned, int limit);

    @Query("DELETE FROM notifications WHERE id = :id")
    void delete(String id);

    @Query("DELETE FROM notifications WHERE pinned = 0")
    void deleteUnpinned();

    @Query("SELECT * FROM notifications ORDER BY postTime DESC LIMIT 1")
    LiveData<StoredNotification> observeLast();
}