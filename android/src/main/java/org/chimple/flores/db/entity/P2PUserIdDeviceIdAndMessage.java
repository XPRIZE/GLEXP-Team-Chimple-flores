package org.chimple.flores.db.entity;

import android.arch.persistence.room.ColumnInfo;

public class P2PUserIdDeviceIdAndMessage {
    @ColumnInfo(name = "user_id")
    public String userId;

    @ColumnInfo(name = "device_id")
    public String deviceId;

    @ColumnInfo(name = "message")
    public String message;

}
