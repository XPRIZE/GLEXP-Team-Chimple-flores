package org.chimple.flores.db.entity;

import com.google.gson.annotations.Expose;
import com.google.gson.annotations.SerializedName;

import java.util.List;

public class HandShakingMessage {
    @Expose(serialize = true, deserialize = true)
    @SerializedName("message_type")
    String messageType;

    public String getMessageType() {
        return messageType;
    }

    public List<HandShakingInfo> getInfos() {
        return infos;
    }

    @Expose(serialize = true, deserialize = true)
    @SerializedName("infos")
    List<HandShakingInfo> infos;

    public HandShakingMessage(String messageType, List<HandShakingInfo> infos) {
        this.messageType = messageType;
        this.infos = infos;
    }

    @Override
    public boolean equals(final Object obj) {
        if (obj == null) {
            return false;
        }
        final HandShakingMessage info = (HandShakingMessage) obj;
        if (this == info) {
            return true;
        } else {
            return (this.messageType.equals(info.messageType) && this.infos == info.infos);
        }
    }

    @Override
    public int hashCode() {
        int hashno = 7;
        hashno = 13 * hashno + (messageType == null ? 0 : messageType.hashCode()) + (infos == null ? 0 : infos.hashCode());
        return hashno;
    }
}
