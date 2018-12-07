package org.chimple.flores.db.entity;

import com.google.gson.annotations.Expose;
import com.google.gson.annotations.SerializedName;


public class ProfileMessage {
    @Expose(serialize = true, deserialize = true)
    @SerializedName("u")
    String userId;

    @Expose(serialize = true, deserialize = true)
    @SerializedName("d")
    String deviceId;


    @Expose(serialize = true, deserialize = true)
    @SerializedName("mt")
    String messageType;

    @Expose(serialize = true, deserialize = true)
    @SerializedName("d")
    String data;

    public ProfileMessage(String userId, String deviceId, String messageType, String data) {
        this.messageType = messageType;
        this.data = data;
        this.userId = userId;
        this.deviceId = deviceId;
    }


    public boolean equals(final Object obj) {
        if (obj == null) {
            return false;
        }
        final ProfileMessage info = (ProfileMessage) obj;
        if (this == info) {
            return true;
        } else {
            return (this.messageType.equals(info.messageType) && this.userId.equals(info.userId) && this.data.equals(info.data) && this.deviceId.equals(info.deviceId));
        }
    }


    public int hashCode() {
        int hashno = 7;
        hashno = 13 * hashno + (messageType == null ? 0 : messageType.hashCode()) + (data == null ? 0 : data.hashCode() + (userId == null ? 0 : userId.hashCode()) + (deviceId == null ? 0 : deviceId.hashCode()));
        return hashno;
    }

    public String getUserId() {
        return userId;
    }

    public void setUserId(String userId) {
        this.userId = userId;
    }

    public String getMessageType() {
        return messageType;

    }

    public String getDeviceId() {
        return deviceId;
    }

    public void setDeviceId(String deviceId) {
        this.deviceId = deviceId;
    }

    public String getData() {
        return data;
    }
}
