package org.chimple.flores.db.entity;


import android.arch.persistence.room.ColumnInfo;
import android.arch.persistence.room.Entity;
import android.arch.persistence.room.Ignore;
import android.arch.persistence.room.Index;
import android.arch.persistence.room.PrimaryKey;
import android.support.annotation.NonNull;

import com.google.gson.annotations.Expose;

import java.util.Date;

@Entity(indices = {
        @Index("device_id"),
        @Index("sync_immediately")
})
public class P2PSyncDeviceStatus {

    public P2PSyncDeviceStatus() {
        this.syncImmediately = false;
        this.discoverTime = new Date();
        this.syncTime = null;
    }

    @Ignore
    public P2PSyncDeviceStatus(String deviceId, boolean syncImmediately) {
        this.deviceId = deviceId;
        this.syncImmediately = syncImmediately ? true : false;
        this.discoverTime = new Date();
        this.syncTime = null;
    }

    @PrimaryKey
    @ColumnInfo(name = "device_id")
    @NonNull
    public String deviceId; //current logged-in user

    @ColumnInfo(name = "discover_time")
    public Date discoverTime;

    @ColumnInfo(name = "sync_time")
    public Date syncTime;

    @ColumnInfo(name = "sync_immediately")
    public Boolean syncImmediately;


    public String getDeviceId() {
        return deviceId;
    }

    public void setDeviceId(String deviceId) {
        this.deviceId = deviceId;
    }

    public Date getDiscoverTime() {
        return discoverTime;
    }

    public void setDiscoverTime(Date discoverTime) {
        this.discoverTime = discoverTime;
    }

    public Date getSyncTime() {
        return syncTime;
    }

    public void setSyncTime(Date syncTime) {
        this.syncTime = syncTime;
    }

    public Boolean getSyncImmediately() {
        return syncImmediately;
    }

    public void setSyncImmediately(Boolean syncImmediately) {
        this.syncImmediately = syncImmediately;
    }

    public String print() {
        return "P2PSyncDeviceStatus:" + " deviceId:" + deviceId;
    }
}
