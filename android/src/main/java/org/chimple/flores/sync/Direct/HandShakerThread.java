package org.chimple.flores.sync.Direct;

import android.util.Log;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.Socket;

public class HandShakerThread extends Thread {

    private static final String TAG = HandShakerThread.class.getSimpleName();
    private HandShakeInitiatorCallBack callBack;
    private final Socket mSocket;
    private String mAddress;
    private int mPort;
    boolean mStopped = false;
    private int triedSoFarTimes = 0;

    public HandShakerThread(HandShakeInitiatorCallBack callBack, String address, int port, int trialnum) {
        setName("HandShakerThread");
        this.mAddress = address;
        this.mPort = port;
        this.callBack = callBack;
        this.mSocket = new Socket();
        this.triedSoFarTimes = trialnum;
    }

    public void run() {
        if (mSocket != null && callBack != null) {
            try {
                mSocket.bind(null);
                mSocket.connect(new InetSocketAddress(mAddress, mPort), 5000);
                Log.i(TAG, "called connect on HandShakerThread socket");
                //return success
                callBack.Connected(mSocket.getInetAddress(), mSocket.getLocalAddress());
                Log.i(TAG, "called connected on HandShakerThread callback");

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
                    callBack.ConnectionFailed(e.toString(), triedSoFarTimes);
                }
            }
        }
    }

    public void cleanUp() {
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