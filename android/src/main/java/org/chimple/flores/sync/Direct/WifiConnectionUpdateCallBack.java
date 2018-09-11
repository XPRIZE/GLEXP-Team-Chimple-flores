package org.chimple.flores.sync.Direct;

import android.net.NetworkInfo;
import android.net.wifi.p2p.WifiP2pDevice;
import android.net.wifi.p2p.WifiP2pGroup;

import java.net.InetAddress;
import java.util.Collection;
import java.util.List;
import java.util.Map;

import org.chimple.flores.sync.SyncUtils;

public interface WifiConnectionUpdateCallBack {
    public void handleWifiP2PStateChange(int state);

    public void handleWifiP2PConnectionChange(NetworkInfo networkInfo);

    public boolean gotPeersList(Collection<WifiP2pDevice> list);

    public void processServiceList(List<P2PSyncService> list);

    public Map<String, P2PSyncService> foundNeighboursList(List<P2PSyncService> list);

    public void GroupInfoAvailable(WifiP2pGroup group);

    public void connectionStatusChanged(SyncUtils.SyncHandShakeState state, NetworkInfo.DetailedState detailedState, int Error, P2PSyncService currentDevice);

    public void Connected(InetAddress remote, boolean ListeningStill);

}
