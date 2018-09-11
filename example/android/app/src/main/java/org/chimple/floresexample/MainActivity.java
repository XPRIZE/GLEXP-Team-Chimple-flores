package org.chimple.floresexample;

import android.os.Bundle;

import io.flutter.app.FlutterActivity;
import io.flutter.plugins.GeneratedPluginRegistrant;
import android.app.Application;
import android.content.Context;
import android.util.Log;

import org.chimple.flores.db.AppDatabase;
import org.chimple.flores.application.P2PContext;

public class MainActivity extends FlutterActivity {
    private static final String TAG = MainActivity.class.getName();
    private static Context context;
    public static AppDatabase db;

    public static int REGULAR_JOB_TIMINGS_FOR_MIN_LATENCY = 4 * 60 * 1000; // every 4 mins mininum
    public static int REGULAR_JOB_TIMINGS_FOR_PERIOD = 8 * 60 * 1000; // every 8 mins
    public static int IMMEDIATE_JOB_TIMINGS = 5 * 1000; // in next 5 seconds


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        GeneratedPluginRegistrant.registerWith(this);
        initialize();
        context = this;
    }

    private void initialize() {
        Log.d(TAG, "Initializing...");

        Thread initializationThread = new Thread() {
            @Override
            public void run() {
                // Initialize all of the important frameworks and objects
                P2PContext.getInstance().initialize(MainActivity.this);
                //TODO: for now force the creation here
                db = AppDatabase.getInstance(MainActivity.this);

                Log.i(TAG, "app database instance" + String.valueOf(db));

                initializationComplete();
            }
        };

        initializationThread.start();
    }


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
