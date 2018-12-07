package org.chimple.floresexample;

import android.os.Bundle;

import io.flutter.app.FlutterActivity;
import io.flutter.plugins.GeneratedPluginRegistrant;
import android.app.Application;
import android.content.Context;
import android.util.Log;
import android.content.SharedPreferences;
import org.chimple.flores.db.AppDatabase;
import org.chimple.flores.multicast.MulticastManager;
import org.chimple.flores.manager.BluetoothManager;
import org.chimple.flores.application.P2PContext;
import static org.chimple.flores.application.P2PContext.CLEAR_CONSOLE_TYPE;
import static org.chimple.flores.application.P2PContext.refreshDevice;


public class MainActivity extends FlutterActivity {
    
    private static MainActivity activity;
    private static final String TAG = MainActivity.class.getName();    
    private static Context context;
    public static AppDatabase db;
    public static MulticastManager manager;
    public static BluetoothManager BluetoothManager;

    public static final String SHARED_PREF = "shardPref";
    public static final String USER_ID = "USER_ID";
    public static final String DEVICE_ID = "DEVICE_ID";
    public static final String NEW_MESSAGE_ADDED = "NEW_MESSAGE_ADDED";
    public static final String REFRESH_DEVICE = "REFRESH_DEVICE";
    public static final String messageEvent = "message-event";
    public static final String uiMessageEvent = "ui-message-event";
    public static final String newMessageAddedOnDevice = "new-message-added-event";
    public static final String refreshDevice = "refresh-device-event";
    public static final String MULTICAST_IP_ADDRESS = "232.1.1.2";
    public static final String MULTICAST_IP_PORT = "4461";

    public static final String CONSOLE_TYPE = "console";
    public static final String LOG_TYPE = "log";
    public static final String CLEAR_CONSOLE_TYPE = "clear-console";



    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        GeneratedPluginRegistrant.registerWith(this);
        activity = this;
        initialize();
        context = this;
    }
    
    @Override
    protected void onStop() {
        super.onStop();        
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        manager.onCleanUp();
        BluetoothManager.onCleanUp();        
    }

    private void initialize() {
        Log.d(TAG, "Initializing...");

        Thread initializationThread = new Thread() {
            @Override
            public void run() {
                // Initialize all of the important frameworks and objects
                P2PContext.getInstance().initialize(MainActivity.activity);
                db = AppDatabase.getInstance(MainActivity.activity);
                manager = MulticastManager.getInstance(MainActivity.activity);
                BluetoothManager = BluetoothManager.getInstance(MainActivity.activity);
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

    public static String getLoggedInUser() {
        SharedPreferences pref = getContext().getSharedPreferences(SHARED_PREF, 0);
        String userId = pref.getString("USER_ID", null); // getting String
        return userId;
    }


    public static String getCurrentDevice() {
        SharedPreferences pref = getContext().getSharedPreferences(SHARED_PREF, 0);
        String deviceId = pref.getString("DEVICE_ID", null); // getting String
        return deviceId;
    }


    @Override
    protected void attachBaseContext(Context base) {
        super.attachBaseContext(base);
    }
}
