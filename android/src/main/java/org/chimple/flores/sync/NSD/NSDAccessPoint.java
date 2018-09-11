package org.chimple.flores.sync.NSD;

import android.content.Context;
import android.os.Handler;
import android.util.Log;

import java.net.InetAddress;

import org.chimple.flores.sync.Direct.HandShakeListenerCallBack;
import org.chimple.flores.sync.Direct.P2PAccessPoint;
import org.chimple.flores.sync.SyncUtils;

public class NSDAccessPoint implements HandShakeListenerCallBack {

    private static final String TAG = NSDAccessPoint.class.getSimpleName();
    private NSDAccessPoint that = this;

    private Context context;
    private NSDWifiConnectionUpdateCallBack callBack;
    private Handler mHandler = null;

    int lastError = -1;


    // Handlers
    private NSDHandShakingListenerThread mHandShakeListenerThread = null;

    public NSDAccessPoint(Context Context, Handler handler, NSDWifiConnectionUpdateCallBack callBack) {
        this.context = Context;
        this.callBack = callBack;
        this.mHandler = handler;
        this.initialize();
    }

    public int GetLastError() {
        return lastError;
    }


    private void initialize() {
        this.reStartHandShakeListening(0);
    }


    public void cleanUp() {
        Log.i(TAG, "Stop NSDAccessPoint");
        if (mHandShakeListenerThread != null) {
            mHandShakeListenerThread.cleanUp();
            mHandShakeListenerThread = null;
        }
    }


    @Override
    public void GotConnection(InetAddress remote, InetAddress local) {
        final InetAddress remoteTmp = remote;
        final InetAddress localTmp = local;
        Log.i(TAG, "NSD GotConnection remote" + remote);
        Log.i(TAG, "NSD GotConnection local" + local);
        mHandler.post(new Runnable() {
            @Override
            public void run() {
                reStartHandShakeListening(0);
                that.callBack.Connected(remoteTmp, true);
            }
        });
    }

    @Override
    public void ListeningFailed(String reason, int triedTimes) {
        final int trialCountTmp = triedTimes;
        Log.i(TAG, "NSDAccessPoint listening failed: " + reason);
        mHandler.post(new Runnable() {
            @Override
            public void run() {

                if (trialCountTmp < 2) {
                    reStartHandShakeListening((trialCountTmp + 1));
                } else {
                    Log.i(TAG, "NSDAccessPoint listener failed 2 times, starting exit timer");
                    NSDSyncManager.getInstance(that.context).startConnectorsTimer();
                }
            }
        });
    }


    private void reStartHandShakeListening(int trialCountTmp) {
        if (mHandShakeListenerThread != null) {
            mHandShakeListenerThread.cleanUp();
            mHandShakeListenerThread = null;
        }

        int port = NSDConnectionUtils.getPort(this.context);
        Log.i(TAG, "Staring NSDHandShakingListenerThread on port:" + port);
        Log.i(TAG, "Staring NSDHandShakingListenerThread on IpAddress:" + SyncUtils.getWiFiIPAddress(this.context));

        mHandShakeListenerThread = new NSDHandShakingListenerThread(that, port, trialCountTmp);
        mHandShakeListenerThread.start();
    }

    public NSDHandShakingListenerThread getmHandShakeListenerThread() {
        return mHandShakeListenerThread;
    }
}
