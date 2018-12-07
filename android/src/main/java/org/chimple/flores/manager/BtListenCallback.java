package org.chimple.flores.manager;

import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothSocket;

public interface BtListenCallback {
    void GotConnection(BluetoothSocket socket, BluetoothDevice device, final String socketType);
    void CreateSocketFailed(String reason);
    void ListeningFailed(String reason);

}
