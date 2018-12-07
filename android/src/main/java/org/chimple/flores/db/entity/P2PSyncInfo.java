package org.chimple.flores.db.entity;

import android.arch.persistence.room.ColumnInfo;
import android.arch.persistence.room.Entity;
import android.arch.persistence.room.Ignore;
import android.arch.persistence.room.Index;
import android.arch.persistence.room.PrimaryKey;
import android.util.Log;
import com.google.gson.annotations.Expose;

import java.io.Serializable;
import java.util.Date;

@Entity(indices = {
        @Index("user_id"),
        @Index("device_id"),
        @Index("sequence"),
        @Index("session_id"),
        @Index("status")
})
public class P2PSyncInfo implements Serializable {

    public P2PSyncInfo() {

    }

    @Ignore
    public P2PSyncInfo(String userId, String deviceId, Long sequence, String recipientUserId, String message, String messageType, Date createdAt) {
        this.userId = userId;
        this.deviceId = deviceId;
        this.sequence = sequence;
        this.recipientUserId = recipientUserId;
        this.message = message;
        this.messageType = messageType;
        this.sender = deviceId;
        this.loggedAt = new Date();
        if(createdAt != null) {
            this.createdAt = createdAt;
        } else {
            this.createdAt = new Date();
        }
        Log.d("P2P Sync Info", "loggedAt:" + this.loggedAt);
        Log.d("P2P Sync Info", "createdAt:" + this.createdAt);
    }


    @PrimaryKey(autoGenerate = true)
    public Long id; // auto generated primary key

    @Expose(serialize = true, deserialize = true)
    @ColumnInfo(name = "user_id")
    public String userId; //current logged-in user

    @Expose(serialize = true, deserialize = true)
    @ColumnInfo(name = "device_id")
    public String deviceId;

    @Expose(serialize = true, deserialize = true)
    @ColumnInfo(name = "sequence")
    public Long sequence;

    @Expose(serialize = true, deserialize = true)
    @ColumnInfo(name = "message_type")
    public String messageType;

    @Expose(serialize = true, deserialize = true)
    @ColumnInfo(name = "recipient_user_id")
    public String recipientUserId;

    @Expose(serialize = true, deserialize = true)
    @ColumnInfo(name = "message")
    public String message;

    @ColumnInfo(name = "status")
    public Boolean status;

    @ColumnInfo(name = "session_id")
    public String sessionId;

    @ColumnInfo(name = "step")
    public Long step;


    public void setId(Long id) {
        this.id = id;
    }


    public Date getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Date createdAt) {
        this.createdAt = createdAt;
    }

    @Expose(serialize = false, deserialize = false)
    @ColumnInfo(name = "logged_at")
    public Date loggedAt;

    @Expose(serialize = true, deserialize = true)
    @ColumnInfo(name = "created_at")
    public Date createdAt;

    private String sender;

    public void setStatus(Boolean status) {
        this.status = status;
    }

    public void setSessionId(String sessionId) {
        this.sessionId = sessionId;
    }

    public void setLoggedAt(Date loggedAt) {
        this.loggedAt = loggedAt;
    }

    public void setUserId(String userId) {
        this.userId = userId;
    }

    public void setDeviceId(String deviceId) {
        this.deviceId = deviceId;
    }

    public void setSequence(Long sequence) {
        this.sequence = sequence;
    }

    public void setMessageType(String messageType) {
        this.messageType = messageType;
    }

    public void setRecipientUserId(String recipientUserId) {
        this.recipientUserId = recipientUserId;
    }

    public void setMessage(String message) {
        this.message = message;
    }

    public Long getId() {
        return id;
    }

    public String getUserId() {
        return userId;
    }

    public String getDeviceId() {
        return deviceId;
    }

    public Long getSequence() {
        return sequence;
    }

    public String getMessageType() {
        return messageType;
    }

    public String getRecipientUserId() {
        return recipientUserId;
    }

    public String getMessage() {
        return message;
    }

    public Boolean getStatus() {
        return status;
    }

    public void setStep(Long step) {
        this.step = step;
    }

    public String getSessionId() {
        return sessionId;
    }

    public Date getLoggedAt() {
        return loggedAt;
    }

    public Long getStep() {
        return step;
    }

    public String getSender() {
        return sender;
    }

    public void setSender(String sender) {
        this.sender = sender;
    }
}
