package org.chimple.flores.sync;

import org.chimple.flores.db.DBSyncManager;

public interface P2PState {
    public void onEnter(P2PStateFlow p2PStateFlow, DBSyncManager manager, String message);

    public void onExit(P2PStateFlow.Transition transition);

    public P2PStateFlow.Transition process(P2PStateFlow.Transition transition);

    public P2PStateFlow.Transition getTransition();

    public String getOutcome();
}


