package org.chimple.flores.db;

import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.util.Base64;
import android.util.Log;
import android.content.Intent;
import android.support.v4.content.LocalBroadcastManager;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonDeserializationContext;
import com.google.gson.JsonDeserializer;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import com.google.gson.reflect.TypeToken;


import org.apache.commons.collections4.Closure;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.collections4.IteratorUtils;
import org.apache.commons.collections4.Predicate;
import org.apache.commons.lang3.StringUtils;

import java.io.ByteArrayOutputStream;
import java.io.OutputStreamWriter;
import java.io.File;
import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.Date;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.Set;
import com.google.gson.stream.JsonWriter;

import org.chimple.flores.db.entity.HandShakingInfo;
import org.chimple.flores.db.entity.HandShakingInfoDeserializer;
import org.chimple.flores.db.entity.HandShakingMessage;
import org.chimple.flores.db.entity.HandShakingMessageDeserializer;
import org.chimple.flores.db.entity.P2PLatestInfoByUserAndDevice;
import org.chimple.flores.db.entity.P2PSyncDeviceStatus;
import org.chimple.flores.db.entity.P2PSyncInfo;
import org.chimple.flores.db.entity.P2PUserIdDeviceIdAndMessage;
import org.chimple.flores.db.entity.P2PUserIdMessage;
import org.chimple.flores.db.entity.ProfileMessage;
import org.chimple.flores.db.entity.ProfileMessageDeserializer;
import org.chimple.flores.sync.Direct.P2PSyncManager;
import org.chimple.flores.scheduler.JobUtils;
import org.chimple.flores.application.P2PApplication;
import org.chimple.flores.*;

import static org.chimple.flores.sync.Direct.P2PSyncManager.P2P_SHARED_PREF;

public class P2PDBApiImpl implements P2PDBApi {
    private static final String TAG = P2PDBApiImpl.class.getName();
    private AppDatabase db;
    private Context context;
    private static P2PDBApiImpl p2pDBApiInstance;

    public static P2PDBApiImpl getInstance(Context context) {
        synchronized (P2PDBApiImpl.class) {
            if (p2pDBApiInstance == null) {
                p2pDBApiInstance = new P2PDBApiImpl(AppDatabase.getInstance(context), context);
            }
            return p2pDBApiInstance;
        }
    }


    private P2PDBApiImpl(AppDatabase db, Context context) {
        this.db = db;
        this.context = context;
    }

    public void persistMessage(String userId, String deviceId, String recepientUserId, String message, String messageType) {
        Long maxSequence = db.p2pSyncDao().getLatestSequenceAvailableByUserIdAndDeviceId(userId, deviceId);
        if (maxSequence == null) {
            maxSequence = 0L;
        }

        maxSequence++;
        P2PSyncInfo info = new P2PSyncInfo(userId, deviceId, maxSequence, recepientUserId, message, messageType);
        this.persistP2PSyncMessage(info);
        Log.i(TAG, "inserted data" + info);
    }

    public void persistP2PSyncMessage(P2PSyncInfo message) {
        Log.i(TAG, "got Sync info:" + message.deviceId);
        Log.i(TAG, "got Sync info:" + message.userId);
        Log.i(TAG, "got Sync info:" + message.message);
        Log.i(TAG, "got Sync info:" + message.messageType);
        Log.i(TAG, "got Sync info:" + message.sequence);
        Log.i(TAG, "got Sync info:" + message.recipientUserId);
        Log.i(TAG, "inserted data" + message);

        List found = db.p2pSyncDao().fetchByUserAndDeviceAndSequence(message.getUserId(), message.getDeviceId(), message.sequence);
        if(found == null || found.size() == 0) {
            db.p2pSyncDao().insertP2PSyncInfo(message);
            Log.i(TAG, "inserted data" + message);
            SharedPreferences pref = this.context.getSharedPreferences(P2P_SHARED_PREF, 0);
            String userId = pref.getString("USER_ID", null); // getting String

            try {
                if((userId != null && message.recipientUserId != null && userId.equals(message.getRecipientUserId())) || message.messageType.equals("Photo")) {
                    Log.i(TAG, "messageReceived intent constructing for user" + userId);
                    //Intent intent = new Intent("org.chimple.flores.FloresPlugin$MessageReceivedActivity");                
                    // Intent intent = new Intent(this.context, FloresPlugin.MessageReceivedActivity.class);
                    // intent.putExtra("userId", message.userId);
                    // intent.putExtra("deviceId", message.deviceId);
                    // intent.putExtra("message", message.message);
                    // intent.putExtra("sequence", message.sequence);
                    // intent.putExtra("recipientUserId", message.recipientUserId);
                    // intent.putExtra("status", message.status);
                    // intent.putExtra("loggedAt", message.loggedAt.getTime());
                    // intent.putExtra("messageType", message.messageType);
                    // intent.putExtra("sessionId", message.sessionId);
                    // intent.putExtra("step", message.step);
                    // Log.i(TAG, "sending intent by starting activity");
                    // intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
                    // this.context.startActivity(intent);
                    FloresPlugin.onMessageReceived(message);
                    //LocalBroadcastManager.getInstance(this.context).sendBroadcast(intent);
                    Log.i(TAG, "messageReceived intent sent successfully");
                }
            } catch (Exception ex) {
                ex.printStackTrace();
                Log.i(TAG, "messageReceived intent failed");
            }

        } else {
            Log.i(TAG, "existing data" + message);
        }
    }

    public List<P2PSyncInfo> fetchByUserAndDeviceAndSequence(String userId, String deviceId, Long sequence) {
        return db.p2pSyncDao().fetchByUserAndDeviceAndSequence(userId, deviceId, sequence);
    }

    public void persistP2PSyncInfos(String p2pSyncJson) {
        try {
            List<P2PSyncInfo> infos = this.deSerializeP2PSyncInfoFromJson(p2pSyncJson);
            db.beginTransaction();
            try {
                for (P2PSyncInfo info : infos) {
                    this.persistP2PSyncMessage(info);
                }

                // if(!P2PApplication.addOnceMessages) {
                //     Log.i(TAG, "adding few new messages" + P2PApplication.addOnceMessages);
                //     DBSyncManager.getInstance(this.context).addMessage("SUN", "SUNNY", "Chat", "🍻🍷🥂" + "SUN", true, "sessionSSS" + "-SUN");
                //     P2PApplication.addOnceMessages = true;
                // }
                   
                db.setTransactionSuccessful();
            } catch (Exception e) {
                e.printStackTrace();
            } finally {
                db.endTransaction();
            }
        } catch(Exception ex) {
            ex.printStackTrace();
        }
    }



    @Override
    public void addDeviceToSync(String deviceId, boolean syncImmediately) {
        P2PSyncDeviceStatus currentStatus = db.p2pSyncDeviceStatusDao().getDeviceInfo(deviceId);
        P2PSyncDeviceStatus status = currentStatus;
        if (currentStatus == null) {
            // treat as new request
            status = new P2PSyncDeviceStatus(deviceId, syncImmediately);
        } else {
            if (currentStatus.syncTime == null) {
                // not yet sync
                if (currentStatus.syncImmediately == false) {
                    if (syncImmediately == true) {
                        status = new P2PSyncDeviceStatus(deviceId, true);
                    } else {
                        status.syncTime = null;
                    }
                } else if (currentStatus.syncImmediately == true) {
                    status.syncTime = null;
                }
            } else {
                // treat as new request
                status = new P2PSyncDeviceStatus(deviceId, syncImmediately);
            }
        }

        db.p2pSyncDeviceStatusDao().insertP2PSyncDeviceStatus(status);
    }

    @Override
    public void syncCompleted(String deviceId) {
        P2PSyncDeviceStatus status = new P2PSyncDeviceStatus(deviceId, false);
        status.setSyncTime(new Date());
        db.p2pSyncDeviceStatusDao().insertP2PSyncDeviceStatus(status);
        Log.i(TAG, "sync completed with deviceId:" + deviceId);

    }

    @Override
    public List<P2PSyncDeviceStatus> getAllSyncDevices() {
        return Arrays.asList(db.p2pSyncDeviceStatusDao().getAllSyncDevices());
    }

    @Override
    public List<P2PSyncDeviceStatus> getAllNonSyncDevices() {
        return Arrays.asList(db.p2pSyncDeviceStatusDao().getAllNotSyncDevices());
    }

    @Override
    public P2PSyncDeviceStatus getLatestDeviceToSync() {
        P2PSyncDeviceStatus syncImmediatelyRequest = db.p2pSyncDeviceStatusDao().getTopDeviceToSyncImmediately();
        if (syncImmediatelyRequest == null) {
            syncImmediatelyRequest = db.p2pSyncDeviceStatusDao().getTopDeviceToNotSyncImmediately();
        }

        return syncImmediatelyRequest;
    }

    @Override
    public P2PSyncDeviceStatus getLatestDeviceToSyncFromDevices(List<String> items) {
        String deviceIds = StringUtils.join(items, ',');

        P2PSyncDeviceStatus syncImmediatelyRequest = db.p2pSyncDeviceStatusDao().getTopDeviceToSyncImmediately(deviceIds);
        if (syncImmediatelyRequest == null) {
            syncImmediatelyRequest = db.p2pSyncDeviceStatusDao().getTopDeviceToNotSyncImmediately(deviceIds);
        }

        return syncImmediatelyRequest;
    }


    public String serializeHandShakingMessage() {
        try {
            List<HandShakingInfo> handShakingInfos = new ArrayList<HandShakingInfo>();
            P2PLatestInfoByUserAndDevice[] infos = db.p2pSyncDao().getLatestInfoAvailableByUserIdAndDeviceId();
            for (P2PLatestInfoByUserAndDevice info : infos) {
                if (info.userId != null && info.deviceId != null) {
                    handShakingInfos.add(new HandShakingInfo(info.userId, info.deviceId, info.sequence));
                }                
            }

            Gson gson = this.registerHandShakingMessageBuilder();

            HandShakingMessage message = new HandShakingMessage("handshaking", handShakingInfos);
            Type handShakingType = new TypeToken<HandShakingMessage>() {
            }.getType();
            String json = gson.toJson(message, handShakingType);
            return json;
        } catch (Exception e) {
            Log.e(TAG, e.getMessage());
            return null;
        }
    }

    public String serializeProfileMessage(String userId, String deviceId, String contents) {
        try {
            String photoContents = contents;
            Gson gson = this.registerProfileMessageBuilder();
            ProfileMessage message = new ProfileMessage(userId, deviceId, "profileMessage", photoContents);
            Type ProfileMessageType = new TypeToken<ProfileMessage>() {
            }.getType();
            String json = gson.toJson(message, ProfileMessageType);
            return json;
        } catch (Exception e) {
            Log.e(TAG, e.getMessage());
            return null;
        }
    }

    public String buildAllSyncMessages(String handShakeJson) {
        List<HandShakingInfo> infos = deSerializeHandShakingInformationFromJson(handShakeJson);
        if (infos != null) {
            Iterator<HandShakingInfo> itInfos = infos.iterator();
            while (itInfos.hasNext()) {
                HandShakingInfo i = (HandShakingInfo) itInfos.next();
                if (i.getDeviceId() == null || i.getUserId() == null) {
                    itInfos.remove();
                }
            }
        }
        List<P2PSyncInfo> output = this.buildSyncInformation(infos);
        String json = this.convertP2PSyncInfoToJsonUsingStreaming(output);
        Log.i(TAG, "SYNC JSON:" + json);
        return json;
    }

    private List<HandShakingInfo> queryInitialHandShakingMessage() {
        List<HandShakingInfo> handShakingInfos = new ArrayList<HandShakingInfo>();
        P2PLatestInfoByUserAndDevice[] infos = db.p2pSyncDao().getLatestInfoAvailableByUserIdAndDeviceId();
        for (P2PLatestInfoByUserAndDevice info : infos) {
            if (info.userId != null && info.deviceId != null) {
                handShakingInfos.add(new HandShakingInfo(info.userId, info.deviceId, info.sequence));
            }                
        }
        return handShakingInfos;
    }


    private Gson registerHandShakingMessageBuilder() {
        GsonBuilder gsonBuilder = new GsonBuilder();
        gsonBuilder.registerTypeAdapter(HandShakingInfo.class, new HandShakingInfoDeserializer());
        gsonBuilder.registerTypeAdapter(HandShakingMessage.class, new HandShakingMessageDeserializer());
        Gson gson = gsonBuilder.create();
        return gson;
    }

    private Gson registerProfileMessageBuilder() {
        GsonBuilder gsonBuilder = new GsonBuilder();
        gsonBuilder.registerTypeAdapter(ProfileMessage.class, new ProfileMessageDeserializer());
        Gson gson = gsonBuilder.create();
        return gson;
    }

    private Gson registerP2PSyncInfoBuilder() {
        GsonBuilder gsonBuilder = new GsonBuilder();
        gsonBuilder.registerTypeAdapter(P2PSyncInfo.class, new P2PSyncInfoDeserializer());
        Gson gson = gsonBuilder.create();

        return gson;
    }

    public String convertP2PSyncInfoToJsonUsingStreaming(List<P2PSyncInfo> objList)  {
        String json = "";
        try {
            ByteArrayOutputStream baos=new ByteArrayOutputStream();
            OutputStreamWriter outputStreamWriter=new OutputStreamWriter(baos,"UTF-8");
            JsonWriter writer = new JsonWriter(outputStreamWriter);
            writer.setIndent("");
            writer.beginArray();
            Gson gson = this.registerP2PSyncInfoBuilder();
            for (P2PSyncInfo myobj : objList) {
                gson.toJson(myobj, P2PSyncInfo.class, writer);
            }
            writer.endArray();
            writer.close();
            json = baos.toString("UTF-8");
        } catch (Exception e) {
            e.printStackTrace();
        }
        return json;
    }

    public String convertP2PSyncInfoToJson(List<P2PSyncInfo> infos) {
        Type collectionType = new TypeToken<List<P2PSyncInfo>>() {
        }.getType();
        Gson gson = this.registerP2PSyncInfoBuilder();
        String json = gson.toJson(infos, collectionType);
        return json;
    }

    private List<P2PSyncInfo> deSerializeP2PSyncInfoFromJson(String p2pSyncJson) {
        Log.i(TAG, "P2P Sync Info received" + p2pSyncJson);
        Gson gson = this.registerP2PSyncInfoBuilder();
        Type collectionType = new TypeToken<List<P2PSyncInfo>>() {
        }.getType();
        List<P2PSyncInfo> infos = gson.fromJson(p2pSyncJson, collectionType);
        return infos;
    }

    private ProfileMessage deSerializeProfileMessageFromJson(String photoJson) {
        Log.i(TAG, "P2P Photo Message received" + photoJson);
        Gson gson = this.registerProfileMessageBuilder();
        Type ProfileMessageType = new TypeToken<ProfileMessage>() {
        }.getType();
        ProfileMessage message = gson.fromJson(photoJson, ProfileMessageType);
        Log.i(TAG, "got deviceId " + message.getDeviceId());
        Log.i(TAG, "got getMessageType " + message.getMessageType());
        Log.i(TAG, "got getUserId " + message.getUserId());
        Log.i(TAG, "got getData " + message.getData());
        return message;
    }


    public List<HandShakingInfo> deSerializeHandShakingInformationFromJson(String handShakingJson) {
        List result = new ArrayList();
        try {
            Gson gson = this.registerHandShakingMessageBuilder();
            Type handShakingMessageType = new TypeToken<HandShakingMessage>() {
            }.getType();
            HandShakingMessage message = gson.fromJson(handShakingJson, handShakingMessageType);
            if (message != null) {
                result = message.getInfos();
            }

        } catch (Exception e) {
            Log.i(TAG, "deSerializeHandShakingInformationFromJson exception" + e.getMessage());
        }
        return result;
    }


    private List<P2PSyncInfo> buildSyncInformation(final List<HandShakingInfo> otherHandShakeInfos) {
        List<P2PSyncInfo> results = new ArrayList<P2PSyncInfo>();

        try {
            final List<HandShakingInfo> latestInfoFromCurrentDevice = this.queryInitialHandShakingMessage();

            Collections.sort(latestInfoFromCurrentDevice, new Comparator<HandShakingInfo>() {
                @Override
                public int compare(HandShakingInfo o1, HandShakingInfo o2) {
                    if (o1.getUserId() != null && o2.getUserId() != null) {
                        return o1.getUserId().compareTo(o2.getUserId());
                    } else {
                        return -1;
                    }
                }
            });


            Collections.sort(otherHandShakeInfos, new Comparator<HandShakingInfo>() {
                @Override
                public int compare(HandShakingInfo o1, HandShakingInfo o2) {
                    if (o1.getUserId() != null && o2.getUserId() != null) {
                        return o1.getUserId().compareTo(o2.getUserId());
                    } else {
                        return -1;
                    }
                }
            });

            Set<HandShakingInfo> temp = new HashSet<>(latestInfoFromCurrentDevice);
            latestInfoFromCurrentDevice.removeAll(otherHandShakeInfos);
            otherHandShakeInfos.removeAll(temp);
            temp.clear();

            Set<HandShakingInfo> finalSetToProcess = new LinkedHashSet<HandShakingInfo>();
            finalSetToProcess.addAll(latestInfoFromCurrentDevice);
            finalSetToProcess.addAll(otherHandShakeInfos);

            final List finalList = new ArrayList(finalSetToProcess);

            Collections.sort(finalList, new Comparator<HandShakingInfo>() {
                @Override
                public int compare(HandShakingInfo o1, HandShakingInfo o2) {
                    if (o1.getUserId() != null && o2.getUserId() != null) {
                        return (o1.getUserId().compareTo(o2.getUserId()));
                    } else {
                        return -1;
                    }
                }
            });


            @SuppressWarnings("unchecked")
            Map<String, HandShakingInfo> map = new HashMap<String, HandShakingInfo>() {
                {
                    IteratorUtils.forEach(finalList.iterator(), new Closure() {
                        @Override
                        public void execute(Object input) {
                            HandShakingInfo item = (HandShakingInfo) input;
                            String key = item.getUserId() + "_" + item.getDeviceId();
                            if (containsKey(key)) {
                                HandShakingInfo storedItem = get(key);
                                if (storedItem.getSequence() > item.getSequence()) {
                                    storedItem.setStartingSequence(item.getSequence());
                                } else {
                                    storedItem.setStartingSequence(storedItem.getSequence());
                                    storedItem.setSequence(item.getSequence());
                                }
                            } else {
                                put(key, item);
                            }
                        }
                    });
                }
            };


            // process Map (execute queries and get result)

            Collection<HandShakingInfo> collectionValues = map.values();
            for (HandShakingInfo i : collectionValues) {
                P2PSyncInfo[] res = null;
                if (i.getStartingSequence() != null && i.getSequence() != null) {
                    res = db.p2pSyncDao().fetchByUserAndDeviceBetweenSequences(i.getUserId(), i.getDeviceId(), i.getStartingSequence(), i.getSequence());
                } else if (i.getStartingSequence() == null && i.getSequence() != null) {
                    res = db.p2pSyncDao().fetchByUserAndDeviceUpToSequence(i.getUserId(), i.getDeviceId(), i.getSequence());
                }
                if (res != null) {
                    results.addAll(Arrays.asList(res));
                }
            }
        } catch (Exception ex) {
            ex.printStackTrace();
        }

        Iterator itResult = results.iterator();
        while (itResult.hasNext()) {
            P2PSyncInfo temp = (P2PSyncInfo) itResult.next();
            Log.i(TAG, "Sync Info to be send deviceId:" + temp.getDeviceId());
            Log.i(TAG, "Sync Info to be send userId:" + temp.getDeviceId());
            Log.i(TAG, "Sync Info to be send messageType:" + temp.getMessageType());
        }
        return results;
    }    

    public List<P2PUserIdDeviceIdAndMessage> getUsers() {
        return Arrays.asList(db.p2pSyncDao().fetchAllUsers());
    }

    public List<P2PSyncInfo> getInfoByUserId(String userid) {
        return Arrays.asList(db.p2pSyncDao().getSyncInformationByUserId(userid));
    }

    public List<P2PUserIdMessage> fetchLatestMessagesByMessageType(String messageType, List<String> userIds) {
        if (userIds != null && userIds.size() > 0) {
            return db.p2pSyncDao().fetchLatestMessagesByMessageType(messageType, userIds);
        } else {
            return db.p2pSyncDao().fetchLatestMessagesByMessageType(messageType);
        }
    }

    public void addDeviceToSyncAndStartJobIfNotRunning(String recipientId) {
        String deviceId = db.p2pSyncDao().getDeviceForRecipientUserId(recipientId);
        if(deviceId != null) {
            addDeviceToSync(deviceId, true);
        }
        
        // JobUtils.scheduledJob(this.context, true);
    }

    public boolean addMessage(String userId, String recipientId, String messageType, String message) {
        try {
            SharedPreferences pref = this.context.getSharedPreferences(P2P_SHARED_PREF, 0);
            String deviceId = pref.getString("DEVICE_ID", null); // getting String

            Long maxSequence = db.p2pSyncDao().getLatestSequenceAvailableByUserIdAndDeviceId(userId, deviceId);
            if (maxSequence == null) {
                maxSequence = 0L;
            }

            maxSequence++;
            P2PSyncInfo info = new P2PSyncInfo(userId, deviceId, maxSequence, recipientId, message, messageType);
            db.p2pSyncDao().insertP2PSyncInfo(info);
            Log.i(TAG, "inserted data" + info);
            this.addDeviceToSyncAndStartJobIfNotRunning(recipientId);
            return true;
        } catch (Exception e) {
            Log.e(TAG, e.getMessage());
            return false;
        }
    }

    public boolean addMessage(String userId, String recipientId, String messageType, String message, Boolean status, String sessionId) {
        try {
            SharedPreferences pref = this.context.getSharedPreferences(P2P_SHARED_PREF, 0);
            String deviceId = pref.getString("DEVICE_ID", null); // getting String

            Long maxSequence = db.p2pSyncDao().getLatestSequenceAvailableByUserIdAndDeviceId(userId, deviceId);
            if (maxSequence == null) {
                maxSequence = 0L;
            }
            maxSequence++;

            Long step = db.p2pSyncDao().getLatestStepSessionId(sessionId);
            if (step == null) {
                step = 0L;
            }

            step++;

            P2PSyncInfo info = new P2PSyncInfo(userId, deviceId, maxSequence, recipientId, message, messageType);
            info.setSessionId(sessionId);
            info.setStatus(status);
            info.setStep(step);
            db.p2pSyncDao().insertP2PSyncInfo(info);
            Log.i(TAG, "inserted data" + info);
            this.addDeviceToSyncAndStartJobIfNotRunning(recipientId);
            return true;
        } catch (Exception e) {
            Log.e(TAG, e.getMessage());
            return false;
        }
    }

    public List<P2PSyncInfo> getConversations(String firstUserId, String secondUserId, String messageType) {
        return db.p2pSyncDao().fetchConversations(firstUserId, secondUserId, messageType);
    }

    public List<P2PSyncInfo> getLatestConversations(String firstUserId, String secondUserId, String messageType) {
        return db.p2pSyncDao().fetchLatestConversations(firstUserId, secondUserId, messageType);
    }

    public List<P2PSyncInfo> getLatestConversations(String firstUserId, String messageType) {
        return db.p2pSyncDao().fetchLatestConversations(firstUserId, messageType);
    }

    public String readProfilePhoto() {
        SharedPreferences pref = this.context.getSharedPreferences(P2P_SHARED_PREF, 0);
        String userId = pref.getString("USER_ID", null); // getting String
        return P2PSyncManager.generateUserPhotoFileName(userId);
    }

    public boolean upsertProfile() {
        try {
            SharedPreferences pref = this.context.getSharedPreferences(P2P_SHARED_PREF, 0);
            String fileName = pref.getString("PROFILE_PHOTO", null); // getting String
            String userId = pref.getString("USER_ID", null); // getting String
            String deviceId = pref.getString("DEVICE_ID", null); // getting String

            return this.upsertProfileForUserIdAndDevice(userId, deviceId, fileName);

        } catch (Exception e) {
            Log.e(TAG, e.getMessage());
            return false;
        }
    }

    public boolean upsertProfileForUserIdAndDevice(String userId, String deviceId, String message) {
        try {
            P2PSyncInfo userInfo = db.p2pSyncDao().getProfileByUserId(userId, DBSyncManager.MessageTypes.PHOTO.type());
            if (userInfo != null) {
                userInfo.setUserId(userId);
                userInfo.setDeviceId(deviceId);
                userInfo.setMessage(message);
                userInfo.setMessageType(DBSyncManager.MessageTypes.PHOTO.type());
            } else {
                userInfo = new P2PSyncInfo();
                userInfo.setUserId(userId);
                userInfo.setDeviceId(deviceId);

                Long maxSequence = db.p2pSyncDao().getLatestSequenceAvailableByUserIdAndDeviceId(userId, deviceId);
                if (maxSequence == null) {
                    maxSequence = 0L;
                }

                maxSequence++;
                userInfo.setSequence(maxSequence);
                userInfo.setMessage(message);
                userInfo.setMessageType(DBSyncManager.MessageTypes.PHOTO.type());
            }
            db.p2pSyncDao().insertP2PSyncInfo(userInfo);
            return true;

        } catch (Exception e) {
            Log.e(TAG, e.getMessage());
            return false;
        }
    }

    @Override
    public List<P2PSyncInfo> getLatestConversationsByUser(String firstUserId) {
        return db.p2pSyncDao().fetchLatestConversationsByUser(firstUserId);
    }
}


class P2PSyncInfoDeserializer implements JsonDeserializer<P2PSyncInfo> {
    @Override
    public P2PSyncInfo deserialize(final JsonElement json, final Type typeOfT, final JsonDeserializationContext context)
            throws JsonParseException {

        final JsonObject jsonObject = json.getAsJsonObject();

        final JsonElement jsonUserId = jsonObject.get("userId");
        final String userId = jsonUserId.getAsString();

        final JsonElement jsonDeviceId = jsonObject.get("deviceId");
        final String deviceId = jsonDeviceId.getAsString();

        final JsonElement jsonSequence = jsonObject.get("sequence");
        final Long sequence = jsonSequence.getAsLong();

        final JsonElement jsonMessageType = jsonObject.get("messageType");
        final String messageType = jsonMessageType.getAsString();

        String recipientUserId = null;
        final JsonElement jsonRecipientType = jsonObject.get("recipientUserId");
        if (jsonRecipientType != null) {
            recipientUserId = jsonRecipientType.getAsString();
        }

        String message = null;
        final JsonElement jsonMessage = jsonObject.get("message");
        if (jsonMessage != null) {
            message = jsonMessage.getAsString();
        }
        final String receivedMessage = message == null ? "" : message;

        String sessionId = null;
        final JsonElement jsonSessionId = jsonObject.get("sessionId");
        if (jsonSessionId != null) {
            sessionId = jsonSessionId.getAsString();
        }

        Boolean status = null;
        final JsonElement jsonStatus = jsonObject.get("status");
        if (jsonStatus != null) {
            status = jsonStatus.getAsBoolean();
        }

        Long step = null;
        final JsonElement jsonStep = jsonObject.get("step");
        if (jsonStep != null) {
            step = jsonStep.getAsLong();
        }


        final P2PSyncInfo p2PSyncInfo = new P2PSyncInfo(userId, deviceId, sequence, recipientUserId, receivedMessage, messageType);
        p2PSyncInfo.setSessionId(sessionId);
        p2PSyncInfo.setStatus(status);
        p2PSyncInfo.setStep(step);

        return p2PSyncInfo;
    }
}