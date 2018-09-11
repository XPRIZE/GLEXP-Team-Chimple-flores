package org.chimple.flores.sync.NSD;

import android.util.Log;

import java.io.IOException;
import java.io.OutputStream;
import java.net.ServerSocket;
import java.net.Socket;

import org.chimple.flores.sync.Direct.HandShakeListenerCallBack;

public class NSDHandShakingListenerThread extends Thread {
    private static final String TAG = NSDHandShakingListenerThread.class.getSimpleName();
    private HandShakeListenerCallBack callBack;
    private final ServerSocket mSocket;
    boolean mStopped = false;
    private int listenerErrorSoFarTimes = 0;

    public NSDHandShakingListenerThread(HandShakeListenerCallBack callBack, int port, int trialnum) {
        setName("NSDHandShakingListenerThread");
        this.callBack = callBack;
        this.listenerErrorSoFarTimes = trialnum;
        ServerSocket tmp = null;

        try {
            tmp = new ServerSocket(port);
            Log.i(TAG, "NSDHandShakingListenerThread ServerSocket created...");
        } catch (Exception e) {
            Log.i(TAG, "new ServerSocket failed: " + e.toString());
        }
        mSocket = tmp;
    }

    public void run() {
        try {
            if (callBack != null) {
                Log.i(TAG, "starting to listen");
                Socket socket = null;
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
                    Log.i(TAG, "NSDHandShakingListenerThread failed: Socket is null");
                    callBack.ListeningFailed("Socket is null", this.listenerErrorSoFarTimes);
                }
            }
        } catch (Exception e) {
            interrupt();
            if (!mStopped) {
                //return failure
                Log.i(TAG, "NSDHandShakingListenerThread accept socket failed: " + e.toString());
                callBack.ListeningFailed(e.toString(), this.listenerErrorSoFarTimes);
            }
        }
    }

    public void cleanUp() {
        Log.i(TAG, "cancelled NSDHandShakingListenerThread");
        mStopped = true;
        try {
            if (mSocket != null) {
                Log.i(TAG, "successfully closed NSDHandShakingListenerThread");
                mSocket.close();
            }
        } catch (IOException e) {
            Log.i(TAG, "closing NSDHandShakingListenerThread socket failed: " + e.toString());
        }
    }
}