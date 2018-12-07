package org.chimple.flores.db.entity;

import com.google.gson.annotations.Expose;
import com.google.gson.annotations.SerializedName;

import java.util.List;

public class SyncInfoMessage {
    @Expose(serialize = true, deserialize = true)
    @SerializedName("mt")
    String messageType;

    @Expose(serialize = true, deserialize = true)
    @SerializedName("i")
    List<P2PSyncInfo> infos;

    @Expose(serialize = true, deserialize = true)
    @SerializedName("s")
    String sender;

    public SyncInfoMessage(String messageType, String sender, List<P2PSyncInfo> infos) {
        this.messageType = messageType;
        this.infos = infos;
        this.sender = sender;
    }


    public boolean equals(final Object obj) {
        if (obj == null) {
            return false;
        }
        final SyncInfoMessage info = (SyncInfoMessage) obj;
        if (this == info) {
            return true;
        } else {
            return (this.messageType.equals(info.messageType) && this.infos == info.infos);
        }
    }


    public int hashCode() {
        int hashno = 7;
        hashno = 13 * hashno + (messageType == null ? 0 : messageType.hashCode()) + (infos == null ? 0 : infos.hashCode());
        return hashno;
    }

    public String getMessageType() {
        return messageType;
    }

    public List<P2PSyncInfo> getInfos() {
        return infos;
    }

    public String getSender() {
        return sender;
    }

    public void setSender(String sender) {
        this.sender = sender;
    }
}
