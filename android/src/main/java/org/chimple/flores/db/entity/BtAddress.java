package org.chimple.flores.db.entity;


import android.arch.persistence.room.ColumnInfo;
import android.arch.persistence.room.Entity;
import android.arch.persistence.room.Ignore;
import android.arch.persistence.room.Index;
import android.arch.persistence.room.PrimaryKey;
import android.support.annotation.NonNull;

import java.util.Date;

@Entity(indices = {
        @Index("device_id"),
        @Index("bt_address")
})
public class BtAddress {

    public BtAddress() {
    }

    @Ignore
    public BtAddress(String deviceId, String bluetoothAddress) {
        this.deviceId = deviceId;
        this.btAddress = bluetoothAddress;
    }

    @NonNull
    public String getDeviceId() {
        return deviceId;
    }

    public void setDeviceId(@NonNull String deviceId) {
        this.deviceId = deviceId;
    }

    @NonNull
    public String getBtAddress() {
        return btAddress;
    }

    public void setBtAddress(@NonNull String btAddress) {
        this.btAddress = btAddress;
    }

    @PrimaryKey
    @ColumnInfo(name = "device_id")
    @NonNull
    public String deviceId; //current logged-in user


    @ColumnInfo(name = "bt_address")
    @NonNull
    public String btAddress; //current logged-in user


}
