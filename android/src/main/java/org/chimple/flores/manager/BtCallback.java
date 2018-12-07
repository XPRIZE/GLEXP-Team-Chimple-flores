package org.chimple.flores.manager;

import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothSocket;

public interface BtCallback {
    void PollSocketFailed(String reason);
    void ConnectionFailed(String reason);
    void Connected(BluetoothSocket socket, BluetoothDevice device, final String socketType);
    public void HandShakeFailed(String reason, boolean isDisconnectAfterSync);
}

