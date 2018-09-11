package org.chimple.flores.sync.NSD;

import java.net.InetAddress;
import java.util.List;

import org.chimple.flores.sync.SyncUtils;

public interface NSDWifiConnectionUpdateCallBack {
    public void Connected(InetAddress remote, boolean ListeningStill);
    public void processServiceList(List<NSDSyncService> list);
    public void serviceUpdateStatus(SyncUtils.DiscoveryState newState);
}
