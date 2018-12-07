package org.chimple.flores.db.entity;

import com.google.gson.JsonDeserializationContext;
import com.google.gson.JsonDeserializer;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import com.google.gson.annotations.Expose;
import com.google.gson.annotations.SerializedName;

import org.apache.commons.beanutils.BeanComparator;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.collections4.ListUtils;
import org.apache.commons.collections4.comparators.ComparatorChain;

import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

public class HandShakingInfo {

    @Expose(serialize = true, deserialize = true)
    @SerializedName("u")
    private String userId;

    @Expose(serialize = true, deserialize = true)
    @SerializedName("d")
    private String deviceId;

    @Expose(serialize = true, deserialize = true)
    @SerializedName("s")
    private Long sequence;

    @Expose(serialize = true, deserialize = true)
    @SerializedName("ss")
    private Long startingSequence;

    @Expose(serialize = true, deserialize = true)
    @SerializedName("f")
    private String from;

    @Expose(serialize = true, deserialize = true)
    @SerializedName("ms")
    private String missingMessages;

    @Expose(serialize = true, deserialize = true)
    @SerializedName("ps")
    private Long profileSequence;


    public HandShakingInfo() {
    }


    public HandShakingInfo(String userId, String deviceId, Long sequence, String missingMessages, Long profileSequence) {
        this.userId = userId;
        this.deviceId = deviceId;
        this.sequence = sequence;
        this.missingMessages = missingMessages;
        this.profileSequence = profileSequence;
    }

    public String getUserId() {
        return userId;
    }

    public String getMissingMessages() {
        return missingMessages;
    }

    public String getDeviceId() {
        return deviceId;
    }

    public Long getSequence() {
        return sequence;
    }

    public Long getStartingSequence() {
        return startingSequence;
    }

    public void setStartingSequence(Long startingSequence) {
        this.startingSequence = startingSequence;
    }

    public void setMissingMessages(String missingMessages) {
        this.missingMessages = missingMessages;
    }

    public String getFrom() {
        return from;
    }

    public void setFrom(String sFrom) {
        this.from = sFrom;
    }

    public void setSequence(Long sequence) {
        this.sequence = sequence;
    }


    public Long getProfileSequence() {
        return profileSequence;
    }

    public void setProfileSequence(Long profileSequence) {
        this.profileSequence = profileSequence;
    }

    public boolean equals(final Object obj) {
        if (obj == null) {
            return false;
        }

        final HandShakingInfo info = (HandShakingInfo) obj;
        if (this == info) {
            return true;
        } else {
            return (this.userId.equals(info.userId) && this.missingMessages.equals(info.missingMessages) && this.deviceId.equals(info.deviceId) && this.sequence.longValue() == info.sequence.longValue() && this.profileSequence.longValue() == info.profileSequence.longValue());
        }
    }


    public int hashCode() {
        int hashno = 7;
        hashno = 13 * hashno + (userId == null ? 0 : userId.hashCode()) + (missingMessages == null ? 0 : missingMessages.hashCode()) +(deviceId == null ? 0 : deviceId.hashCode()) + (sequence == null ? 0 : sequence.hashCode() + (profileSequence == null ? 0 : profileSequence.hashCode()));
        return hashno;
    }
}