package org.chimple.flores.sync.Direct;

import java.net.InetAddress;

public interface HandShakeInitiatorCallBack {
    public void Connected(InetAddress remote, InetAddress local);
    public void ConnectionFailed(String reason, int trialCount);

}
