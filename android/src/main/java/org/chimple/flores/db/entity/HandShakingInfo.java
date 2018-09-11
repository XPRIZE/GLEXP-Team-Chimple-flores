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
    private String userId;

    @Expose(serialize = true, deserialize = true)
    private String deviceId;

    @Expose(serialize = true, deserialize = true)
    private Long sequence;

    @Expose(serialize = false, deserialize = false)
    private Long startingSequence;

    public HandShakingInfo() {
    }


    public HandShakingInfo(String userId, String deviceId, Long sequence) {
        this.userId = userId;
        this.deviceId = deviceId;
        this.sequence = sequence;
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

    public Long getStartingSequence() {
        return startingSequence;
    }

    public void setStartingSequence(Long startingSequence) {
        this.startingSequence = startingSequence;
    }

    public void setSequence(Long sequence) {
        this.sequence = sequence;
    }

    @Override
    public boolean equals(final Object obj) {
        if (obj == null) {
            return false;
        }
        final HandShakingInfo info = (HandShakingInfo) obj;
        if (this == info) {
            return true;
        } else {
            return (this.userId.equals(info.userId) && this.deviceId.equals(info.deviceId) && this.sequence.longValue() == info.sequence.longValue());
        }
    }

    @Override
    public int hashCode() {
        int hashno = 7;
        hashno = 13 * hashno + (userId == null ? 0 : userId.hashCode()) + (deviceId == null ? 0 : deviceId.hashCode()) + (sequence == null ? 0 : sequence.hashCode());
        return hashno;
    }
}