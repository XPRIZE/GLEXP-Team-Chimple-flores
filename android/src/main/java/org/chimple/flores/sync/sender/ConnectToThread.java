package org.chimple.flores.sync.sender;

import android.util.Log;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.Socket;

public class ConnectToThread extends Thread {
    private static final String TAG = ConnectToThread.class.getSimpleName();

    private CommunicationCallBack callBack;
    private final Socket mSocket;
    private String mAddress;
    private int mPort;
    boolean mStopped = false;

    public ConnectToThread(CommunicationCallBack callBack, String address, int port) {
        setName("ConnectToThread");
        this.mAddress = address;
        this.mPort = port;
        this.callBack = callBack;
        this.mSocket = new Socket();

    }

    public void run() {
        Log.i(TAG, "Starting to connect in ConnectToThread");
        if (this.mSocket != null && this.callBack != null) {
            try {
                mSocket.bind(null);
                mSocket.connect(new InetSocketAddress(mAddress, mPort), 5000);
                //return success
                callBack.Connected(mSocket);
            } catch (IOException e) {
                Log.i(TAG, "socket connect failed: " + e.toString());
                try {
                    if (mSocket != null) {
                        mSocket.close();
                    }
                } catch (IOException ee) {
                    Log.i(TAG, "closing socket 2 failed: " + ee.toString());
                }
                if (!mStopped) {
                    callBack.ConnectionFailed(e.toString());
                }
            }
        }
    }

    public void Stop() {
        mStopped = true;
        try {
            if (mSocket != null) {
                mSocket.close();
            }
        } catch (IOException e) {
            Log.i(TAG, "closing socket failed: " + e.toString());
        }
    }
}