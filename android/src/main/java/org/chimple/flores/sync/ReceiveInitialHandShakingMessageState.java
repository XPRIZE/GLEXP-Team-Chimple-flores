package org.chimple.flores.sync;

import android.util.Log;

import org.chimple.flores.db.DBSyncManager;

import static org.chimple.flores.sync.P2PStateFlow.Transition.RECEIVE_HANDSHAKING_INFORMATION;
import static org.chimple.flores.sync.P2PStateFlow.Transition.SEND_DB_SYNC_INFORMATION;
import static org.chimple.flores.sync.P2PStateFlow.Transition.SEND_HANDSHAKING_INFORMATION;

public class ReceiveInitialHandShakingMessageState implements P2PState {
    private static final String TAG = ReceiveInitialHandShakingMessageState.class.getSimpleName();
    private P2PStateFlow.Transition cTransition;

    private String outcome = null;


    public ReceiveInitialHandShakingMessageState() {
        this.cTransition = RECEIVE_HANDSHAKING_INFORMATION;
    }

    @Override
    public void onEnter(P2PStateFlow p2PStateFlow, DBSyncManager manager, String readMessage) {
        if (p2PStateFlow.getThread() != null) {
            this.outcome = readMessage;
            Log.i(TAG, "handShakingInformationReceived" + this.outcome);
            p2PStateFlow.setHandShakingInformationReceived(true);
            if (!p2PStateFlow.isHandShakingInformationSent()) {
                p2PStateFlow.transit(SEND_HANDSHAKING_INFORMATION, this.outcome);
            } else {
                p2PStateFlow.transit(SEND_DB_SYNC_INFORMATION, null);
            }
        } else {
            Log.i(TAG, "ReceiveInitialHandShakingMessageState THREAD NULL >>>>>");
        }

    }

    public String getOutcome() {
        return this.outcome;
    }

    @Override
    public void onExit(P2PStateFlow.Transition transition) {

    }

    @Override
    public P2PStateFlow.Transition getTransition() {
        return this.cTransition;
    }

    @Override
    public P2PStateFlow.Transition process(P2PStateFlow.Transition transition) {
        P2PStateFlow.Transition newTransition = null;
        switch (transition) {
            case SEND_DB_SYNC_INFORMATION: {
                newTransition = SEND_DB_SYNC_INFORMATION;
                break;
            }
            case SEND_HANDSHAKING_INFORMATION: {
                newTransition = SEND_HANDSHAKING_INFORMATION;
                break;
            }
            default: {
                newTransition = RECEIVE_HANDSHAKING_INFORMATION;
                break;
            }
        }
        return newTransition;
    }
}
