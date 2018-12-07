package org.chimple.flores.db.entity;

import com.google.gson.annotations.Expose;
import com.google.gson.annotations.SerializedName;

import java.util.Collection;
import java.util.Iterator;

public class SyncInfoItem {
    @Expose(serialize = true, deserialize = true)
    @SerializedName("d")
    String deviceId;

    @Expose(serialize = true, deserialize = true)
    @SerializedName("u")
    String userId;

    @Expose(serialize = true, deserialize = true)
    @SerializedName("s")
    Long sequence;

    @Expose(serialize = true, deserialize = true)
    @SerializedName("ss")
    Long startingSequence;

    public SyncInfoItem(String userId, String deviceId, Long startingSequence, Long sequence) {
        this.userId = userId;
        this.deviceId = deviceId;
        this.startingSequence = startingSequence;
        this.sequence = sequence;
    }

    public String getDeviceId() {
        return deviceId;
    }

    public String getUserId() {
        return userId;
    }

    public Long getSequence() {
        return sequence;
    }

    public Long getStartingSequence() {
        return startingSequence;
    }
}
