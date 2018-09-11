package org.chimple.flores.db.dao;

import android.arch.persistence.room.Dao;
import android.arch.persistence.room.Insert;
import android.arch.persistence.room.OnConflictStrategy;
import android.arch.persistence.room.Query;

import java.util.List;

import org.chimple.flores.db.entity.P2PLatestInfoByUserAndDevice;
import org.chimple.flores.db.entity.P2PSyncInfo;
import org.chimple.flores.db.entity.P2PUserIdDeviceIdAndMessage;
import org.chimple.flores.db.entity.P2PUserIdMessage;


@Dao
public interface P2PSyncInfoDao {
    @Query("SELECT * FROM P2PSyncInfo WHERE user_id=:userId AND device_id=:deviceId")
    public List<P2PSyncInfo> getSyncInformationByUserIdAndDeviceId(String userId, String deviceId);

    @Query("SELECT * FROM P2PSyncInfo WHERE user_id=:userId")
    public P2PSyncInfo[] getSyncInformationByUserId(String userId);

    @Query("SELECT * FROM P2PSyncInfo WHERE user_id=:userId and message_type= :messageType")
    public P2PSyncInfo getProfileByUserId(String userId, String messageType);


    @Query("SELECT MAX(sequence) FROM P2PSyncInfo WHERE user_id=:userId AND device_id=:deviceId GROUP BY user_id, device_id")
    public Long getLatestSequenceAvailableByUserIdAndDeviceId(String userId, String deviceId);

    @Query("SELECT MAX(step) FROM P2PSyncInfo WHERE user_id=:userId AND session_id=:sessionId GROUP BY user_id, session_id")
    public Long getLatestStepForUserIdAndSessionId(String userId, String sessionId);


    @Query("SELECT user_id, device_id, MAX(sequence) as sequence FROM P2PSyncInfo where user_id is not null and device_id is not null GROUP BY user_id, device_id")
    public P2PLatestInfoByUserAndDevice[] getLatestInfoAvailableByUserIdAndDeviceId();


    @Query("SELECT * FROM P2PSyncInfo WHERE user_id=:userId AND device_id=:deviceId AND sequence > :startingSequence and sequence <= :endingSequence")
    public P2PSyncInfo[] fetchByUserAndDeviceBetweenSequences(String userId, String deviceId, Long startingSequence, Long endingSequence);


    @Query("SELECT * FROM P2PSyncInfo WHERE user_id=:userId AND device_id=:deviceId AND sequence <= :sequence")
    public P2PSyncInfo[] fetchByUserAndDeviceUpToSequence(String userId, String deviceId, Long sequence);

    @Query("SELECT * FROM P2PSyncInfo WHERE user_id=:userId AND device_id=:deviceId AND sequence = :sequence")
    public List<P2PSyncInfo> fetchByUserAndDeviceAndSequence(String userId, String deviceId, Long sequence);


    @Insert(onConflict = OnConflictStrategy.REPLACE)
    public Long insertP2PSyncInfo(P2PSyncInfo info);

    @Query("SELECT ps.device_id from (SELECT user_id, max(sequence) as sequence FROM P2PSyncInfo  WHERE message_type = 'Photo' group by user_id) as tmp, P2PSyncInfo ps where ps.user_id = tmp.user_id  and ps.sequence = tmp.sequence and ps.user_id =:userId")
    public String getDeviceForRecipientUserId(String userId);


    @Query("SELECT ps.user_id, ps.device_id, ps.message  from (SELECT user_id, max(sequence) as sequence FROM P2PSyncInfo  WHERE message_type = 'Photo' group by user_id) as tmp, P2PSyncInfo ps where ps.user_id = tmp.user_id and ps.sequence = tmp.sequence")
    public P2PUserIdDeviceIdAndMessage[] fetchAllUsers();

    @Query("SELECT distinct(user_id) from P2PSyncInfo")
    public String[] fetchAllNeighours();

    @Query("SELECT MAX(step) FROM P2PSyncInfo WHERE session_id=:sessionId GROUP BY session_id")
    public Long getLatestStepSessionId(String sessionId);

    @Query("SELECT tmp.user_id, ps.message from (SELECT user_id, max(sequence) as sequence FROM P2PSyncInfo  WHERE message_type = :messageType AND user_id in (:userIds) group by user_id)  as tmp, P2PSyncInfo ps where ps.user_id = tmp.user_id and ps.sequence = tmp.sequence")
    public List<P2PUserIdMessage> fetchLatestMessagesByMessageType(String messageType, List<String> userIds);

    @Query("SELECT tmp.user_id, ps.message from (SELECT user_id, max(sequence) as sequence FROM P2PSyncInfo  WHERE message_type = :messageType group by user_id)  as tmp, P2PSyncInfo ps where ps.user_id = tmp.user_id and ps.sequence = tmp.sequence")
    public List<P2PUserIdMessage> fetchLatestMessagesByMessageType(String messageType);


    @Query("SELECT * FROM (SELECT * FROM P2PSyncInfo WHERE message_type = :messageType AND ((user_id = :userId and recipient_user_id = :recipientId) or (user_id = :recipientId and recipient_user_id = :userId)) order by logged_at desc limit 100) order by logged_at")
    public List<P2PSyncInfo> fetchConversations(String userId, String recipientId, String messageType);

    @Query("SELECT p2p.* from (SELECT session_id, max(step) as step from P2PSyncInfo where message_type = :messageType group by session_id) tmp, P2PSyncInfo p2p where p2p.session_id = tmp.session_id and p2p.step = tmp.step and p2p.status = 1 and ((p2p.user_id = :userId and p2p.recipient_user_id = :recipientId) or (p2p.user_id = :recipientId and p2p.recipient_user_id = :userId))")
    public List<P2PSyncInfo> fetchLatestConversations(String userId, String recipientId, String messageType);

    @Query("SELECT p2p.* from (SELECT session_id, max(step) as step from P2PSyncInfo where message_type = :messageType group by session_id) tmp, P2PSyncInfo p2p where p2p.session_id = tmp.session_id and p2p.step = tmp.step and p2p.status = 1 and (p2p.user_id = :userId or p2p.recipient_user_id = :userId)")
    public List<P2PSyncInfo> fetchLatestConversations(String userId, String messageType);

    @Query("SELECT p2p.* from (SELECT session_id, max(step) as step from P2PSyncInfo where status = 1 group by session_id) tmp, P2PSyncInfo p2p where p2p.session_id = tmp.session_id and p2p.step = tmp.step and p2p.user_id = :userId")
    public List<P2PSyncInfo> fetchLatestConversationsByUser(String userId);
}
