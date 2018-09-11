package org.chimple.flores.db.entity;

import android.arch.persistence.room.ColumnInfo;

public class P2PUserIdMessage {
    @ColumnInfo(name = "user_id")
    public String userId;

    @ColumnInfo(name = "message")
    public String message;
}
