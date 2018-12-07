package org.chimple.flores.db.entity;
import com.google.gson.annotations.Expose;
import com.google.gson.annotations.SerializedName;
import java.util.List;

public class HandShakingMessage {
    @Expose(serialize = true, deserialize = true)
    @SerializedName("mt")
    String messageType;

    @Expose(serialize = true, deserialize = true)
    @SerializedName("r")
    String reply;


    @Expose(serialize = true, deserialize = true)
    @SerializedName("f")
    String from;

    @Expose(serialize = true, deserialize = true)
    @SerializedName("bt")
    String bt;

    public String getBt() { return bt; }

    public String getFrom() { return from; }

    public String getReply() { return reply; }

    public String getMessageType() {
        return messageType;
    }

    public List<HandShakingInfo> getInfos() {
        return infos;
    }

    @Expose(serialize = true, deserialize = true)
    @SerializedName("i")
    List<HandShakingInfo> infos;

    public HandShakingMessage(String from, String messageType, String reply, List<HandShakingInfo> infos, String bt) {
        this.messageType = messageType;
        this.infos = infos;
        this.from = from;
        this.reply = reply;
        this.bt = bt;
    }


    public boolean equals(final Object obj) {
        if (obj == null) {
            return false;
        }
        final HandShakingMessage info = (HandShakingMessage) obj;
        if (this == info) {
            return true;
        } else {
            return (this.messageType.equals(info.messageType) && this.from.equals(info.from) && this.infos == info.infos);
        }
    }


    public int hashCode() {
        int hashno = 7;
        hashno = 13 * hashno + from.hashCode() +(messageType == null ? 0 : messageType.hashCode()) + (infos == null ? 0 : infos.hashCode());
        return hashno;
    }
}