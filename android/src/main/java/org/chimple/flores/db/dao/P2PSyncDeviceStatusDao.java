package org.chimple.flores.db.dao;

import android.arch.persistence.room.Dao;
import android.arch.persistence.room.Insert;
import android.arch.persistence.room.OnConflictStrategy;
import android.arch.persistence.room.Query;

import org.chimple.flores.db.entity.P2PSyncDeviceStatus;

@Dao
public interface P2PSyncDeviceStatusDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    public Long insertP2PSyncDeviceStatus(P2PSyncDeviceStatus info);

    @Query("SELECT * FROM P2PSyncDeviceStatus WHERE device_id = :deviceId")
    public P2PSyncDeviceStatus getDeviceInfo(String deviceId);

    @Query("SELECT * FROM P2PSyncDeviceStatus WHERE sync_time is not null order by discover_time asc")
    public P2PSyncDeviceStatus[] getAllSyncDevices();

    @Query("SELECT * FROM P2PSyncDeviceStatus WHERE sync_time is null order by discover_time asc")
    public P2PSyncDeviceStatus[] getAllNotSyncDevices();

    @Query("SELECT * FROM P2PSyncDeviceStatus WHERE sync_time is null and sync_immediately = 1 order by discover_time asc limit 1")
    public P2PSyncDeviceStatus getTopDeviceToSyncImmediately();

    @Query("SELECT * FROM P2PSyncDeviceStatus WHERE sync_time is null and (sync_immediately  = 0 or sync_immediately is null) order by discover_time asc limit 1")
    public P2PSyncDeviceStatus getTopDeviceToNotSyncImmediately();

    @Query("SELECT * FROM P2PSyncDeviceStatus WHERE sync_time is null and device_id in (:deviceIds) and sync_immediately = 1 order by discover_time asc limit 1")
    public P2PSyncDeviceStatus getTopDeviceToSyncImmediately(String deviceIds);

    @Query("SELECT * FROM P2PSyncDeviceStatus WHERE sync_time is null and device_id in (:deviceIds) and (sync_immediately  = 0 or sync_immediately is null) order by discover_time asc limit 1")
    public P2PSyncDeviceStatus getTopDeviceToNotSyncImmediately(String deviceIds);

}
