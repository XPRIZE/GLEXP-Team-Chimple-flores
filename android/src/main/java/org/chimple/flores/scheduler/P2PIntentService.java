package org.chimple.flores.scheduler;

import android.app.Service;
import android.app.job.JobParameters;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.IBinder;
import android.os.Looper;
import android.os.Message;

import android.support.annotation.Nullable;
import android.util.Log;

import java.util.UUID;

import org.chimple.flores.sync.NSD.NSDSyncManager;
import org.chimple.flores.sync.Direct.P2PSyncManager;
import org.chimple.flores.sync.SyncUtils;


import static org.chimple.flores.scheduler.P2PHandShakingJobService.JOB_PARAMS;
import static org.chimple.flores.sync.Direct.P2PSyncManager.P2P_SHARED_PREF;

public class P2PIntentService extends Service {
    private static final String TAG = P2PIntentService.class.getSimpleName();
    private volatile Looper mServiceLooper;
    private volatile ServiceHandler mServiceHandler;
    private P2PSyncManager p2pSyncManager;
    private NSDSyncManager nsdSyncManager;
    private JobParameters currentJobParams;
    ;
    private P2PIntentService that = this;

    public P2PIntentService() {
        super();
    }

    private final class ServiceHandler extends Handler {
        public ServiceHandler(Looper looper) {
            super(looper);
        }

        @Override
        public void handleMessage(Message msg) {
            onHandleIntent((Intent) msg.obj);
        }
    }

    @Override
    public void onCreate() {
        super.onCreate();
        HandlerThread thread = new HandlerThread("IntentService");
        thread.start();
        mServiceLooper = thread.getLooper();
        boolean isInternetConnected = SyncUtils.isWifiConnected(this);
        mServiceHandler = new ServiceHandler(mServiceLooper);
        if (isInternetConnected) {
            this.nsdSyncManager = NSDSyncManager.getInstance(this.getApplicationContext());
        } else {
            this.p2pSyncManager = P2PSyncManager.getInstance(this.getApplicationContext());
        }
    }


    @Override
    public void onStart(Intent intent, int startId) {
        currentJobParams = intent.getExtras().getParcelable(JOB_PARAMS);
        Log.i(TAG, currentJobParams.toString());
        Message msg = mServiceHandler.obtainMessage();
        msg.arg1 = startId;
        msg.obj = intent;
        mServiceHandler.sendMessage(msg);
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        onStart(intent, startId);
        return START_NOT_STICKY;
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        Log.i(TAG, "Destroying P2PSync Manager");
        if (this.p2pSyncManager != null) {
            this.p2pSyncManager.onDestroy();
            this.p2pSyncManager = null;
        }

        if (this.nsdSyncManager != null) {
            this.nsdSyncManager.onDestroy();
            this.nsdSyncManager = null;
        }

        mServiceLooper.quit();
    }

    @Override
    public IBinder onBind(Intent intent) {
        // Auto-generated method stub
        return null;
    }

    protected void onHandleIntent(@Nullable Intent intent) {
        Log.i(TAG, "do actual work");

        boolean isInternetConnected = SyncUtils.isWifiConnected(this);
        Log.i(TAG, "isInternetConnected: " + isInternetConnected);

        SharedPreferences pref = getApplicationContext().getSharedPreferences(P2P_SHARED_PREF, 0); // 0 - for private mode
        SharedPreferences.Editor editor = pref.edit();

        if (isInternetConnected && this.nsdSyncManager != null) {
            editor.putBoolean("IS_P2P", false);
            editor.commit();
            Log.i(TAG, "should start NSD Sync");
            this.nsdSyncManager.execute(this.currentJobParams);
        } else {
            editor.putBoolean("IS_P2P", true);
            editor.commit();
            Log.i(TAG, "should start P2P Sync");
            if (this.p2pSyncManager != null) {
                this.p2pSyncManager.execute(this.currentJobParams);
            }
        }

    }
}
