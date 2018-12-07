package org.chimple.flores.db.entity;

import com.google.gson.JsonDeserializationContext;
import com.google.gson.JsonDeserializer;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;

import java.lang.reflect.Type;
import java.util.Arrays;

public class ProfileMessageDeserializer implements JsonDeserializer<ProfileMessage> {

    public ProfileMessage deserialize(final JsonElement json, final Type typeOfT, final JsonDeserializationContext context)
            throws JsonParseException {

        final JsonObject jsonObject = json.getAsJsonObject();
        final JsonElement jsonMessageType = jsonObject.get("mt");
        final String messageType = jsonMessageType.getAsString();

        final JsonElement jsonDeviceType = jsonObject.get("d");
        final String deviceId = jsonDeviceType.getAsString();

        final JsonElement jsonUserIdType = jsonObject.get("u");
        final String userId = jsonUserIdType.getAsString();

        final JsonElement jsonDataType = jsonObject.get("d");
        final String data = jsonDataType.getAsString();

        final ProfileMessage profileMessage = new ProfileMessage(userId, deviceId, messageType, data);
        return profileMessage;
    }
}