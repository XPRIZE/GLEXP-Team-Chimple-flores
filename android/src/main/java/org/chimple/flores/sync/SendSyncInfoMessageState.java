package org.chimple.flores.sync;

import android.util.Log;

import org.chimple.flores.db.AppDatabase;
import org.chimple.flores.db.DBSyncManager;
import org.chimple.flores.db.P2PDBApiImpl;

import static org.chimple.flores.sync.P2PStateFlow.Transition.RECEIVE_DB_SYNC_INFORMATION;
import static org.chimple.flores.sync.P2PStateFlow.Transition.SEND_DB_SYNC_INFORMATION;

public class SendSyncInfoMessageState implements P2PState {
    private static final String TAG = SendInitialHandShakingMessageState.class.getSimpleName();

    private P2PStateFlow.Transition cTransition;

    public SendSyncInfoMessageState() {
        this.cTransition = SEND_DB_SYNC_INFORMATION;
    }

    public P2PStateFlow.Transition getTransition() {
        return this.cTransition;
    }

    @Override
    public void onEnter(P2PStateFlow p2PStateFlow, DBSyncManager manager, String message) {
        //send handshaking message
        try {
            String handShakingInformationReceived = p2PStateFlow.getStateResult(P2PStateFlow.Transition.RECEIVE_HANDSHAKING_INFORMATION);
            Log.i(TAG, "handShakingInformationReceived in SendSyncInfoMessageState" + handShakingInformationReceived);
            Log.i(TAG, "onEnter SendSyncInfoMessageState thread 2 " + (p2PStateFlow.getThread() != null));
            String syncInformation = P2PDBApiImpl.getInstance(manager.getContext()).buildAllSyncMessages(handShakingInformationReceived);
            final String updatedSyncInformation = "START" + syncInformation + "END";
            Log.i(TAG, "updatedSyncInformation:" + updatedSyncInformation);
            if (p2PStateFlow.getThread() != null && updatedSyncInformation != null && !updatedSyncInformation.isEmpty()) {
                byte[] bytes = updatedSyncInformation.getBytes();
                p2PStateFlow.getThread().write(updatedSyncInformation.getBytes(), 0, bytes.length);
                Log.i(TAG, "syncInformation message sent" + updatedSyncInformation);
            }
            p2PStateFlow.setAllSyncInformationSent(true);
            if(p2PStateFlow.isAllSyncInformationReceived()) {
                Log.i(TAG, "setAllSyncInformationSent");
                p2PStateFlow.allMessagesExchanged();
            }
        } catch (Exception e) {
            Log.e(TAG, e.getMessage());
        }

    }

    @Override
    public void onExit(P2PStateFlow.Transition newTransition) {
        Log.i(TAG, "EXIT SEND_DB_SYNC_INFORMATION STATE to transition" + newTransition);
    }

    @Override
    public P2PStateFlow.Transition process(P2PStateFlow.Transition transition) {
        P2PStateFlow.Transition newTransition = null;
        switch (transition) {
            case RECEIVE_DB_SYNC_INFORMATION: {
                newTransition = RECEIVE_DB_SYNC_INFORMATION;
                break;
            }
            default: {
                newTransition = SEND_DB_SYNC_INFORMATION;
                break;
            }
        }
        return newTransition;

    }

    public String getOutcome() {
        return null;
    }
}
