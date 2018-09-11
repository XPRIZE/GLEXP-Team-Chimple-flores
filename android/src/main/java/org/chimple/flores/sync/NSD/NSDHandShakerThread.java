package org.chimple.flores.sync.NSD;

import android.os.StrictMode;
import android.util.Log;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.Socket;

public class NSDHandShakerThread extends Thread {
    private static final String TAG = NSDHandShakerThread.class.getSimpleName();
    private NSDHandShakeInitiatorCallBack callBack;
    private final Socket mSocket;
    private String mAddress;
    private int mPort;
    boolean mStopped = false;
    private int triedSoFarTimes = 0;

    public NSDHandShakerThread(NSDHandShakeInitiatorCallBack callBack, String address, int port, int trialnum) {
        setName("NSDHandShakerThread");
        this.mAddress = address;
        this.mPort = port;
        this.callBack = callBack;
        this.mSocket = new Socket();
        this.triedSoFarTimes = trialnum;
        StrictMode.ThreadPolicy policy = new StrictMode.ThreadPolicy.Builder()
                .permitAll().build();
        StrictMode.setThreadPolicy(policy);
    }

    public void run() {
        try {
            if (mSocket != null && callBack != null) {
                mSocket.bind(null);
                mSocket.connect(new InetSocketAddress(mAddress, mPort), 5000);
                Log.i(TAG, "called connect on NSDHandShakerThread socket");
                //return success
                callBack.NSDConnected(mSocket.getInetAddress(), mSocket.getLocalAddress());
                Log.i(TAG, "called connected on NSDHandShakerThread callback");
            }
        } catch (Exception e) {
            Log.i(TAG, "socket connect failed: " + e.toString());
            interrupt();
            try {
                if (mSocket != null) {
                    mSocket.close();
                }
            } catch (IOException ee) {
                Log.i(TAG, "closing socket 2 failed: " + ee.toString());
            }
            if (!mStopped) {
                callBack.NSDConnectionFailed(e.toString(), triedSoFarTimes);
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