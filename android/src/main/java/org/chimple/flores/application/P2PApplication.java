package org.chimple.flores.application;

import android.app.Application;
import android.content.Context;
import android.util.Log;

import org.chimple.flores.db.AppDatabase;

public class P2PApplication extends Application {
    private static final String TAG = P2PApplication.class.getName();
    private static Context context;
    private P2PApplication that;
    public static AppDatabase db;
    // public static boolean addOnceMessages = false;

    public static int REGULAR_JOB_TIMINGS_FOR_MIN_LATENCY = 30 * 1000; // 60 seconds
    public static int REGULAR_JOB_TIMINGS_FOR_PERIOD = 30 * 1000;
    public static int IMMEDIATE_JOB_TIMINGS = 5 * 1000;

    @Override
    public void onCreate() {
        super.onCreate();
        initialize();
        context = this;
        that = this;
    }

    private void initialize() {
        Log.d(TAG, "Initializing...");

        Thread initializationThread = new Thread() {
            @Override
            public void run() {
                // Initialize all of the important frameworks and objects
//                that.createShardProfilePreferences();
                P2PContext.getInstance().initialize(P2PApplication.this);
                // TODO: for now force the creation here
                db = AppDatabase.getInstance(P2PApplication.this);

                Log.i(TAG, "app database instance" + String.valueOf(db));

                initializationComplete();
            }
        };

        initializationThread.start();
    }

//    private void createShardProfilePreferences() {
//        SharedPreferences pref = this.getContext().getSharedPreferences(P2P_SHARED_PREF, 0); // 0 - for private mode
//        SharedPreferences.Editor editor = pref.edit();
//        USERID_UUID = UUID.randomUUID().toString();
//        Log.i(TAG, "created UUID User:" + USERID_UUID);
//        editor.putString("USER_ID", USERID_UUID);
//        editor.putString("DEVICE_ID", UUID.randomUUID().toString());
//        editor.commit(); // commit changes
//    }


    private void initializationComplete() {
        Log.i(TAG, "Initialization complete...");
    }

    public static Context getContext() {
        return context;
    }

    @Override
    protected void attachBaseContext(Context base) {
        super.attachBaseContext(base);
    }
}