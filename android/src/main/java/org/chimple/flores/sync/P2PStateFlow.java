package org.chimple.flores.sync;

import android.content.Intent;
import android.content.SharedPreferences;
import android.support.v4.content.LocalBroadcastManager;
import android.util.Log;

import java.util.HashMap;
import java.util.Map;

import org.chimple.flores.db.DBSyncManager;
import org.chimple.flores.sync.sender.ConnectedThread;

import static org.chimple.flores.sync.Direct.P2POrchester.allMessageExchangedForP2P;
import static org.chimple.flores.sync.Direct.P2PSyncManager.P2P_SHARED_PREF;
import static org.chimple.flores.sync.NSD.NSDOrchester.allMessageExchangedForNSD;
import static org.chimple.flores.sync.P2PStateFlow.Transition.NONE;
import static org.chimple.flores.sync.P2PStateFlow.Transition.RECEIVE_DB_SYNC_INFORMATION;
import static org.chimple.flores.sync.P2PStateFlow.Transition.RECEIVE_HANDSHAKING_INFORMATION;
import static org.chimple.flores.sync.P2PStateFlow.Transition.SEND_DB_SYNC_INFORMATION;
import static org.chimple.flores.sync.P2PStateFlow.Transition.SEND_HANDSHAKING_INFORMATION;

public class P2PStateFlow {

    public enum Transition {
        RECEIVE_HANDSHAKING_INFORMATION,
        SEND_HANDSHAKING_INFORMATION,
        RECEIVE_DB_SYNC_INFORMATION,
        SEND_DB_SYNC_INFORMATION,
        NONE
    }

    private static final String TAG = P2PStateFlow.class.getSimpleName();

    private boolean handShakingInformationReceived = false;
    private boolean allSyncInformationReceived = false;
    private boolean handShakingInformationSent = false;
    private boolean allSyncInformationSent = false;

    private DBSyncManager manager;
    private static P2PStateFlow instance;
    private ConnectedThread thread;

    private Map<Transition, P2PState> allPossibleStates = null;

    private P2PState currentState;


    private P2PStateFlow() {
    }


    /**
     * @return the state that handled this message
     */
    public P2PState getState() {
        return currentState;
    }


    public static P2PStateFlow getInstanceUsingDoubleLocking(DBSyncManager manager) {
        if (instance == null) {
            synchronized (P2PStateFlow.class) {
                if (instance == null) {
                    instance = new P2PStateFlow();
                    instance.manager = manager;

                    instance.initializeAllP2PStates();
                    instance.setInitialState(new NoneState());
                }
            }
        }
        return instance;
    }

    public void setConnectedThread(final ConnectedThread thread) {
        this.thread = thread;
    }

    public ConnectedThread getThread() {
        return thread;
    }

    private void initializeAllP2PStates() {
        allPossibleStates = new HashMap<Transition, P2PState>();
        allPossibleStates.put(NONE, new NoneState());
        allPossibleStates.put(SEND_HANDSHAKING_INFORMATION, new SendInitialHandShakingMessageState());
        allPossibleStates.put(RECEIVE_HANDSHAKING_INFORMATION, new ReceiveInitialHandShakingMessageState());
        allPossibleStates.put(SEND_DB_SYNC_INFORMATION, new SendSyncInfoMessageState());
        allPossibleStates.put(RECEIVE_DB_SYNC_INFORMATION, new ReceiveSyncInfoMessageState());
    }

    private void setInitialState(P2PState initialState) {
        this.currentState = initialState;
    }

    public void processMessages(String receivedMessage) {
        if (receivedMessage != null) {
            String handShakeMessage = "\"message_type\":\"handshaking\"";
            boolean isHandShakingMessage = receivedMessage.contains(handShakeMessage);
            if (!handShakingInformationReceived && isHandShakingMessage) {
                this.transit(RECEIVE_HANDSHAKING_INFORMATION, receivedMessage);
            } else if (!allSyncInformationReceived && !isHandShakingMessage) {
                this.transit(RECEIVE_DB_SYNC_INFORMATION, receivedMessage);
            }
        }
    }

    public void resetAllStates() {
        synchronized (P2PStateFlow.class) {
            if (instance != null) {
                this.setHandShakingInformationSent(false);
                this.setHandShakingInformationReceived(false);
                this.setAllSyncInformationSent(false);
                this.setAllSyncInformationReceived(false);
                allPossibleStates = null;
                instance.initializeAllP2PStates();
                instance.setInitialState(new NoneState());
            }
        }
    }

    private void broadcastExitMessageForP2P() {
        Log.d("sender", "Broadcasting message exit for P2P");
        Intent intent = new Intent(allMessageExchangedForP2P);
        LocalBroadcastManager.getInstance(manager.getContext()).sendBroadcast(intent);
    }

    private void broadcastExitMessageForNSD() {
        Log.d("sender", "Broadcasting message exit message for NSD");
        Intent intent = new Intent(allMessageExchangedForNSD);
        LocalBroadcastManager.getInstance(manager.getContext()).sendBroadcast(intent);
    }

    public void allMessagesExchanged() {
        SharedPreferences pref = manager.getContext().getSharedPreferences(P2P_SHARED_PREF, 0);
        boolean isP2P = pref.getBoolean("IS_P2P", false); // getting String

        Log.i(TAG, ".... All messages exchanged .... with P2P = " + isP2P);
        this.resetAllStates();
        if(isP2P) {
            this.broadcastExitMessageForP2P();
        } else {
            this.broadcastExitMessageForNSD();
        }
    }

    public void transit(Transition command, String message) {
        Log.i(TAG, "checking if thread is not null" + (thread != null));
        Transition transitionTo = this.getState().process(command);
        P2PState currentState = this.getState();
        Transition currentStateTransition = currentState.getTransition();

        if (transitionTo != currentStateTransition) {
            currentState.onExit(transitionTo);
            if (allPossibleStates.containsKey(transitionTo)) {
                P2PState nextState = allPossibleStates.get(transitionTo);
                if (nextState != null) {
                    this.currentState = nextState;
                    nextState.onEnter(instance, manager, message);
                }
            }
        }
    }

    public String getStateResult(Transition t) {
        if (allPossibleStates.containsKey(t)) {
            P2PState nextState = allPossibleStates.get(t);
            return nextState.getOutcome();
        }
        return null;
    }

    public void setHandShakingInformationReceived(boolean handShakingInformationReceived) {
        this.handShakingInformationReceived = handShakingInformationReceived;
    }

    public boolean isHandShakingInformationReceived() {
        return handShakingInformationReceived;
    }

    public boolean isAllSyncInformationReceived() {
        return allSyncInformationReceived;
    }

    public boolean isHandShakingInformationSent() {
        return handShakingInformationSent;
    }

    public boolean isAllSyncInformationSent() {
        return allSyncInformationSent;
    }

    public void setAllSyncInformationReceived(boolean allSyncInformationReceived) {
        this.allSyncInformationReceived = allSyncInformationReceived;
    }

    public void setHandShakingInformationSent(boolean handShakingInformationSent) {
        this.handShakingInformationSent = handShakingInformationSent;
    }

    public void setAllSyncInformationSent(boolean allSyncInformationSent) {
        this.allSyncInformationSent = allSyncInformationSent;
    }
}