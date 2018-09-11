package org.chimple.flores.scheduler;

import android.app.job.JobParameters;
import android.app.job.JobService;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.net.wifi.WifiManager;
import android.os.Build;
import android.support.annotation.RequiresApi;
import android.support.v4.content.LocalBroadcastManager;
import android.util.Log;

@RequiresApi(Build.VERSION_CODES.LOLLIPOP)

public class P2PHandShakingJobService extends JobService {
    private static final String TAG = P2PHandShakingJobService.class.getSimpleName();
    private P2PSyncCompletionIntentBroadcastReceiver receiver;

    public static final String P2P_SYNC_RESULT_RECEIVED = "P2P_SYNC_RESULT_RECEIVED";
    public static final String JOB_PARAMS = "JOB_PARAMS";


    private Intent p2pSyncService;

    @Override
    public void onCreate() {
        super.onCreate();
        //startForeground(1,new Notification());
        this.registerP2PSyncCompletionIntentBroadcastReceiver();
        Log.i(TAG, "Service created");
    }

    @Override
    public void onDestroy() {
        this.unregisterP2PSyncCompletionIntentBroadcastReceiver();
        super.onDestroy();
        Log.i(TAG, "Service destroyed");
    }

    /**
     * When the app's MainActivity is created, it starts this service. This is so that the
     * activity and this service can communicate back and forth. See "setUiCallback()"
     */
    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        return START_NOT_STICKY;
    }

    @Override
    public boolean onStartJob(final JobParameters params) {
        // The work that this service "does" is simply wait for a certain duration and finish
        // the job (on another thread).
        if (!JobUtils.isJobRunning()) {
            p2pSyncService = new Intent(getApplicationContext(), P2PIntentService.class);
            p2pSyncService.putExtra(JOB_PARAMS, params);
            getApplicationContext().startService(new Intent(p2pSyncService));
            JobUtils.setJobRunning(true);
            Log.i(TAG, "on start job: " + params.getJobId());
        } else {
            Log.i(TAG, "Job is already running");
        }
        return true;
    }

    @Override
    public boolean onStopJob(JobParameters params) {
        // Stop tracking these job parameters, as we've 'finished' executing.
        Log.i(TAG, "on stop job: " + params.getJobId());
        return true;
    }

    private void unregisterP2PSyncCompletionIntentBroadcastReceiver() {
        if (receiver != null) {
            LocalBroadcastManager.getInstance(this).unregisterReceiver(receiver);
            receiver = null;
            Log.i(TAG, "p2p Sync Completion Receiver unregistered");
        }
    }

    private void registerP2PSyncCompletionIntentBroadcastReceiver() {
        receiver = new P2PSyncCompletionIntentBroadcastReceiver(this);
        IntentFilter filter = new IntentFilter();
        filter.addAction(P2P_SYNC_RESULT_RECEIVED);
        LocalBroadcastManager.getInstance(this).registerReceiver(receiver, filter);
        Log.i(TAG, "p2p Sync Completion Receiver registered");
    }


    /**
     * BroadcastReceiver used to receive Intents fired from the WifiDirectHandler when P2P events occur
     * Used to update the UI and receive communication messages
     */
    public class P2PSyncCompletionIntentBroadcastReceiver extends BroadcastReceiver {
        private P2PHandShakingJobService service;

        public P2PSyncCompletionIntentBroadcastReceiver(P2PHandShakingJobService service) {
            this.service = service;
        }

        @Override
        public void onReceive(Context context, Intent intent) {
            JobParameters params = intent.getExtras().getParcelable(JOB_PARAMS);
            Log.i(TAG, "P2PSyncCompletionIntentBroadcastReceiver ...." + params);
            if (params != null) {
                Log.i(TAG, "on finished job: " + params.getJobId());
                JobUtils.setJobRunning(false);
                JobUtils.cancelAllJobs(context);
                JobUtils.scheduledJob(context, true);
                getApplicationContext().stopService(new Intent(p2pSyncService));
                jobFinished(params, false);
            }
        }
    }
}
