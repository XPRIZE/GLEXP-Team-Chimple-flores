package org.chimple.flores.db.entity;

import com.google.gson.JsonDeserializationContext;
import com.google.gson.JsonDeserializer;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;

import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.Arrays;


public class SyncRequestMessageDeserializer implements JsonDeserializer<SyncInfoRequestMessage> {

    public SyncInfoRequestMessage deserialize(final JsonElement json, final Type typeOfT, final JsonDeserializationContext context)
            throws JsonParseException {

        final JsonObject jsonObject = json.getAsJsonObject();

        final JsonElement jsonMessageType = jsonObject.get("mt");
        String messageType = "";
        if (jsonMessageType != null) {
            messageType = jsonMessageType.getAsString();
        }

        final JsonElement jsonMd = jsonObject.get("md");
        String md = "";
        if (jsonMd != null) {
            md = jsonMd.getAsString();
        }

        final JsonElement jsonSender = jsonObject.get("s");
        String sender = "";
        if (jsonSender != null) {
            sender = jsonSender.getAsString();
        }

        SyncInfoItem[] infos = context.deserialize(jsonObject.get("l"), SyncInfoItem[].class);
        final SyncInfoRequestMessage message = new SyncInfoRequestMessage(sender, md, new ArrayList(Arrays.asList(infos)));
        return message;
    }
}