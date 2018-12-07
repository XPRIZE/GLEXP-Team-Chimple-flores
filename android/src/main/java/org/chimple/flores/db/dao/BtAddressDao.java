package org.chimple.flores.db.dao;

import android.arch.persistence.room.Dao;
import android.arch.persistence.room.Insert;
import android.arch.persistence.room.OnConflictStrategy;
import android.arch.persistence.room.Query;

import org.chimple.flores.db.entity.BtAddress;
import org.chimple.flores.db.entity.P2PLatestInfoByUserAndDevice;
import org.chimple.flores.db.entity.P2PSyncDeviceStatus;
import org.chimple.flores.db.entity.P2PSyncInfo;
import org.chimple.flores.db.entity.P2PUserIdDeviceId;
import org.chimple.flores.db.entity.P2PUserIdDeviceIdAndMessage;
import org.chimple.flores.db.entity.P2PUserIdMessage;

import java.util.List;


@Dao
public interface BtAddressDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    public Long insertBtInfo(BtAddress info);

    @Query("SELECT bt_address FROM BtAddress WHERE device_id = :deviceId limit 1")
    public String getBluetoothAddress(String deviceId);

    @Query("SELECT distinct(bt_address) from BtAddress")
    public String[] fetchAllSyncedDevices();
}


