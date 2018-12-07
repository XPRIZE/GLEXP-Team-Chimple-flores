package org.chimple.flores.db.entity;

import com.google.gson.JsonDeserializationContext;
import com.google.gson.JsonDeserializer;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;

import java.lang.reflect.Type;

public class SyncItemDeserializer implements JsonDeserializer<SyncInfoItem> {

    public SyncInfoItem deserialize(final JsonElement json, final Type typeOfT, final JsonDeserializationContext context)
            throws JsonParseException {

        final JsonObject jsonObject = json.getAsJsonObject();
        final JsonElement jsonUserId = jsonObject.get("u");

        String userId = null;
        if (jsonUserId != null) {
            userId = jsonUserId.getAsString();
        }

        String deviceId = null;
        final JsonElement jsonDeviceId = jsonObject.get("d");
        if (jsonDeviceId != null) {
            deviceId = jsonDeviceId.getAsString();
        }

        Long sequence = 0L;
        final JsonElement jsonSequence = jsonObject.get("s");
        if (jsonSequence != null) {
            sequence = jsonSequence.getAsLong();
        }

        Long startingSequence = 0L;
        final JsonElement jsonStartingSequence = jsonObject.get("ss");
        if (jsonStartingSequence != null) {
            startingSequence = jsonStartingSequence.getAsLong();
        }


        final SyncInfoItem item = new SyncInfoItem(userId, deviceId, startingSequence, sequence);
        return item;
    }
}