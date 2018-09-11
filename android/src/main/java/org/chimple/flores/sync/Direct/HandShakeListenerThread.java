package org.chimple.flores.sync.Direct;

import java.io.IOException;
import java.io.OutputStream;
import java.net.ServerSocket;
import java.net.Socket;

import android.util.Log;

public class HandShakeListenerThread extends Thread {

    private static final String TAG = HandShakeListenerThread.class.getSimpleName();
    private HandShakeListenerCallBack callBack;
    private final ServerSocket mSocket;
    boolean mStopped = false;
    private int listenerErrorSoFarTimes = 0;

    public HandShakeListenerThread(HandShakeListenerCallBack callBack, int port, int trialnum) {
        setName("HandShakeListenerThread");
        Log.i(TAG, "HandShakeListenerThread constructor");
        this.callBack = callBack;
        this.listenerErrorSoFarTimes = trialnum;
        ServerSocket tmp = null;

        try {
            tmp = new ServerSocket(port);
            Log.i(TAG, "HandShakeListenerThread ServerSocket created...");
        } catch (Exception e) {
            Log.i(TAG, "new ServerSocket failed: " + e.toString());
        }
        mSocket = tmp;
    }

    public void run() {
        if (callBack != null) {
            Log.i(TAG, "starting to listen");
            Socket socket = null;
            try {
                if (mSocket != null) {
                    socket = mSocket.accept();
                }
                if (socket != null) {
                    Log.i(TAG, "handshaking connection established");
                    callBack.GotConnection(socket.getInetAddress(), socket.getLocalAddress());
                    OutputStream stream = socket.getOutputStream();
                    String hello = "shakeback";
                    stream.write(hello.getBytes());
                    socket.close();
                } else if (!mStopped) {
                    Log.i(TAG, "HandShakeListenerThread failed: Socket is null");
                    callBack.ListeningFailed("Socket is null", this.listenerErrorSoFarTimes);
                }
            } catch (Exception e) {
                if (!mStopped) {
                    //return failure
                    Log.i(TAG, "HandShakeListenerThread accept socket failed: " + e.toString());
                    callBack.ListeningFailed(e.toString(), this.listenerErrorSoFarTimes);
                }
            }
        }
    }

    public void cleanUp() {
        Log.i(TAG, "cancelled HandShakeListenerThread");
        Log.i(TAG, "cancelled HandShakeListenerThread with socket:" + (mSocket != null));
        mStopped = true;
        try {
            if (mSocket != null) {
                Log.i(TAG, "successfully closed HandShakeListenerThread");
                mSocket.close();
            }
        } catch (IOException e) {
            Log.i(TAG, "closing HandShakeListenerThread socket failed: " + e.toString());
        }
    }
}