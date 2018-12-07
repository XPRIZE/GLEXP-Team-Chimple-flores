package org.chimple.flores.db.entity;

import android.arch.persistence.room.ColumnInfo;

public class P2PLatestInfoByUserAndDevice {

    @ColumnInfo(name = "user_id")
    public String userId;

    @ColumnInfo(name = "device_id")
    public String deviceId;

    public String getUserId() {
        return userId;
    }

    public void setUserId(String userId) {
        this.userId = userId;
    }

    public String getDeviceId() {
        return deviceId;
    }

    public void setDeviceId(String deviceId) {
        this.deviceId = deviceId;
    }

    public Long getSequence() {
        return sequence;
    }

    public void setSequence(Long sequence) {
        this.sequence = sequence;
    }

    @ColumnInfo(name = "sequence")
    public Long sequence;



}
