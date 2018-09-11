package org.chimple.flores.sync;

import android.util.Log;

import org.chimple.flores.db.DBSyncManager;

import static org.chimple.flores.sync.P2PStateFlow.Transition.NONE;
import static org.chimple.flores.sync.P2PStateFlow.Transition.RECEIVE_HANDSHAKING_INFORMATION;
import static org.chimple.flores.sync.P2PStateFlow.Transition.SEND_HANDSHAKING_INFORMATION;

public class NoneState implements P2PState {
    private static final String TAG = NoneState.class.getSimpleName();

    private P2PStateFlow.Transition cTransition;

    public NoneState() {
        this.cTransition = NONE;
    }

    public P2PStateFlow.Transition getTransition() {
        return this.cTransition;
    }

    @Override
    public void onEnter(P2PStateFlow p2PStateFlow, DBSyncManager manager, String message) {
        Log.i(TAG, "ENTER NONE STATE ... WAITING FOR NEXT COMMAND");
    }

    @Override
    public void onExit(P2PStateFlow.Transition newTransition) {
        Log.i(TAG, "EXIT NONE STATE to transition" + newTransition);
    }

    @Override
    public P2PStateFlow.Transition process(P2PStateFlow.Transition transition) {
        P2PStateFlow.Transition newTransition = null;
        switch (transition) {
            case SEND_HANDSHAKING_INFORMATION: {
                newTransition = SEND_HANDSHAKING_INFORMATION;
                break;
            }
            case RECEIVE_HANDSHAKING_INFORMATION: {
                newTransition = RECEIVE_HANDSHAKING_INFORMATION;
                break;
            }
            default: {
                newTransition = NONE;
                break;
            }
        }
        return newTransition;
    }

    public String getOutcome() {
        return null;
    }
}
