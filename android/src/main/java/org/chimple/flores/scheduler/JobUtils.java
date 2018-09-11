package org.chimple.flores.scheduler;

import android.app.job.JobInfo;
import android.app.job.JobScheduler;
import android.content.ComponentName;
import android.content.Context;
import android.os.Build;
import android.util.Log;

import static android.content.Context.JOB_SCHEDULER_SERVICE;
import static org.chimple.flores.application.P2PApplication.IMMEDIATE_JOB_TIMINGS;
import static org.chimple.flores.application.P2PApplication.REGULAR_JOB_TIMINGS_FOR_MIN_LATENCY;
import static org.chimple.flores.application.P2PApplication.REGULAR_JOB_TIMINGS_FOR_PERIOD;


public class JobUtils {
    private static final String TAG = JobUtils.class.getName();

    private static boolean isJobRunning = false;

    public synchronized static void scheduledJob(Context context, boolean immediate) {
        if (!isJobRunning()) {
            JobScheduler jobScheduler = (JobScheduler) context.getSystemService(JOB_SCHEDULER_SERVICE);
            boolean isAnyPendingJob = isAnyJobScheduled(context);
            if (isAnyPendingJob) {
                Log.i(TAG, "Cancelling all pending jobs");
                jobScheduler.cancelAll();
            }
            JobInfo.Builder builder = null;
            if(immediate) {
                builder = buildImmediateJob(context);
            } else {
                builder = buildJob(context);
            }

            int status = jobScheduler.schedule(builder.build());
            Log.i(TAG, "Scheduling regular job, Status " + status);
        } else {
            Log.i(TAG, "Job is already running");
        }
    }

    private synchronized static JobInfo.Builder buildJob(Context context) {
        ComponentName serviceComponent = new ComponentName(context, P2PHandShakingJobService.class);

        JobInfo.Builder builder;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            builder = new JobInfo.Builder(0, serviceComponent)
                    .setMinimumLatency(REGULAR_JOB_TIMINGS_FOR_MIN_LATENCY)
                    .setPersisted(true);
        } else {
            builder = new JobInfo.Builder(0, serviceComponent)
                    .setPeriodic(REGULAR_JOB_TIMINGS_FOR_PERIOD)
                    .setPersisted(true);
        }
        return builder;
    }

    private synchronized static JobInfo.Builder buildImmediateJob(Context context) {
        ComponentName serviceComponent = new ComponentName(context, P2PHandShakingJobService.class);

        JobInfo.Builder builder;

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            builder = new JobInfo.Builder(0, serviceComponent)
                    .setMinimumLatency(IMMEDIATE_JOB_TIMINGS)
//                    .setRequiredNetworkType(JobInfo.NETWORK_TYPE_ANY)
                    .setPersisted(false);
        } else {
            builder = new JobInfo.Builder(0, serviceComponent)
                    .setPeriodic(IMMEDIATE_JOB_TIMINGS)
                    .setPersisted(false);
        }
        return builder;
    }

    public synchronized static boolean isAnyJobScheduled(Context context) {
        JobScheduler scheduler = (JobScheduler) context.getSystemService(JOB_SCHEDULER_SERVICE);

        boolean hasBeenScheduled = false;

        for (JobInfo jobInfo : scheduler.getAllPendingJobs()) {
            if (jobInfo.getId() == 0) {
                hasBeenScheduled = true;
                Log.i(TAG, "found scheduled job:" + hasBeenScheduled);
                break;
            }
        }

        return hasBeenScheduled;
    }

    public synchronized static void cancelAllJobs(Context context) {
        JobScheduler scheduler = (JobScheduler) context.getSystemService(JOB_SCHEDULER_SERVICE);
        scheduler.cancel(0);
        scheduler.cancelAll();
    }

    public synchronized static boolean isJobRunning() {
        return isJobRunning;
    }

    public synchronized static void setJobRunning(boolean jobRunning) {
        isJobRunning = jobRunning;
    }
}
