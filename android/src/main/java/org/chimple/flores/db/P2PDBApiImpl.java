package org.chimple.flores.db;

import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.support.v4.content.LocalBroadcastManager;
import android.util.Log;
import android.os.Environment;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonDeserializationContext;
import com.google.gson.JsonDeserializer;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import com.google.gson.reflect.TypeToken;
import android.os.Environment;
import static android.os.Environment.DIRECTORY_DOWNLOADS;
import org.apache.commons.io.FilenameUtils;
import java.io.FilenameFilter;
import org.apache.commons.io.FileUtils;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import org.apache.commons.collections4.Closure;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.collections4.IteratorUtils;
import org.apache.commons.collections4.TransformerUtils;
import org.apache.commons.lang3.StringUtils;

import java.lang.reflect.Type;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
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

import org.chimple.flores.db.entity.P2PUserIdDeviceId;
import org.chimple.flores.application.P2PContext;
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
import org.chimple.flores.db.entity.SyncInfoItem;
import org.chimple.flores.db.entity.SyncInfoMessage;
import org.chimple.flores.db.entity.SyncInfoMessageDeserializer;
import org.chimple.flores.db.entity.SyncInfoRequestMessage;
import org.chimple.flores.db.entity.SyncItemDeserializer;
import org.chimple.flores.db.entity.SyncRequestMessageDeserializer;
import org.chimple.flores.multicast.MulticastManager;
import org.chimple.flores.manager.BluetoothManager;
import org.chimple.flores.db.entity.BtAddress;

import static org.chimple.flores.application.P2PContext.CONSOLE_TYPE;
import static org.chimple.flores.application.P2PContext.LOG_TYPE;
import static org.chimple.flores.application.P2PContext.NEW_MESSAGE_ADDED;
import static org.chimple.flores.application.P2PContext.SHARED_PREF;
import static org.chimple.flores.application.P2PContext.newMessageAddedOnDevice;
import static org.chimple.flores.db.AppDatabase.SYNC_NUMBER_OF_LAST_MESSAGES;
import static org.chimple.flores.db.AppDatabase.PURGE_MESSAGE_LIMIT;


import org.chimple.flores.FloresPlugin;

public class P2PDBApiImpl {
    private static final String TAG = P2PDBApiImpl.class.getName();
    private AppDatabase db;
    private Context context;
    private static P2PDBApiImpl p2pDBApiInstance;
    private static MulticastManager manager;
    private static BluetoothManager bluetoothManager;

    public static P2PDBApiImpl getInstance(Context context) {
        synchronized (P2PDBApiImpl.class) {
            if (p2pDBApiInstance == null) {
                p2pDBApiInstance = new P2PDBApiImpl(AppDatabase.getInstance(context), MulticastManager.getInstance(context), BluetoothManager.getInstance(context), context);
            }
            return p2pDBApiInstance;
        }
    }


    private P2PDBApiImpl(AppDatabase db, MulticastManager manager, BluetoothManager bluetoothManager, Context context) {
        this.db = db;
        this.context = context;
        this.manager = manager;
        this.bluetoothManager = bluetoothManager;
    }

    public void persistMessage(String userId, String deviceId, String recepientUserId, String message, String messageType, Date createDate) {
        Long maxSequence = db.p2pSyncDao().getLatestSequenceAvailableByUserIdAndDeviceId(userId, deviceId);
        if (maxSequence == null) {
            maxSequence = 0L;
        }

        maxSequence++;
        P2PSyncInfo info = new P2PSyncInfo(userId, deviceId, maxSequence, recepientUserId, message, messageType, createDate);
        this.persistP2PSyncMessage(info);
        Log.i(TAG, "inserted data" + info);
    }

    public String persistP2PSyncMessage(P2PSyncInfo message) {
        if(message == null) {
            return null;
        }
        Log.i(TAG, "got Sync userId:" + message.userId);
        Log.i(TAG, "got Sync deviceId:" + message.deviceId);
        Log.i(TAG, "got Sync sequence:" + message.sequence);
        Log.i(TAG, "got Sync message:" + message.message);
        P2PSyncInfo found = db.p2pSyncDao().fetchByUserAndDeviceAndSequence(message.getUserId(), message.getDeviceId(), message.sequence);
        if(found != null) {
            message.id = found.id;
        }
        db.p2pSyncDao().insertP2PSyncInfo(message);
        Log.i(TAG, "inserted data" + message);
        manager.getAllSyncInfosReceived().add(message.getDeviceId() + "_" + message.getUserId() + "_" + Long.valueOf(message.getSequence().longValue()));
        bluetoothManager.getAllSyncInfosReceived().add(message.getDeviceId() + "_" + message.getUserId() + "_" + Long.valueOf(message.getSequence().longValue()));
        manager.notifyUI(message.message, message.getSender(), CONSOLE_TYPE);

        SharedPreferences pref = this.context.getSharedPreferences(SHARED_PREF, 0);
        String userId = pref.getString("USER_ID", null); // getting String
        try {
            // one more recipient id 0
            if ((message.recipientUserId != null 
                && (message.getRecipientUserId().equals("0") 
                || userId.equals(message.getRecipientUserId()))) 
                || message.messageType.equals("Photo")) {
                // Log.i(TAG, "messageReceived intent constructing for user" + userId);
                Log.d(TAG, "messageReceived intent constructing for user" + userId + " and type:" + message.messageType + " with content:" + message.message);
                FloresPlugin.onMessageReceived(message);
                //LocalBroadcastManager.getInstance(this.context).sendBroadcast(intent);
                Log.i(TAG, "messageReceived intent sent successfully");
            }
        } catch (Exception ex) {
            ex.printStackTrace();
            Log.i(TAG, "messageReceived intent failed");
        }

        return message.message;
    }

    public String persistOutOfSyncP2PSyncMessage(P2PSyncInfo message) {
        if(message == null) {
         return null;
        } else {
            try {            
                Log.i(TAG, "got Sync userId:" + message.userId);
                Log.i(TAG, "got Sync deviceId:" + message.deviceId);
                Log.i(TAG, "got Sync sequence:" + message.sequence);
                Log.i(TAG, "got Sync message:" + message.message);
                Long lastValidSequence = db.p2pSyncDao().fetchMinValidSequenceByUserAndDevice(message.getUserId(), message.getDeviceId(), message.sequence);            
                if(lastValidSequence != null) {
                    Log.i(TAG, "in persistOutOfSyncP2PSyncMessage --> got last valid sequence:" + lastValidSequence.longValue());                 
                    for (int i = lastValidSequence.intValue() + 1; i < message.sequence; i++) {
                        Long existingId = db.p2pSyncDao().findId(message.userId, message.deviceId, message.sequence);
                        if (existingId == null) {
                            P2PSyncInfo missingP2P = new P2PSyncInfo(message.userId, message.deviceId, new Long(i), message.recipientUserId, null, DBSyncManager.MessageTypes.MISSING.type(), message.getCreatedAt());
                            Log.i(TAG, "in persistOutOfSyncP2PSyncMessage --> inserted missing message userId:" + message.userId + " deviceId:" + message.deviceId + "sequence:" + i + "messageType:" + DBSyncManager.MessageTypes.MISSING.type());
                            db.p2pSyncDao().insertP2PSyncInfo(missingP2P);
                            manager.notifyUI(message.message + "inserted ----> missing message with sequence:" + i, message.getSender(), LOG_TYPE);
                        }
                    }        
                } else {
                    for (int i = 1; i < message.sequence; i++) {
                        P2PSyncInfo missingP2P = new P2PSyncInfo(message.userId, message.deviceId, new Long(i), message.recipientUserId, null, DBSyncManager.MessageTypes.MISSING.type(), message.getCreatedAt());
                        Log.i(TAG, "inserted out of sync message" + missingP2P.toString());
                        Log.i(TAG, "in persistOutOfSyncP2PSyncMessage --> inserted out of sync message userId:" + message.userId + " deviceId:" + message.deviceId + "sequence:" + i + "messageType:" + DBSyncManager.MessageTypes.MISSING.type());
                        Long existingId = db.p2pSyncDao().findId(message.userId, message.deviceId, message.sequence);
                        missingP2P.id = existingId;
                        db.p2pSyncDao().insertP2PSyncInfo(missingP2P);
                        manager.notifyUI(message.message + "inserted ----> missing message with sequence:" + i, message.getSender(), LOG_TYPE);
                    }
                }
                
                P2PSyncInfo found = db.p2pSyncDao().fetchByUserAndDeviceAndSequence(message.getUserId(), message.getDeviceId(), message.sequence);
                if (found != null) {
                    message.id = found.id;
                }

                db.p2pSyncDao().insertP2PSyncInfo(message);
                Log.i(TAG, "inserted data" + message);
                manager.getAllSyncInfosReceived().add(message.getDeviceId() + "_" + message.getUserId() + "_" + Long.valueOf(message.getSequence().longValue()));
                bluetoothManager.getAllSyncInfosReceived().add(message.getDeviceId() + "_" + message.getUserId() + "_" + Long.valueOf(message.getSequence().longValue()));
                manager.notifyUI(message.message + "inserted ----> out of sync with sequence:" + message.getSequence(), message.getSender(), CONSOLE_TYPE);
                SharedPreferences pref = this.context.getSharedPreferences(SHARED_PREF, 0);
                String userId = pref.getString("USER_ID", null); // getting String
                try {
                    if ((message.recipientUserId != null 
                    && (message.getRecipientUserId().equals("0") 
                    || userId.equals(message.getRecipientUserId()))) 
                    || message.messageType.equals("Photo")) {
                       Log.d(TAG, "messageReceived intent constructing for user" + userId + " and type:" + message.messageType + " with content:" + message.message);                        
                       FloresPlugin.onMessageReceived(message);
                       // this.appendLog("messageReceived intent constructing for user" + userId + " and type:" + message.messageType + " with content:" + message.message);
                        //LocalBroadcastManager.getInstance(this.context).sendBroadcast(intent);
                        Log.i(TAG, "messageReceived intent sent successfully");
                    }
                } catch (Exception ex) {
                    ex.printStackTrace();
                    Log.i(TAG, "messageReceived intent failed");
                }                        
            } catch (Exception e) {
                e.printStackTrace();
            }           
            return message.message;
        }    
    }

    public void appendLog(String text)
    {
        try {
            String path = Environment.getExternalStorageDirectory().getPath() + "/" + "log.file";
            File logFile = new File(path);
            
            if (!logFile.exists())
            {
                try
                {
                    logFile.createNewFile();
                }
                catch (IOException e)
                {
                    // TODO Auto-generated catch block
                    e.printStackTrace();
                }
            }
            try
            {
                //BufferedWriter for performance, true to set append to file flag
                BufferedWriter buf = new BufferedWriter(new FileWriter(logFile, true));
                buf.append(text);
                buf.newLine();
                buf.close();
            }
            catch (IOException e)
            {
                // TODO Auto-generated catch block
                e.printStackTrace();
            }
        } catch (Exception e) {
            e.printStackTrace();
        }    
    }

    public P2PSyncInfo fetchByUserAndDeviceAndSequence(String userId, String deviceId, Long sequence) {
        return db.p2pSyncDao().fetchByUserAndDeviceAndSequence(userId, deviceId, sequence);
    }

    public String persistP2PSyncInfos(String p2pSyncJson) {
        String result = "";
        try {
            List<P2PSyncInfo> infos = this.deSerializeP2PSyncInfoFromJson(p2pSyncJson);
            db.beginTransaction();
            try {
                for (P2PSyncInfo info : infos) {
                    result = this.persistP2PSyncMessage(info);
                }

                db.setTransactionSuccessful();
            } catch (Exception e) {
                e.printStackTrace();
            } finally {
                db.endTransaction();
            }
        } catch (Exception ex) {
            ex.printStackTrace();
        }
        return result;
    }

    public String persistP2PSyncInfo(P2PSyncInfo info) {
        String result = "";
        try {
            db.beginTransaction();
            try {
                result = this.persistP2PSyncMessage(info);
                db.setTransactionSuccessful();
            } catch (Exception e) {
                e.printStackTrace();
            } finally {
                db.endTransaction();
            }
        } catch (Exception ex) {
            ex.printStackTrace();
        }
        return result;
    }

    public String persistOutOfSyncP2PSyncInfo(P2PSyncInfo info) {
        String result = "";
        try {
            db.beginTransaction();
            try {
                result = this.persistOutOfSyncP2PSyncMessage(info);
                db.setTransactionSuccessful();
            } catch (Exception e) {
                e.printStackTrace();
            } finally {
                db.endTransaction();
            }
        } catch (Exception ex) {
            ex.printStackTrace();
        }
        return result;
    }


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


    public void syncCompleted(String deviceId) {
        P2PSyncDeviceStatus status = new P2PSyncDeviceStatus(deviceId, false);
        status.setSyncTime(new Date());
        db.p2pSyncDeviceStatusDao().insertP2PSyncDeviceStatus(status);
        Log.i(TAG, "sync completed with deviceId:" + deviceId);

    }


    public List<P2PSyncDeviceStatus> getAllSyncDevices() {
        return Arrays.asList(db.p2pSyncDeviceStatusDao().getAllSyncDevices());
    }


    public List<P2PSyncDeviceStatus> getAllNonSyncDevices() {
        return Arrays.asList(db.p2pSyncDeviceStatusDao().getAllNotSyncDevices());
    }


    public P2PSyncDeviceStatus getLatestDeviceToSync() {
        P2PSyncDeviceStatus syncImmediatelyRequest = db.p2pSyncDeviceStatusDao().getTopDeviceToSyncImmediately();
        if (syncImmediatelyRequest == null) {
            syncImmediatelyRequest = db.p2pSyncDeviceStatusDao().getTopDeviceToNotSyncImmediately();
        }

        return syncImmediatelyRequest;
    }


    public P2PSyncDeviceStatus getLatestDeviceToSyncFromDevices(List<String> items) {
        String deviceIds = StringUtils.join(items, ',');

        P2PSyncDeviceStatus syncImmediatelyRequest = db.p2pSyncDeviceStatusDao().getTopDeviceToSyncImmediately(deviceIds);
        if (syncImmediatelyRequest == null) {
            syncImmediatelyRequest = db.p2pSyncDeviceStatusDao().getTopDeviceToNotSyncImmediately(deviceIds);
        }

        return syncImmediatelyRequest;
    }

    public List<SyncInfoRequestMessage> buildSyncInfoRequestMessages(Collection<HandShakingInfo> infos) {
        Map<String, List<SyncInfoItem>> items = new HashMap<String, List<SyncInfoItem>>();
        Iterator<HandShakingInfo> it = infos.iterator();
        while (it.hasNext()) {
            HandShakingInfo info = it.next();
            String key = info.getFrom();
            if (items.containsKey(key)) {
                List<SyncInfoItem> syncItems = items.get(key);
                syncItems.add(new SyncInfoItem(info.getUserId(), info.getDeviceId(), info.getStartingSequence() == null ? 1L : info.getStartingSequence(), info.getSequence()));
                items.put(key, syncItems);
            } else {
                List<SyncInfoItem> syncItems = new ArrayList<SyncInfoItem>();
                syncItems.add(new SyncInfoItem(info.getUserId(), info.getDeviceId(), info.getStartingSequence() == null ? 1L : info.getStartingSequence(), info.getSequence()));
                items.put(info.getFrom(), syncItems);
            }
        }


        List<SyncInfoRequestMessage> syncInfoRequestMessages = new ArrayList<SyncInfoRequestMessage>();

        Iterator<String> keys = items.keySet().iterator();
        while (keys.hasNext()) {
            String mDeviceId = keys.next();
            List<SyncInfoItem> syncItems = items.get(mDeviceId);
            Log.d(TAG, "buildSyncInfoRequestMessages mDeviceId --->" + mDeviceId);
            if (mDeviceId == null || mDeviceId.isEmpty()) {
                Log.d(TAG, "build SyncInfoRequestMessage setting mDeviceID as it was Not Set ----->" + P2PContext.getCurrentDevice());
            }
            Log.d(TAG, "build SyncInfoRequestMessage ----->" + P2PContext.getCurrentDevice());
            Log.d(TAG, "build SyncInfoRequestMessage mDeviceId ----->" + mDeviceId);
            SyncInfoRequestMessage m = new SyncInfoRequestMessage(P2PContext.getCurrentDevice(), mDeviceId, syncItems);
            syncInfoRequestMessages.add(m);
        }

        return syncInfoRequestMessages;
    }

    public Map<String, HandShakingInfo> handShakingInformationFromCurrentDevice() {
        Map<String, HandShakingInfo> handShakingInfos = new HashMap<String, HandShakingInfo>();
        try {
            P2PLatestInfoByUserAndDevice[] infos = db.p2pSyncDao().getLatestInfoAvailableByUserIdAndDeviceId();
            for (P2PLatestInfoByUserAndDevice info : infos) {
                String tDeviceId = info.getDeviceId();
                String tUserId = info.getUserId();
                Long latestUserProfileId = db.p2pSyncDao().findLatestProfilePhotoId(tUserId, tDeviceId);
                P2PLatestInfoByUserAndDevice[] missingRecords = db.p2pSyncDao().getMissingMessagesByUserIdAndDeviceId(tUserId, tDeviceId);
                List<P2PLatestInfoByUserAndDevice> missingRecordsList = Arrays.asList(missingRecords);
                Collection missingSequences = CollectionUtils.collect(missingRecordsList, TransformerUtils.invokerTransformer("getSequence"));
                String missingRecordsStr = StringUtils.join(missingSequences, ",");
                if (info.userId != null && info.deviceId != null) {
                    HandShakingInfo i = new HandShakingInfo(info.userId, info.deviceId, info.sequence, missingRecordsStr, latestUserProfileId);
                    i.setFrom(P2PContext.getCurrentDevice());
                    Log.d(TAG, "handShakingInformationFromCurrentDevice: " + info.userId + " " + info.deviceId + " " + info.sequence);
                    handShakingInfos.put(info.userId, i);
                }

            }
        } catch (Exception e) {
            Log.e(TAG, e.getMessage());
        }
        return handShakingInfos;
    }

    public String serializeHandShakingMessage(boolean needAcknowlegement) {
        try {
            List<HandShakingInfo> handShakingInfos = new ArrayList<HandShakingInfo>();
            P2PLatestInfoByUserAndDevice[] infos = db.p2pSyncDao().getLatestInfoAvailableByUserIdAndDeviceId();
            for (P2PLatestInfoByUserAndDevice info : infos) {
                if (info.userId != null && info.deviceId != null) {
                    Log.d(TAG, "checking for user:" + info.userId + " fetchByUserAndDeviceBetweenSequencesand device:" + info.deviceId + " and sequence:" + info.sequence);
                    Long latestUserProfileId = db.p2pSyncDao().findLatestProfilePhotoId(info.userId, info.deviceId);
                    P2PLatestInfoByUserAndDevice[] missingRecords = db.p2pSyncDao().getMissingMessagesByUserIdAndDeviceId(info.userId, info.deviceId);
                    String missingRecordsStr = null;
                    if (missingRecords.length > 0) {
                        long startingSequence = info.sequence.longValue() > SYNC_NUMBER_OF_LAST_MESSAGES ? info.sequence.longValue() - SYNC_NUMBER_OF_LAST_MESSAGES + 1 : 1;
                        Log.d(TAG, "startingSequence in serializeHandShakingMessage ---> " + startingSequence);
                        P2PSyncInfo[] rs = db.p2pSyncDao().fetchByUserAndDeviceBetweenSequences(info.userId, info.deviceId, startingSequence, info.sequence.longValue());
                        StringBuilder missingRecordsBuffer = new StringBuilder();
                        for (P2PSyncInfo p : rs) {
                            if (p.getMessageType().equals(DBSyncManager.MessageTypes.MISSING.type())) {
                                missingRecordsBuffer.append("0");
                            } else {
                                missingRecordsBuffer.append("1");
                            }
                        }
                        missingRecordsStr = missingRecordsBuffer.toString();
                    }

                    Log.d(TAG, "missingRecordsStr:" + missingRecordsStr);
                    handShakingInfos.add(new HandShakingInfo(info.userId, info.deviceId, info.sequence, missingRecordsStr, latestUserProfileId));
                }
            }

            Gson gson = this.registerHandShakingMessageBuilder();
            String reply = needAcknowlegement ? "true" : "false";
            String bluetoothAddress = db.btInfoDao().getBluetoothAddress(P2PContext.getCurrentDevice());
            Log.d(TAG, "BT ADDRESS:" + bluetoothAddress);
            if(bluetoothAddress == null) {
                bluetoothAddress = bluetoothManager.getBluetoothMacAddress();
                Log.d(TAG, "BT ADDRESS:" + bluetoothAddress);
                if(bluetoothAddress != null) {
                    this.saveBtAddress(P2PContext.getCurrentDevice(), bluetoothAddress);    
                }                
            }
            HandShakingMessage message = new HandShakingMessage(P2PContext.getCurrentDevice(), "handshaking", reply, handShakingInfos, bluetoothAddress);
            Type handShakingType = new TypeToken<HandShakingMessage>() {
            }.getType();
            String json = gson.toJson(message, handShakingType);
            return json;
        } catch (Exception e) {
            Log.e(TAG, e.getMessage());
            return null;
        }        
    }

    public SyncInfoRequestMessage buildSyncRequstMessage(String json) {
        SyncInfoRequestMessage message = deSerializeSyncRequestMessagesFromJson(json);
        return message;
    }

    private SyncInfoRequestMessage deSerializeSyncRequestMessagesFromJson(String json) {
        SyncInfoRequestMessage message = null;
        try {
            Gson gson = this.registerSyncRequestMessageBuilder();
            Type SyncInfoRequestMessageType = new TypeToken<SyncInfoRequestMessage>() {
            }.getType();
            message = gson.fromJson(json, SyncInfoRequestMessageType);

        } catch (Exception e) {
            Log.i(TAG, "deSerializeHandShakingInformationFromJson exception" + e.getMessage());
        }
        return message;
    }


    public List<String> serializeSyncRequestMessages(Collection<HandShakingInfo> infos) {
        List<String> results = new ArrayList<String>();
        try {
            Gson gson = this.registerSyncRequestMessageBuilder();
            Type requestType = new TypeToken<SyncInfoRequestMessage>() {
            }.getType();


            List<SyncInfoRequestMessage> messages = this.buildSyncInfoRequestMessages(infos);
            Iterator<SyncInfoRequestMessage> mIt = messages.iterator();
            while (mIt.hasNext()) {
                SyncInfoRequestMessage requestMessage = mIt.next();
                String json = gson.toJson(requestMessage, requestType);
                results.add(json);
            }
        } catch (Exception e) {
            Log.e(TAG, e.getMessage());
        }
        return results;
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
        String json = null;
        HandShakingMessage message = deSerializeHandShakingInformationFromJson(handShakeJson);
        if (message != null) {
            List<HandShakingInfo> infos = message.getInfos();
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
            json = this.convertP2PSyncInfoToJsonUsingStreaming(output);
            Log.i(TAG, "SYNC JSON:" + json);
        }

        return json;
    }

    public Long findLatestProfilePhotoId(String userId, String deviceId) {
        return db.p2pSyncDao().findLatestProfilePhotoId(userId, deviceId);
    }    


    private List<HandShakingInfo> queryInitialHandShakingMessage() {
        List<HandShakingInfo> handShakingInfos = new ArrayList<HandShakingInfo>();
        P2PLatestInfoByUserAndDevice[] infos = db.p2pSyncDao().getLatestInfoAvailableByUserIdAndDeviceId();
        for (P2PLatestInfoByUserAndDevice info : infos) {
            if (info.userId != null && info.deviceId != null) {
                Long latestUserProfileId = db.p2pSyncDao().findLatestProfilePhotoId(info.userId, info.deviceId);
                P2PLatestInfoByUserAndDevice[] missingRecords = db.p2pSyncDao().getMissingMessagesByUserIdAndDeviceId(info.userId, info.deviceId);
                List<P2PLatestInfoByUserAndDevice> missingRecordsList = Arrays.asList(missingRecords);
                Collection missingSequences = CollectionUtils.collect(missingRecordsList, TransformerUtils.invokerTransformer("getSequence"));
                String missingRecordsStr = StringUtils.join(missingSequences, ",");
                handShakingInfos.add(new HandShakingInfo(info.userId, info.deviceId, info.sequence, missingRecordsStr, latestUserProfileId));
            }
        }
        return handShakingInfos;
    }

    private Gson registerSyncRequestMessageBuilder() {
        GsonBuilder gsonBuilder = new GsonBuilder();
        gsonBuilder.registerTypeAdapter(SyncInfoItem.class, new SyncItemDeserializer());
        gsonBuilder.registerTypeAdapter(SyncInfoRequestMessage.class, new SyncRequestMessageDeserializer());
        Gson gson = gsonBuilder.create();
        return gson;
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
        gsonBuilder.setDateFormat("yyyy-MM-dd HH:mm:ss");
        gsonBuilder.registerTypeAdapter(P2PSyncInfo.class, new P2PSyncInfoDeserializer());
        gsonBuilder.registerTypeAdapter(SyncInfoMessage.class, new SyncInfoMessageDeserializer());
        Gson gson = gsonBuilder.create();

        return gson;
    }

    public String convertSingleP2PSyncInfoToJsonUsingStreaming(P2PSyncInfo syncInfo) {

        try {
            List<P2PSyncInfo> p2PSyncInfos = new ArrayList<P2PSyncInfo>();
            p2PSyncInfos.add(syncInfo);

            Gson gson = this.registerP2PSyncInfoBuilder();
            SyncInfoMessage message = new SyncInfoMessage("syncInfoMessage", syncInfo.getSender(), p2PSyncInfos);
            Type syncInfoMessageType = new TypeToken<SyncInfoMessage>() {
            }.getType();
            String json = gson.toJson(message, syncInfoMessageType);
            Log.d(TAG, "convertSingleP2PSyncInfoToJsonUsingStreaming: " + json);
            return json;
        } catch (Exception e) {
            Log.e(TAG, e.getMessage());
            return "";
        }
    }

    /* not longer used sender should be real sydner in p2pSyncInfos*/
    public String convertP2PSyncInfoToJsonUsingStreaming(List<P2PSyncInfo> p2PSyncInfos) {
        String json = "";
        try {
            Gson gson = this.registerP2PSyncInfoBuilder();
            SyncInfoMessage message = new SyncInfoMessage("syncInfoMessage", P2PContext.getCurrentDevice(), p2PSyncInfos);
            Type syncInfoMessageType = new TypeToken<SyncInfoMessage>() {
            }.getType();
            json = gson.toJson(message, syncInfoMessageType);
            Log.d(TAG, "convertSingleP2PSyncInfoToJsonUsingStreaming: " + json);
        } catch (Exception e) {
            Log.e(TAG, e.getMessage());
        }
        return json;
    }

    public List<P2PSyncInfo> deSerializeP2PSyncInfoFromJson(String p2pSyncJson) {
        Log.i(TAG, "P2P Sync Info received" + p2pSyncJson);
        Gson gson = this.registerP2PSyncInfoBuilder();
        List<P2PSyncInfo> infos = null;
        Type SyncInfoMessageType = new TypeToken<SyncInfoMessage>() {
        }.getType();
        SyncInfoMessage message = gson.fromJson(p2pSyncJson, SyncInfoMessageType);
        if (message != null) {
            infos = message.getInfos();
            for (P2PSyncInfo s : infos) {
                s.setSender(message.getSender());
            }
        }

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


    public List<String> fetchP2PSyncInfoBySyncRequest(SyncInfoItem i) {
        List<String> jsons = new ArrayList<String>();
        List<P2PSyncInfo> results = Arrays.asList(db.p2pSyncDao().fetchByUserAndDeviceBetweenSequences(i.getUserId(), i.getDeviceId(), i.getStartingSequence(), i.getSequence()));
        for (P2PSyncInfo p : results) {
            jsons.add(convertSingleP2PSyncInfoToJsonUsingStreaming(p));
        }

        return jsons;
    }

    public HandShakingMessage deSerializeHandShakingInformationFromJson(String handShakingJson) {
        HandShakingMessage message = null;
        try {
            Gson gson = this.registerHandShakingMessageBuilder();
            Type handShakingMessageType = new TypeToken<HandShakingMessage>() {
            }.getType();
            message = gson.fromJson(handShakingJson, handShakingMessageType);

        } catch (Exception e) {
            Log.i(TAG, "deSerializeHandShakingInformationFromJson exception" + e.getMessage());
        }
        return message;
    }


    public List<P2PSyncInfo> buildSyncInformation(final List<HandShakingInfo> otherHandShakeInfos) {
        List<P2PSyncInfo> results = new ArrayList<P2PSyncInfo>();

        try {
            final List<HandShakingInfo> latestInfoFromCurrentDevice = this.queryInitialHandShakingMessage();

            Collections.sort(latestInfoFromCurrentDevice, new Comparator<HandShakingInfo>() {

                public int compare(HandShakingInfo o1, HandShakingInfo o2) {
                    if (o1.getUserId() != null && o2.getUserId() != null) {
                        return o1.getUserId().compareTo(o2.getUserId());
                    } else {
                        return -1;
                    }
                }
            });


            Collections.sort(otherHandShakeInfos, new Comparator<HandShakingInfo>() {

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

    public List<P2PSyncInfo> refreshAllMessages() {
        return Arrays.asList(db.p2pSyncDao().refreshAllMessages());
    }

    public List<P2PSyncInfo> getSyncInformationByUserIdAndDeviceId(String userId, String deviceId) {
        return Arrays.asList(db.p2pSyncDao().getSyncInformationByUserIdAndDeviceId(userId, deviceId));
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
        if (deviceId != null) {
            addDeviceToSync(deviceId, true);
        }

    }

    public boolean addMessage(String userId, String recipientId, String messageType, String message) {
        try {
            SharedPreferences pref = this.context.getSharedPreferences(SHARED_PREF, 0);
            String deviceId = pref.getString("DEVICE_ID", null); // getting String

            Long maxSequence = db.p2pSyncDao().getLatestSequenceAvailableByUserIdAndDeviceId(userId, deviceId);
            if (maxSequence == null) {
                maxSequence = 0L;
            }

            maxSequence++;
            P2PSyncInfo info = new P2PSyncInfo(userId, deviceId, maxSequence, recipientId, message, messageType, new Date());
            db.p2pSyncDao().insertP2PSyncInfo(info);
            Log.i(TAG, "inserted data" + info);
            broadcastNewMessageAdded(info);
            return true;
        } catch (Exception e) {
            Log.e(TAG, e.getMessage());
            return false;
        }
    }

    public boolean deleteDataPerDeviceId(String deviceId) {
        try {
            db.p2pSyncDao().deletePerDeviceID(deviceId);
            Log.i(TAG, "deleted data" + deviceId);
            return true;
        } catch (Exception e) {
            Log.e(TAG, e.getMessage());
            return false;
        }
    }

    // for testing only
    public boolean addMessage(String userId, String deviceId, Long sequence, String recipientId, String messageType, String message) {
        try {
            P2PSyncInfo info = new P2PSyncInfo(userId, deviceId, sequence, recipientId, message, messageType, new Date());
            db.p2pSyncDao().insertP2PSyncInfo(info);
            Log.i(TAG, "inserted data" + info);
            broadcastNewMessageAdded(info);
            return true;
        } catch (Exception e) {
            Log.e(TAG, e.getMessage());
            return false;
        }
    }

    private void broadcastNewMessageAdded(P2PSyncInfo info) {
        Log.d(TAG, "broadcastNewMessageAdded ----> " + info.getMessage());
        Intent intent = new Intent(newMessageAddedOnDevice);
        intent.putExtra(NEW_MESSAGE_ADDED, info);
        LocalBroadcastManager.getInstance(context).sendBroadcast(intent);

        long purgeLimit = PURGE_MESSAGE_LIMIT + SYNC_NUMBER_OF_LAST_MESSAGES;
        List<P2PUserIdDeviceId> purgeSenders = db.p2pSyncDao().findSenderToPurge(purgeLimit);
        for (P2PUserIdDeviceId s : purgeSenders) {
            Long latestUserProfileId = db.p2pSyncDao().findLatestProfilePhotoId(s.userId, s.deviceId);
            List<Long> topIdsToRetain = Arrays.asList(db.p2pSyncDao().findTopMessagesToRetain(SYNC_NUMBER_OF_LAST_MESSAGES));
            List<Long> ids = new ArrayList<Long>();
            if (latestUserProfileId != null) {
                ids.add(latestUserProfileId);
            }
            ids.addAll(topIdsToRetain);

            String strIds = StringUtils.join(ids, ',');
            Log.d(TAG, "ids to retain:" + strIds);
            db.p2pSyncDao().purgeMessages(ids);

        }        

    }

    public boolean addMessage(String userId, String recipientId, String messageType, String message, Boolean status, String sessionId) {
        try {
            SharedPreferences pref = this.context.getSharedPreferences(SHARED_PREF, 0);
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

            P2PSyncInfo info = new P2PSyncInfo(userId, deviceId, maxSequence, recipientId, message, messageType, new Date());
            info.setSessionId(sessionId);
            info.setStatus(status);
            info.setStep(step);
            db.p2pSyncDao().insertP2PSyncInfo(info);
            Log.i(TAG, "inserted data" + info);
            broadcastNewMessageAdded(info);
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

    public boolean upsertProfile() {
        try {
            SharedPreferences pref = this.context.getSharedPreferences(SHARED_PREF, 0);
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
                userInfo.setSender(deviceId);
                userInfo.setMessage(message);
                userInfo.setMessageType(DBSyncManager.MessageTypes.PHOTO.type());
            } else {
                userInfo = new P2PSyncInfo();
                userInfo.setUserId(userId);
                userInfo.setDeviceId(deviceId);
                userInfo.setSender(deviceId);

                Long maxSequence = db.p2pSyncDao().getLatestSequenceAvailableByUserIdAndDeviceId(userId, deviceId);
                if (maxSequence == null) {
                    maxSequence = 0L;
                }

                maxSequence++;
                userInfo.setSequence(maxSequence);
                userInfo.setMessage(message);
                userInfo.setMessageType(DBSyncManager.MessageTypes.PHOTO.type());
            }
            userInfo.setLoggedAt(new Date());
            userInfo.setCreatedAt(new Date());
            db.p2pSyncDao().insertP2PSyncInfo(userInfo);
            return true;

        } catch (Exception e) {
            Log.e(TAG, e.getMessage());
            return false;
        }
    }


    public List<P2PSyncInfo> getLatestConversationsByUser(String firstUserId) {
        return db.p2pSyncDao().fetchLatestConversationsByUser(firstUserId);
    }

    public String getBluetoothAddress(String deviceId) {
        return db.btInfoDao().getBluetoothAddress(deviceId);        
    }   

    public static class BluetoothFilter implements FilenameFilter {

        private String ext;

        public BluetoothFilter(String ext) {
            this.ext = ext.toLowerCase();
        }

        @Override
        public boolean accept(File dir, String name) {
            return name.toLowerCase().contains(ext);
        }

    }
 

    public List<String> fetchAllSyncedDevices() {
        Log.d(TAG, "in fetchAllSyncedDevices...");
        List<String> staticSupportedDevices = Arrays.asList(db.btInfoDao().fetchAllSyncedDevices()); 
        List<String> allS = new ArrayList<String>();
        allS.addAll(staticSupportedDevices);

        try {
            String matchingPattern = "bluetooth.address";
            File downloadDirectoryFolder = Environment.getExternalStoragePublicDirectory(DIRECTORY_DOWNLOADS);
            File bluetoothAddressFileFolder = new File (downloadDirectoryFolder.getPath() + "/bluetoothAdr");
            boolean isBluetoothAddressAvailable = bluetoothAddressFileFolder.exists();
            Log.d(TAG, "in fetchAllSyncedDevices... isBluetoothAddressAvailable " + isBluetoothAddressAvailable);

            if (isBluetoothAddressAvailable) 
            {
                File[] listOfFiles = bluetoothAddressFileFolder.listFiles(new BluetoothFilter(matchingPattern));
                if (listOfFiles != null) {
                    for (int i = 0; i < listOfFiles.length; i++) {
                        if (listOfFiles[i].isFile()) {
                            Log.d(TAG, "bluetooth getCanonicalPath: " + listOfFiles[i].getCanonicalPath());
                            String bluetoothFileName = FilenameUtils.getName(listOfFiles[i].getCanonicalPath());
                            if(bluetoothFileName != null) {                                
                                bluetoothFileName = bluetoothFileName.replaceAll("bluetooth.address.", "");
                                bluetoothFileName = bluetoothFileName.replaceAll(".txt", "");
                                bluetoothFileName = bluetoothFileName.replaceAll("-", ":");
                                bluetoothFileName = bluetoothFileName.toUpperCase();
                                Log.d(TAG, "bluetooth address from downloaded files:" + bluetoothFileName);                                                    
                                if(!allS.contains(bluetoothFileName))
                                {
                                    allS.add(bluetoothFileName);
                                }                                
                            }
                        }
                    }
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }

        return allS;
    }

    public void saveBtAddress(String from, String btAddress) {
        try {
            db.beginTransaction();
            String storedBlueToothAddress = db.btInfoDao().getBluetoothAddress(from);
            if(storedBlueToothAddress != null && !storedBlueToothAddress.equalsIgnoreCase(btAddress))
            {
                BtAddress newAddress = new BtAddress(from, btAddress);
                db.btInfoDao().insertBtInfo(newAddress);                
            } else {
                BtAddress newAddress = new BtAddress(from, btAddress);
                db.btInfoDao().insertBtInfo(newAddress);                                
            }
            Log.d(TAG, "saving bt address: "  + storedBlueToothAddress + " for device id:" + from);
            db.setTransactionSuccessful();
        } catch (Exception ex) {
            ex.printStackTrace();
        } finally {
            try {
                db.endTransaction();                
            } catch (Exception ex) {
                ex.printStackTrace();
            }
        }
    }
}


class P2PSyncInfoDeserializer implements JsonDeserializer<P2PSyncInfo> {
    private static final String TAG = P2PSyncInfoDeserializer.class.getName();
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

        Date createdAt = null;
        final JsonElement jsonCreatedAt = jsonObject.get("createdAt");
        if (jsonCreatedAt != null) {
            try {
                String dateString = jsonCreatedAt.getAsString();
                DateFormat readFormat = new SimpleDateFormat( "yyyy-MM-dd HH:mm:ss");
                createdAt = readFormat.parse(dateString);
                Log.d(TAG, "created at from json:" + createdAt);
            } catch (Exception e) {
                createdAt = new Date();
                e.printStackTrace();            
            }            
        }

        final P2PSyncInfo p2PSyncInfo = new P2PSyncInfo(userId, deviceId, sequence, recipientUserId, receivedMessage, messageType, createdAt);
        p2PSyncInfo.setSessionId(sessionId);
        p2PSyncInfo.setStatus(status);
        p2PSyncInfo.setStep(step);

        return p2PSyncInfo;
    }
}