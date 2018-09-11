package org.chimple.flores;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import android.app.Activity;
import android.os.Bundle;
import android.support.annotation.NonNull;
import android.util.Log;
import android.content.Intent;

import io.flutter.plugin.common.EventChannel;
import io.flutter.plugin.common.EventChannel.EventSink;
import io.flutter.plugin.common.EventChannel.StreamHandler;
import io.flutter.plugin.common.MethodCall;
import io.flutter.plugin.common.MethodChannel;
import io.flutter.plugin.common.MethodChannel.MethodCallHandler;
import io.flutter.plugin.common.MethodChannel.Result;
import io.flutter.plugin.common.PluginRegistry;

import org.chimple.flores.db.DBSyncManager;
import org.chimple.flores.db.entity.P2PSyncInfo;
import org.chimple.flores.db.entity.P2PUserIdDeviceIdAndMessage;
import org.chimple.flores.db.entity.P2PUserIdMessage;
import org.chimple.flores.scheduler.JobUtils;

/**
 * FloresPlugin
 */
public class FloresPlugin implements MethodCallHandler, StreamHandler {
    private static final String TAG = FloresPlugin.class.getName();
    private static MethodChannel methodChannel;
  /**
   * Plugin registration.
   */
  public static void registerWith(PluginRegistry.Registrar registrar) {
      if (methodChannel != null) {
          Log.i(TAG, "You should not call registerWith more than once.");
      } else {
        methodChannel = new MethodChannel(registrar.messenger(), "chimple.org/flores");      
      }

    final EventChannel eventChannel =
        new EventChannel(registrar.messenger(), "chimple.org/flores_event");
    final FloresPlugin instance = new FloresPlugin(registrar);
    eventChannel.setStreamHandler(instance);
    methodChannel.setMethodCallHandler(instance);
  }

  FloresPlugin(PluginRegistry.Registrar registrar) {
    this.registrar = registrar;
  }

  private final PluginRegistry.Registrar registrar;

  public static void onMessageReceived(P2PSyncInfo message) {
    Log.i(TAG, "messageReceived: "+message);
    methodChannel.invokeMethod("messageReceived", convertToMap(message));
  }

  @Override
  public void onMethodCall(MethodCall call, Result result) {
      switch (call.method) {
          case "getUsers":
          {
              List<P2PUserIdDeviceIdAndMessage> udList = DBSyncManager.getInstance(registrar.context()).getUsers();
              List<Map<String, String>> users = new ArrayList<Map<String, String>>();
              Log.i(TAG, "getUsers: "+users);

              for (P2PUserIdDeviceIdAndMessage ud: udList
                      ) {
                  Map<String, String> user = new HashMap<String, String>();
                  user.put("userId", ud.userId);
                  user.put("deviceId", ud.deviceId);
                  user.put("message", ud.message);
                  users.add(user);
              }

              if (users.size() >= 0) {
                  result.success(users);
              } else {
                  result.error("UNAVAILABLE", "Users are not available.", null);
              }
              break;
          }
          case "addUser":
          {
              Map<String, String> arg = (Map<String, String>)call.arguments;
              String userId = arg.get("userId");
              String deviceId = arg.get("deviceId");
              String message = arg.get("message");
              boolean status = DBSyncManager.getInstance(registrar.context()).upsertUser(userId, deviceId, message);
            //   DBSyncManager.getInstance(registrar.context()).addMessage(userId, "r3" + userId, "Chat", "Good Day 🎮" + userId, true, "session 3" + userId);
            //   DBSyncManager.getInstance(registrar.context()).addMessage(userId, "r4" + userId, "Chat", "🍤🍉" + userId, true, "session 4" + userId);

            //   DBSyncManager.getInstance(registrar.context()).addMessage(userId, "r1" + userId, "Chat", "Hi" + userId, true, "session1" + userId);
            //   DBSyncManager.getInstance(registrar.context()).addMessage(userId, "r1" + userId, "Chat", "😸" + userId, true, "session2" + userId);

            //   DBSyncManager.getInstance(registrar.context()).loggedInUser(userId, deviceId);
              result.success(status);
              break;
          }
          case "start":
          {
              JobUtils.scheduledJob(registrar.context().getApplicationContext(), true);
              break;
          }
          case "addMessage":
          {
              Map<String, String> arg = (Map<String, String>)call.arguments;
              String userId = arg.get("userId");
              String recipientId = arg.get("recipientId");
              String messageType = arg.get("messageType");
              String message = arg.get("message");
              String statusStr = arg.get("status");
              Boolean status = Boolean.valueOf(statusStr);
              String sessionId = arg.get("sessionId");
              boolean retStatus =
              DBSyncManager.getInstance(registrar.context())
                              .addMessage(userId, recipientId, messageType, message, status, sessionId);
              result.success(retStatus);
              break;
          }
          case "getLatestMessages":
          {
              Map<String, String> arg = (Map<String, String>)call.arguments;
              String messageType = arg.get("messageType");
              String userId = arg.get("userId");
              String secondUserId = arg.get("secondUserId");
              List<String> userIds = new ArrayList<String>();
              userIds.add(userId);
              userIds.add(secondUserId);
              List<P2PUserIdMessage> messageList =
              DBSyncManager.getInstance(registrar.context())
                              .fetchLatestMessagesByMessageType(messageType, userIds);
              List<Map<String, String>> messages = new ArrayList<Map<String, String>>();
              for (P2PUserIdMessage m: messageList
                      ) {
                  Map<String, String> message = new HashMap<String, String>();
                  message.put("userId", m.userId);
                  message.put("message", m.message);
                  messages.add(message);
              }

              if (messages.size() >= 0) {
                  result.success(messages);
              } else {
                  result.error("UNAVAILABLE", "Messages are not available.", null);
              }
              break;
          }
          case "getConversations":
          {
              Map<String, String> arg = (Map<String, String>)call.arguments;
              String messageType = arg.get("messageType");
              String userId = arg.get("userId");
              String secondUserId = arg.get("secondUserId");
              List<P2PSyncInfo> messageList =
              DBSyncManager.getInstance(registrar.context())
                              .getConversations(userId, secondUserId, messageType);
              Log.i(TAG, "getConversations: "+messageType+userId+secondUserId);
              List<Map<String, String>> messages = convertToListOfMaps(messageList);
              Log.i(TAG, messages.toString());
              if (messages.size() >= 0) {
                  result.success(messages);
              } else {
                  result.error("UNAVAILABLE", "Messages are not available.", null);
              }
              break;
          }
          case "getLatestConversations":
          {
              Map<String, String> arg = (Map<String, String>)call.arguments;
              String messageType = arg.get("messageType");
              String userId = arg.get("userId");
              List<P2PSyncInfo> messageList =
              DBSyncManager.getInstance(registrar.context())
                              .getLatestConversations(userId, messageType);
              List<Map<String, String>> messages = convertToListOfMaps(messageList);

              if (messages.size() >= 0) {
                  result.success(messages);
              } else {
                  result.error("UNAVAILABLE", "Messages are not available.", null);
              }
              break;
          }
          case "loggedInUser":
          {
              Map<String, String> arg = (Map<String, String>)call.arguments;
              String userId = arg.get("userId");
              String deviceId = arg.get("deviceId");
              boolean status = DBSyncManager.getInstance(registrar.context())
                              .loggedInUser(userId, deviceId);

              result.success(status);
              break;

          }
          default:
          {
              result.notImplemented();
          }
      }
  }

  @Override
  public void onListen(Object arguments, EventSink events) {}

  @Override
  public void onCancel(Object arguments) {}

  static private List<Map<String, String>> convertToListOfMaps(List<P2PSyncInfo> messageList) {
      List<Map<String, String>> messages = new ArrayList<Map<String, String>>();
      for (P2PSyncInfo m: messageList
              ) {
          Map<String, String> message = convertToMap(m);
          messages.add(message);
      }
    return messages;
  }

    @NonNull
    static private Map<String, String> convertToMap(P2PSyncInfo m) {
        Map<String, String> message = new HashMap<String, String>();
        message.put("userId", m.userId);
        message.put("message", m.message);
        message.put("messageType", m.messageType);
        message.put("deviceId", m.deviceId);
        message.put("recipientUserId", m.recipientUserId);
        message.put("sessionId", m.sessionId);
        if(m.id != null)
          message.put("id", m.id.toString());
        if(m.loggedAt != null)
          message.put("loggedAt", Long.toString(m.loggedAt.getTime()));
        if(m.sequence != null)
          message.put("sequence", m.sequence.toString());
        if(m.status != null)
          message.put("status", m.status.toString());
        if(m.step != null)
          message.put("step", m.step.toString());
        Log.i(TAG, "convertToMap: "+message.toString());
        return message;
    }

    /**
     * Handle the incoming message and immediately closes the activity.
     *
     * <p>Needs to be invocable by Android system; hence it is public.
     */
    public static class MessageReceivedActivity extends Activity {

        @Override
        protected void onCreate(Bundle savedInstanceState) {
            super.onCreate(savedInstanceState);
            Log.i(TAG, "messageReceivedActivity created");

            // Get the Intent that started this activity and extract the string
            Intent intent = getIntent();
            String userId = intent.getStringExtra("userId");
            String message = intent.getStringExtra("message");
            String deviceId = intent.getStringExtra("deviceId");
            String messageType = intent.getStringExtra("messageType");
            String recipientUserId = intent.getStringExtra("recipientUserId");
            String sessionId = intent.getStringExtra("sessionId");
            Long id = intent.getLongExtra("id", 0);
            Long loggedAt = intent.getLongExtra("loggedAt", 0);
            Long sequence = intent.getLongExtra("sequence", 0);
            boolean status = intent.getBooleanExtra("status", true);
            Long step = intent.getLongExtra("step", 0);
            Log.i(TAG, "messageReceivedActivity: "+message);

            methodChannel.invokeMethod("messageReceived", message);
            finish();
        }
    }
}
