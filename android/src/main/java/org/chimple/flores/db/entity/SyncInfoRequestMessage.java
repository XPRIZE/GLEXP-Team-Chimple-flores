package org.chimple.flores.db.entity;

import com.google.gson.annotations.Expose;
import com.google.gson.annotations.SerializedName;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

public class SyncInfoRequestMessage {

    public static final String MESSAGE_TYPE = "syncInfoRequestMessage";

    @Expose(serialize = true, deserialize = true)
    @SerializedName("mt")
    String messageType;

    @Expose(serialize = true, deserialize = true)
    @SerializedName("md")
    String mDeviceId;

    @Expose(serialize = true, deserialize = true)
    @SerializedName("s")
    String sender;


    @Expose(serialize = true, deserialize = true)
    @SerializedName("l")
    List<SyncInfoItem> items;


    public SyncInfoRequestMessage(String sender, String mDeviceId, List<SyncInfoItem> items) {
        this.messageType = MESSAGE_TYPE;
        this.mDeviceId = mDeviceId;
        this.items = items;
        this.sender = sender;
    }

    public List<SyncInfoItem> getItems() {
        return items;
    }

    public String getMessageType() {

        return messageType;
    }

    public String getSender() {
        return sender;
    }

    public String getmDeviceId() {
        return mDeviceId;
    }


}
