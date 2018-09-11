package org.chimple.flores.sync.NSD;

import java.net.InetAddress;

public interface NSDHandShakeInitiatorCallBack {
    public void NSDConnected(InetAddress remote, InetAddress local);
    public void NSDConnectionFailed(String reason, int trialCount);

}
