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
    StoredIcon getIcon(String packageName, String senderName);

    @Query("DELETE FROM icons")
    void deleteAll();

    // Get all package/sender combinations that have icons
    @Query("SELECT packageName || '/' || senderName FROM icons")
    List<String> getAllIconKeys();

    // Delete icons that don't match any important sender keys
    @Query("DELETE FROM icons WHERE (packageName || '/' || senderName) NOT IN (:importantSenderKeys)")
    void deleteOrphanedIcons(List<String> importantSenderKeys);
}