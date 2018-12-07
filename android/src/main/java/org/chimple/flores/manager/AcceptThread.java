package org.chimple.flores.manager;

import android.bluetooth.BluetoothServerSocket;
import android.bluetooth.BluetoothSocket;
import android.content.Context;
import android.util.Log;

import java.io.IOException;

import static org.chimple.flores.application.P2PContext.LOG_TYPE;
import static org.chimple.flores.manager.BluetoothManager.MY_UUID_INSECURE;
import static org.chimple.flores.manager.BluetoothManager.NAME_INSECURE;
import static org.chimple.flores.manager.BluetoothManager.STATE_CONNECTED;
import static org.chimple.flores.manager.BluetoothManager.STATE_CONNECTING;
import static org.chimple.flores.manager.BluetoothManager.STATE_LISTEN;
import static org.chimple.flores.manager.BluetoothManager.STATE_NONE;

public class AcceptThread extends Thread {
    private static final String TAG = AcceptThread.class.getSimpleName();
    private final BluetoothServerSocket mmServerSocket;
    private final BluetoothManager mManager;
    private final BtListenCallback mCallback;
    private String mSocketType;


    public AcceptThread(Context context, BtListenCallback callback) {
        BluetoothServerSocket tmp = null;
        mSocketType = "Insecure";
        mManager = BluetoothManager.getInstance(context);
        mCallback = callback;
        // Create a new listening server socket
        try {
            tmp = mManager.getmAdapter().listenUsingInsecureRfcommWithServiceRecord(
                    NAME_INSECURE, MY_UUID_INSECURE);

        } catch (IOException e) {
            Log.e(TAG, "Socket Type: " + mSocketType + "listen() failed", e);
            callback.CreateSocketFailed(e.toString());
        }
        mmServerSocket = tmp;
        mManager.setmState(STATE_LISTEN);
    }

    public void run() {
        Log.d(TAG, "Socket Type: " + mSocketType +
                "BEGIN mAcceptThread" + this);

        mManager.notifyUI("Socket Type: " + mSocketType +
                "BEGIN mAcceptThread", " -----> ", LOG_TYPE);
        setName("AcceptThread" + mSocketType);

        BluetoothSocket socket = null;

        // Listen to the server socket if we're not connected
        while (mManager.getmState() != STATE_CONNECTED) {
            try {
                // This is a blocking call and will only return on a
                // successful connection or an exception
                socket = mmServerSocket.accept();

            } catch (IOException e) {
                Log.e(TAG, "Socket Type: " + mSocketType + "- accept() failed", e);
                mManager.notifyUI("Socket Type: " + mSocketType + "- accept() failed" + e.toString(), " -----> ", LOG_TYPE);

                break;
            }

            // If a connection was accepted
            if (socket != null) {
                synchronized (BluetoothManager.class) {
                    switch (mManager.getmState()) {
                        case STATE_LISTEN:
                        case STATE_CONNECTING:
                            // Situation normal. Start the connected thread.
                            mManager.notifyUI("AcceptThread GotConnection:" + socket + " " + socket.getRemoteDevice() + " " + mSocketType, " ------>", LOG_TYPE);
                            mCallback.GotConnection(socket, socket.getRemoteDevice(), mSocketType);
                            break;
                        case STATE_NONE:
                        case STATE_CONNECTED:
                            // Either not ready or already connected. Terminate new socket.
                            try {
                                socket.close();
                            } catch (IOException e) {
                                Log.e(TAG, "Could not close unwanted socket", e);
                                mCallback.ListeningFailed(e.toString());
                            }
                            break;
                    }
                }
            }
        }
        Log.i(TAG, "END mAcceptThread, socket Type: " + mSocketType);

    }

    public void Stop() {
        Log.d(TAG, "Socket Type" + mSocketType + "cancel " + this);
        try {
            mmServerSocket.close();
        } catch (IOException e) {
            Log.e(TAG, "Socket Type" + mSocketType + "close() of server failed", e);
        }
    }
}