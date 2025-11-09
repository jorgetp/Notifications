package com.jorgetp.notifications.dao;

import androidx.room.Dao;
import androidx.room.Insert;
import androidx.room.OnConflictStrategy;
import androidx.room.Query;

import java.util.List;

@Dao
public interface IconDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    void insertOrUpdate(StoredIcon icon);

    @Query("SELECT * FROM icons WHERE packageName = :packageName AND senderName = :senderName")
    StoredIcon get(String packageName, String senderName);

    @Query("DELETE FROM icons")
    void deleteAll();

    @Query("SELECT * FROM icons")
    List<StoredIcon> getAll();

    // Delete icons that don't match any important sender keys
    @Query("DELETE FROM icons WHERE (packageName || '/' || senderName) NOT IN (:importantSenderKeys)")
    void deleteOrphanedIcons(List<String> importantSenderKeys);
}