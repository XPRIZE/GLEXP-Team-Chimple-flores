package org.chimple.flores.manager;

import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothSocket;
import android.content.Context;
import android.util.Log;

import java.io.IOException;

import static org.chimple.flores.application.P2PContext.LOG_TYPE;
import static org.chimple.flores.manager.BluetoothManager.MY_UUID_INSECURE;
import static org.chimple.flores.manager.BluetoothManager.STATE_CONNECTING;

public class ConnectThread extends Thread {
    private static final String TAG = ConnectThread.class.getSimpleName();
    private final BluetoothManager mManager;
    private final BluetoothSocket mmSocket;
    private final BluetoothDevice mmDevice;
    private final BtCallback mCallback;
    private String mSocketType;


    public ConnectThread(BluetoothDevice device, Context context, BtCallback callback) {
        mmDevice = device;
        mCallback = callback;
        BluetoothSocket tmp = null;
        mSocketType = "Insecure";
        mManager = BluetoothManager.getInstance(context);

        // Get a BluetoothSocket for a connection with the
        // given BluetoothDevice
        try {
            Log.e(TAG, "createInsecureRfcommSocketToServiceRecord: " + MY_UUID_INSECURE);
            tmp = device.createInsecureRfcommSocketToServiceRecord(
                    MY_UUID_INSECURE);

        } catch (Exception e) {
            Log.e(TAG, "Socket Type: " + mSocketType + "create() failed", e);
            callback.PollSocketFailed(e.toString());
        }
        mmSocket = tmp;
        mManager.setmState(STATE_CONNECTING);
    }

    public void run() {
        Log.i(TAG, "BEGIN mConnectThread SocketType:" + mSocketType);
        setName("ConnectThread" + mSocketType);
        mManager.notifyUI("BEGIN mConnectThread SocketType:" + mSocketType +" and device :" + mmDevice.getAddress(), "------->", LOG_TYPE);

        // Always cancel discovery because it will slow down a connection
        mManager.stopDiscovery();

        // Make a connection to the BluetoothSocket
        try {
            // This is a blocking call and will only return on a
            // successful connection or an exception
            mManager.startHandShakeTimer();
            if(mmSocket != null) {
                mmSocket.connect();    
            }            
        } catch (Exception e) {
            // Close the socket
            try {
                mmSocket.close();
            } catch (Exception e2) {
                Log.e(TAG, "unable to close() " + mSocketType +
                        " socket during connection failure", e2);                
            }
            mCallback.ConnectionFailed("Connection Failed");
            return;
        }

        // Reset the ConnectThread because we're done
        synchronized (BluetoothManager.class) {
            mManager.setmConnectThread(null);
        }

        // Start the connected thread
        mCallback.Connected(mmSocket, mmDevice, mSocketType);
    }

    public void Stop() {
        try {
            mmSocket.close();
        } catch (Exception e) {
            Log.e(TAG, "close() of connect " + mSocketType + " socket failed", e);
        }
    }
}
