package org.chimple.flores.sync.NSD;
import java.util.List;

import org.chimple.flores.sync.SyncUtils;

public interface NSDOrchesterCallBack {
    public void NSDDiscovertyStateChanged(SyncUtils.DiscoveryState newState);
    public void NSDConnectionStateChanged(SyncUtils.ConnectionState newState);
    public void NSDListeningStateChanged(SyncUtils.ReportingState newState);
    public void NSDConnected(String address, boolean isOwner);
    public void ListeningSocketFailed(String reason);
}
