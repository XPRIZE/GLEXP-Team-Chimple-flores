package org.chimple.flores.manager;

import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothSocket;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.AsyncTask;
import android.os.CountDownTimer;
import android.os.Handler;
import android.support.v4.content.LocalBroadcastManager;
import android.util.Log;


import com.google.common.base.Splitter;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Lists;

import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.collections4.ListUtils;
import org.apache.commons.collections4.Predicate;
import org.apache.commons.lang3.StringUtils;
import org.chimple.flores.application.P2PContext;
import org.chimple.flores.db.DBSyncManager;
import org.chimple.flores.db.P2PDBApiImpl;
import org.chimple.flores.db.entity.HandShakingInfo;
import org.chimple.flores.db.entity.HandShakingMessage;
import org.chimple.flores.db.entity.P2PSyncInfo;
import org.chimple.flores.db.entity.SyncInfoItem;
import org.chimple.flores.db.entity.SyncInfoMessage;
import org.chimple.flores.db.entity.SyncInfoRequestMessage;

import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Random;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import org.chimple.flores.AbstractManager;

import static org.chimple.flores.application.P2PContext.CLEAR_CONSOLE_TYPE;
import static org.chimple.flores.application.P2PContext.CONSOLE_TYPE;
import static org.chimple.flores.application.P2PContext.LOG_TYPE;
import static org.chimple.flores.application.P2PContext.NEW_MESSAGE_ADDED;
import static org.chimple.flores.application.P2PContext.bluetoothMessageEvent;
import static org.chimple.flores.application.P2PContext.newMessageAddedOnDevice;
import static org.chimple.flores.application.P2PContext.refreshDevice;
import static org.chimple.flores.application.P2PContext.uiMessageEvent;
import static org.chimple.flores.db.AppDatabase.SYNC_NUMBER_OF_LAST_MESSAGES;

public class BluetoothManager extends AbstractManager implements BtListenCallback, BtCallback, BluetoothStatusChanged {
    private static final String TAG = BluetoothManager.class.getSimpleName();
    private Context context;
    private static BluetoothManager instance;
    private P2PDBApiImpl p2PDBApiImpl;
    private DBSyncManager dbSyncManager;
    private Map<String, HandShakingMessage> handShakingMessagesInCurrentLoop = new ConcurrentHashMap<>();
    private Set<String> allSyncInfosReceived = new HashSet<String>();
    private BluetoothAdapter mAdapter;
    private final AtomicBoolean isDiscoverying = new AtomicBoolean(false);    
    private final AtomicInteger mState = new AtomicInteger(STATE_NONE);
    private final AtomicInteger mNewState = new AtomicInteger(STATE_NONE);    
    List<String> peerDevices = Collections.synchronizedList(new ArrayList<String>());
    List<String> supportedDevices = Collections.synchronizedList(new ArrayList<String>());
    private Handler mHandler = null;
    private int bluetoothState = -1;
    private final AtomicBoolean isSyncStarted = new AtomicBoolean(false);
    // private final AtomicBoolean anyPeersFound = new AtomicBoolean(false); 


    public static final long START_ALL_BLUETOOTH_ACTIVITY = 5 * 1000;
    public static final long STOP_ALL_BLUETOOTH_ACTIVITY = 5 * 1000;
    public static final long LONG_TIME_ALARM = 1 * 60 * 1000; // 2 min cycle
    private static final int START_HANDSHAKE_TIMER = 15 * 1000; // 15 sec
    private static final int STOP_DISCOVERY_TIMER = 5 * 1000; // 5 sec
    private static final int MAX_STOP_DISCOVERY_TIMER = 8 * 1000; // 8 sec
    private CountDownTimer startBluetoothDiscoveryTimer;
    private CountDownTimer handShakeFailedTimer;
    private CountDownTimer nextRoundTimer;
    private CountDownTimer startAllBlueToothActivityTimer;
    private CountDownTimer stopAllBlueToothActivityTimer;

    private CountDownTimer repeatSyncActivityTimer;
    private CountDownTimer disconnectTimer;

    private static final int DISCONNECT_TIMER = 2 * 1000; // 2 sec
    private static final int POLLING_TIMER = 1 * 1000; // 1 sec


    // Constants that indicate the current connection state
    public static final int STATE_NONE = 0;       // we're doing nothing
    public static final int STATE_LISTEN = 1;     // now listening for incoming connections
    public static final int STATE_CONNECTING = 2; // now initiating an outgoing connection
    public static final int STATE_CONNECTED = 3;  // now connected to a remote device

    public static final String NAME_INSECURE = "BluetoothChatInsecure";
    // Unique UUID for this application
    public static final UUID MY_UUID_INSECURE =
            UUID.fromString("8ce255c0-200a-11e0-ac64-0800200c9a66");


    public static final int MESSAGE_READ = 0x11;
    public static final int MESSAGE_WRITE = 0x22;
    public static final int SOCKET_DISCONNEDTED = 0x33;


    private AcceptThread mInsecureAcceptThread;
    private ConnectThread mConnectThread;
    private ConnectedThread mConnectedThread;
    private int pollingIndex = 0;
    private String connectedAddress = "";

    public static BluetoothManager getInstance(Context context) {
        if (instance == null) 
        {
            synchronized (BluetoothManager.class) {
                Log.d(TAG, "BluetoothManager initialize");
                instance = new BluetoothManager(context);
                instance.mHandler = new Handler(context.getMainLooper());
                instance.dbSyncManager = DBSyncManager.getInstance(context);
                instance.p2PDBApiImpl = P2PDBApiImpl.getInstance(context);
                instance.mAdapter = BluetoothAdapter.getDefaultAdapter();
                instance.mState.set(STATE_NONE);
                instance.mNewState.set(instance.mState.get());
                instance.registerReceivers();
                instance.broadCastRefreshDevice();
                if (instance.mAdapter != null && !instance.mAdapter.isEnabled()) {
                    instance.mAdapter.enable(); 
                }
                instance.mHandler.post(new Runnable() {
                    @Override
                    public void run() {
                        instance.handShakeFailedTimer = new CountDownTimer(START_HANDSHAKE_TIMER, 5000) {
                            @Override
                            public void onTick(long millisUntilFinished) {
                                instance.notifyUI("startHandShakeTimer ticking", " ------>", LOG_TYPE);
                            }

                            @Override
                            public void onFinish() {
                                instance.notifyUI("startHandShakeTimer TimeOut", " ------>", LOG_TYPE);
                                instance.HandShakeFailed("TimeOut", false);
                            }
                        };
                    }
                });

                instance.mHandler.post(new Runnable() {
                    @Override
                    public void run() {
                        Random r = new Random();
                        int randomInt = r.nextInt(MAX_STOP_DISCOVERY_TIMER - STOP_DISCOVERY_TIMER) + STOP_DISCOVERY_TIMER;
                        instance.startBluetoothDiscoveryTimer = new CountDownTimer(randomInt, 1000) {
                            @Override
                            public void onTick(long millisUntilFinished) {
                                if (!instance.isDiscoverying.get()) {
                                     instance.doDiscovery();
                                }
                            }

                            @Override
                            public void onFinish() {
                                instance.notifyUI("STOP_DISCOVERY_TIMER ...finished ", " ----->", LOG_TYPE);
                                Log.d(TAG, "started ACTION_DISCOVERY_FINISHED");                                
                                instance.peerDevices.addAll(instance.supportedDevices);
                                instance.notifyUI("startDiscoveryTimer ...ACTION_DISCOVERY_FINISHED: found peers:" + instance.peerDevices.size(), "---------->", LOG_TYPE);                                
                                instance.stopDiscovery();
                                instance.startNextPolling();
                            }
                        };
                    }
                });


                instance.mHandler.post(new Runnable() {
                    @Override
                    public void run() {
                        instance.nextRoundTimer = new CountDownTimer(POLLING_TIMER, 500) {
                            public void onTick(long millisUntilFinished) {
                                // not using
                            }

                            public void onFinish() {
                                instance.DoNextPollingRound();
                            }
                        };
                    }
                });
                
                instance.createRepeatSyncActivityTimer(LONG_TIME_ALARM);

                instance.mHandler.post(new Runnable() {
                    @Override
                    public void run() {
                        instance.startAllBlueToothActivityTimer = new CountDownTimer(START_ALL_BLUETOOTH_ACTIVITY, 1000) {
                            @Override
                            public void onTick(long millisUntilFinished) {
                                Log.d(TAG, "startAllBlueToothActivityTimer ticking ....");
                            }

                            @Override
                            public void onFinish() {
                                if (instance.isBluetoothEnabled()) {
                                    instance.startAcceptListener();
                                    Log.d(TAG, "restarting repeatSyncActivityTimer timer ....");
                                    if (instance.repeatSyncActivityTimer != null) {
                                        instance.repeatSyncActivityTimer.cancel();
                                        instance.repeatSyncActivityTimer.start();
                                    }
                                }
                            }
                        };
                    }
                });

                

                instance.mHandler.post(new Runnable() {
                    @Override
                    public void run() {
                        instance.stopAllBlueToothActivityTimer = new CountDownTimer(STOP_ALL_BLUETOOTH_ACTIVITY, 1000) {
                            @Override
                            public void onTick(long millisUntilFinished) {

                            }

                            @Override
                            public void onFinish() {
                                if (instance.isBluetoothEnabled()) {
                                    instance.stopAcceptListener();                                    
                                    instance.Stop();
                                }
                            }
                        };
                    }
                });                        
            }  

            if(instance.isBluetoothEnabled() && !instance.isConnected.get()) {
                instance.startAcceptListener();
            }            
        }
        return instance;
    }    


    private final BroadcastReceiver btBrowdCastReceiver = new BroadcastReceiver() {
        @Override
         public void onReceive(Context context, Intent intent) {
            String action = intent.getAction(); 
            Log.d(TAG, "BtBrowdCastReceiver action received:" + action);
            // if (BluetoothAdapter.ACTION_DISCOVERY_STARTED.equals(action)) {
            //     Log.d(TAG, "started ACTION_DISCOVERY_STARTED");
            //     notifyUI("startDiscoveryTimer ...ACTION_DISCOVERY_STARTED", "---------->", LOG_TYPE);
            // } else if (BluetoothAdapter.ACTION_SCAN_MODE_CHANGED.equals(action)) {
            //     int mode = intent.getIntExtra(BluetoothAdapter.EXTRA_SCAN_MODE, BluetoothAdapter.ERROR);
            //     Log.d(TAG, "Bluetooth ACTION_SCAN_MODE_CHANGED");
            //     if (instance != null) {                    
            //         instance.BluetoothStateChanged(mode);
            //     }
            // } else if (BluetoothDevice.ACTION_FOUND.equals(action)) {
            //     // Get the BluetoothDevice object from the Intent
            //     BluetoothDevice device = intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE);
            //     // If it's already paired, skip it, because it's been listed already
            //     Log.d(TAG, "Adding device to list: " + device.getName() + "\n" + device.getAddress());
            //     notifyUI("Adding device to list: " + device.getName() + "\n" + device.getAddress(), " ----> ", LOG_TYPE);
            //     peerDevices.add(device.getAddress());
            //     // When discovery is finished, change the Activity title
            // } else if (BluetoothAdapter.ACTION_DISCOVERY_FINISHED.equals(action)) {
            //     Log.d(TAG, "started ACTION_DISCOVERY_FINISHED");
            //     instance.notifyUI("startDiscoveryTimer ...ACTION_DISCOVERY_FINISHED: found peers:" + instance.peerDevices.size(), "---------->", LOG_TYPE);                
            //     instance.peerDevices.addAll(instance.supportedDevices);
            //     instance.notifyUI("adding all supported devices as no peer found ... : found peers:" + instance.peerDevices.size(), "---------->", LOG_TYPE);
            //     instance.stopDiscovery();
            //     instance.startNextPolling();
            // }

            if (BluetoothAdapter.ACTION_SCAN_MODE_CHANGED.equals(action)) {
                int mode = intent.getIntExtra(BluetoothAdapter.EXTRA_SCAN_MODE, BluetoothAdapter.ERROR);
                Log.d(TAG, "Bluetooth ACTION_SCAN_MODE_CHANGED");
                if (instance != null) {                    
                    instance.BluetoothStateChanged(mode);
                }
            }
        } 
    };
    
    private void createDisconnectTimer() {
        instance.mHandler.post(new Runnable() {
            @Override
            public void run() {
                Log.d(TAG, "in createDisconnectTimer 111: " + (instance.disconnectTimer == null));
                if(instance.disconnectTimer == null) {
                        instance.disconnectTimer = new CountDownTimer(DISCONNECT_TIMER, 1000) {
                        @Override
                        public void onTick(long millisUntilFinished) {
                            Log.d(TAG, "disconnectTimer ticking ...");
                        }

                        @Override
                        public void onFinish() {
                            Log.d(TAG, "disconnectTimer finished staring disconnect from...." + instance.connectedAddress);
                            instance.disconnectTimer = null;
                            instance.notifyUI("BLUETOOTH SYNC COMPLETED ....", " ------> ", LOG_TYPE);
                            instance.startNextDeviceToSync();                                                    
                        }
                    }.start();                    
                }            
            }
        });
    }

    private void startSync(boolean isImmediate) {
        synchronized(BluetoothManager.class) {
        Log.d(TAG, "in startSync : should start sync:" + !instance.isSyncStarted.get());            
            instance.Start(instance.pollingIndex);
        }        
    }


    public boolean isBluetoothEnabled() {
        return instance.bluetoothState != BluetoothAdapter.SCAN_MODE_NONE && !instance.isConnected.get() && instance.mAdapter != null && instance.mAdapter.isEnabled();
    }

    private void startBluetoothBased() {
        synchronized(BluetoothManager.class) {
            Log.d(TAG, "is network connected:" + instance.isConnected.get());
            if (instance.isConnected.get())
            {
                Log.d(TAG, "still connected with network - no blue tooth");
                instance.stopBlueToothConnections();             
            } 
            else 
            {
                if (instance.isBluetoothEnabled()) {                    
                    instance.startBluetoothConnections();
                } else {
                    instance.stopBlueToothConnections();          
                }                
            } 
        }
    }

    private void startBluetoothConnections() {
        Log.d(TAG, "network is not connected and  bluetooth is enabled ...starting all bluetooth activity");
        if (stopAllBlueToothActivityTimer != null) {
            stopAllBlueToothActivityTimer.cancel();
        }
        if(instance.startAllBlueToothActivityTimer != null) {
            instance.startAllBlueToothActivityTimer.cancel();
            instance.startAllBlueToothActivityTimer.start();    
        }
    }



    public void Start(final int index) {
        synchronized (BluetoothManager.class) {
                AsyncTask.execute(new Runnable() {
                    @Override
                    public void run() {
                        instance.isSyncStarted.set(true);
                        instance.supportedDevices = p2PDBApiImpl.fetchAllSyncedDevices();
                        List<String> allS = new ArrayList<String>();
                        allS.addAll(supportedDevices);

                        Iterator<String>it = allS.iterator();
                        while(it.hasNext()) {
                            String d = (String) it.next();
                            notifyUI("supportedDevices:" + d, " ------> ", LOG_TYPE);
                        }        
                        
                        pollingIndex = index;
                        instance.startListener();
                        if (instance.peerDevices != null && instance.peerDevices.size() == 0) {
                            notifyUI("startDiscoveryTimer ...", "---------->", LOG_TYPE);
                            instance.startDiscoveryTimer();
                        } 

                        notifyUI("Start All ... with peers:" + instance.peerDevices.size(), "---------->", LOG_TYPE);                                                
                    }

                });            
        }
    }

    public void onCleanUp() {
        
        instance.unRegisterReceivers();

        if(instance.startBluetoothDiscoveryTimer != null) {
            instance.startBluetoothDiscoveryTimer.cancel();
            instance.startBluetoothDiscoveryTimer = null;
        }

        if(instance.handShakeFailedTimer != null) {
            instance.handShakeFailedTimer.cancel();
            instance.handShakeFailedTimer = null;
        }

        if(instance.nextRoundTimer != null) {
            instance.nextRoundTimer.cancel();
            instance.nextRoundTimer = null;
        }

        if(instance.startAllBlueToothActivityTimer != null) {
            instance.startAllBlueToothActivityTimer.cancel();
            instance.startAllBlueToothActivityTimer = null;
        }

        if(instance.stopAllBlueToothActivityTimer != null) {
            instance.stopAllBlueToothActivityTimer.cancel();
            instance.stopAllBlueToothActivityTimer = null;
        }

        if(instance.repeatSyncActivityTimer != null) {
            instance.repeatSyncActivityTimer.cancel();
            instance.repeatSyncActivityTimer = null;
        }

        if(instance.disconnectTimer != null) {
            instance.disconnectTimer.cancel();
            instance.disconnectTimer = null;
        }

        instance.Stop();

        instance = null;    
    }

    public void Stop() {        
        // Cancel the thread that completed the connection
        instance.reset();
        instance.stopDiscovery();

        if (instance.handShakeFailedTimer != null) {
            instance.handShakeFailedTimer.cancel();
        }

       if (instance.startBluetoothDiscoveryTimer != null) {
           instance.startBluetoothDiscoveryTimer.cancel();
       }


        if (instance.nextRoundTimer != null) {
            instance.nextRoundTimer.cancel();
        }

        if (startAllBlueToothActivityTimer != null) {
            startAllBlueToothActivityTimer.cancel();
        }

        if (stopAllBlueToothActivityTimer != null) {
            stopAllBlueToothActivityTimer.cancel();
        }

        instance.stopRepeatSyncActivityTimer();

        instance.notifyUI("Stop All....", " ----->", LOG_TYPE);
    }

    public void stopDiscovery() {
       if (startBluetoothDiscoveryTimer != null) {
           startBluetoothDiscoveryTimer.cancel();
       }

        instance.isDiscoverying.set(false);
        // if (instance != null && instance.mAdapter.isDiscovering()) {
        //     instance.mAdapter.cancelDiscovery();
        // }
    }

    private void unRegisterReceivers() {
        Log.d(TAG, "UNREGISTERED BLUETOOTH RECEIVERS ....");     
        if(btBrowdCastReceiver != null) {
            Log.d(TAG, "UNREGISTERED BLUETOOTH RECEIVERS .... btBrowdCastReceiver");     
            instance.context.unregisterReceiver(btBrowdCastReceiver);                        
        }

        if (newMessageAddedReceiver != null) {
            Log.d(TAG, "UNREGISTERED BLUETOOTH RECEIVERS .... newMessageAddedReceiver");     
            LocalBroadcastManager.getInstance(instance.context).unregisterReceiver(newMessageAddedReceiver);         
        }

        if (refreshDeviceReceiver != null) {
            Log.d(TAG, "UNREGISTERED BLUETOOTH RECEIVERS .... refreshDeviceReceiver");     
            LocalBroadcastManager.getInstance(instance.context).unregisterReceiver(refreshDeviceReceiver);            
        }

        if (mMessageEventReceiver != null) {
            Log.d(TAG, "UNREGISTERED BLUETOOTH RECEIVERS .... mMessageEventReceiver");     
            LocalBroadcastManager.getInstance(instance.context).unregisterReceiver(mMessageEventReceiver);            
        }          
    }


    private void registerReceivers() {        
        IntentFilter filter = new IntentFilter();
        filter.addAction(BluetoothAdapter.ACTION_SCAN_MODE_CHANGED);
        this.context.registerReceiver(btBrowdCastReceiver, filter);        

        Log.d(TAG, "REGISTERED BLUETOOTH RECEIVERS ....");     

        LocalBroadcastManager.getInstance(instance.context).registerReceiver(mMessageEventReceiver, new IntentFilter(bluetoothMessageEvent));           
        LocalBroadcastManager.getInstance(instance.context).registerReceiver(newMessageAddedReceiver, new IntentFilter(newMessageAddedOnDevice));
        LocalBroadcastManager.getInstance(instance.context).registerReceiver(refreshDeviceReceiver, new IntentFilter(refreshDevice));        
    }

    private void startNextPolling() {

        if (instance.nextRoundTimer != null) {
            instance.notifyUI("startNextPolling ...", " ------>", LOG_TYPE);
            instance.nextRoundTimer.cancel();
            instance.nextRoundTimer.start();
        }
    }


    private void DoNextPollingRound() {
        Log.d(TAG, "finding next to sync with .....");
        String nextDevice = getNextToSync();
        if (nextDevice != null) {
            instance.notifyUI("Starting Connection with: " + nextDevice, " ----->", LOG_TYPE);
            Log.d(TAG, "Starting Connection with: " + nextDevice);
            instance.connectedAddress = nextDevice;
            BluetoothDevice device = instance.mAdapter.getRemoteDevice(nextDevice.trim());
            instance.isSyncStarted.set(true);
            instance.connect(device);

            if (instance.nextRoundTimer != null) {
                instance.nextRoundTimer.cancel();
            }            
        } else {            
            Log.d(TAG, "DoNextPollingRound -> isSyncStarted false");
            instance.startAgain();            
        }
    }

    public synchronized void connect(BluetoothDevice device) {
        Log.d(TAG, "start connect to: " + device);
        notifyUI("start connect to: " + device, " ----->", LOG_TYPE);

        // Cancel any thread attempting to make a connection
        if (mState.get() == STATE_CONNECTING) {
            if (mConnectThread != null) {
                mConnectThread.Stop();
                mConnectThread = null;
            }
        }

        // Cancel any thread currently running a connection
        if (mConnectedThread != null) {
            mConnectedThread.Stop();
            mConnectedThread = null;
        }

        // Start the thread to connect with the given device
        mConnectThread = new ConnectThread(device, context, instance);
        mConnectThread.start();
    }

    private void removeDuplicates() {
        synchronized(BluetoothManager.class) {
            if(instance.peerDevices != null) {
                Set<String> hs = new HashSet<>();
                hs.addAll(instance.peerDevices);
                instance.peerDevices.clear(); 
                instance.peerDevices.addAll(hs);
            }
        }
    }

    private synchronized String getNextToSync() {
        String ret = null;
        
        if (peerDevices != null && peerDevices.size() > 0) {
            instance.removeDuplicates();
            String myAddress = getBluetoothMacAddress();
            List<String> sDevices = new ArrayList<String>();
            sDevices.addAll(supportedDevices);
            notifyUI("My address:" + myAddress, " ------>", CONSOLE_TYPE);
            if (myAddress != null && peerDevices.contains(myAddress)) {
                peerDevices.remove(myAddress);
                sDevices.remove(myAddress);
            }

            for(int i = 0; i < peerDevices.size(); i++) {
                Log.d(TAG, "peer contains:" + peerDevices.get(i));
            }

            for(int i = 0; i < sDevices.size(); i++) {
                Log.d(TAG, "support devices contains:" + sDevices.get(i));
            }

            List<String> blueToothDevices = (List<String>) CollectionUtils.intersection(peerDevices, sDevices);
            if (blueToothDevices.size() > 0) {
                pollingIndex = pollingIndex + 1;
                if (pollingIndex >= blueToothDevices.size()) {
                    pollingIndex = 0;
                }

                if (ret == null && pollingIndex >= 0 && pollingIndex < blueToothDevices.size()) {
                    ret = blueToothDevices.get(pollingIndex);
                    Log.d(TAG, "polling index: " + pollingIndex + ", ret: " + ret + ", size: " + blueToothDevices.size());
                    notifyUI("polling index: " + pollingIndex + ", ret: " + ret + ", size: " + blueToothDevices.size(), " ------>", CONSOLE_TYPE);
                }
            }
        }

        Log.d(TAG, "whom we are sync with:" + ret);


        return ret;
    }

    private BluetoothManager(Context context) {
        super(context);
        this.context = context;
    }

    public int getmState() {
        return mState.get();
    }

    public void setmState(int mState) {
        this.mState.set(mState);
    }

    public int getmNewState() {
        return mNewState.get();
    }

    public void setmNewState(int mNewState) {
        this.mNewState.set(mNewState);
    }

    public AcceptThread getmInsecureAcceptThread() {
        return mInsecureAcceptThread;

    }

    public void setmInsecureAcceptThread(AcceptThread mInsecureAcceptThread) {
        this.mInsecureAcceptThread = mInsecureAcceptThread;
    }

    public ConnectThread getmConnectThread() {
        return mConnectThread;
    }

    public void setmConnectThread(ConnectThread mConnectThread) {
        this.mConnectThread = mConnectThread;
    }

    public ConnectedThread getmConnectedThread() {
        return mConnectedThread;
    }

    public void setmConnectedThread(ConnectedThread mConnectedThread) {
        this.mConnectedThread = mConnectedThread;
    }

    public P2PDBApiImpl getP2PDBApiImpl() {
        return p2PDBApiImpl;
    }

    public DBSyncManager getDbSyncManager() {
        return dbSyncManager;
    }

    public BluetoothAdapter getmAdapter() {
        return mAdapter;
    }

    private void reset() {
        Log.d(TAG, "in startAcceptListener .... ");    
        if (mConnectThread != null) {
            mConnectThread.Stop();
            mConnectThread = null;
            notifyUI("mConnectThread stopped", " ------>", LOG_TYPE);
            Log.d(TAG, "mConnectThread stopped");
        }

        // Cancel any thread currently running a connection
        if (mConnectedThread != null) {
            mConnectedThread.Stop();
            mConnectedThread = null;
            notifyUI("mConnectedThread stopped", " ------>", LOG_TYPE);
            Log.d(TAG, "mConnectedThread stopped");
        }
    }

    @Override
    public void Connected(BluetoothSocket socket, BluetoothDevice device, String socketType) {
        synchronized (BluetoothManager.class) {
            Log.d(TAG, "Connected, Socket Type:" + socketType);
            notifyUI("Connected, Socket Type:" + socketType, " ----->", LOG_TYPE);

            instance.startHandShakeTimer();

            // stop listening new connection
            instance.stopAcceptListener();
            instance.reset();

            // Start the thread to manage the connection and perform transmissions
            mConnectedThread = new ConnectedThread(socket, socketType, instance, instance.context);
            mConnectedThread.start();
        }
    }

    private void stopAcceptListener() {
        if (mInsecureAcceptThread != null) {
            mInsecureAcceptThread.Stop();
            mInsecureAcceptThread = null;
            notifyUI("mInsecureAcceptThread stopped", " ------>", LOG_TYPE);
        }
    }

    private void startAcceptListener() {    
        Log.d(TAG, "in startAcceptListener .... ");    
        if (mInsecureAcceptThread == null && this.mAdapter != null) {
            mInsecureAcceptThread = new AcceptThread(this.context, instance);
            mInsecureAcceptThread.start();
            notifyUI("mInsecureAcceptThread start", " ------>", LOG_TYPE);
        }
    }


    private void createRepeatSyncActivityTimer(final long milliSeconds) {
        instance.mHandler.post(new Runnable() {
            @Override
            public void run() {           
                Log.d(TAG, "create repeatSyncActivityTimer with delay ..." + milliSeconds); 
                instance.repeatSyncActivityTimer = new CountDownTimer(milliSeconds, 10000) {
                    @Override
                    public void onTick(long millisUntilFinished) {
                        Log.d(TAG, "repeatSyncActivityTimer ticking ..." + !instance.isSyncStarted.get());
                    }

                    @Override
                    public void onFinish() {
                        synchronized(BluetoothManager.class) {
                            try {
                                if (instance.isBluetoothEnabled() && !instance.isSyncStarted.get()) {
                                    Log.d(TAG, "repeatSyncActivityTimer finished staring Sync ....");
                                    instance.startSync(false);
                                    if(instance.repeatSyncActivityTimer != null) {
                                        instance.repeatSyncActivityTimer.cancel();
                                        instance.repeatSyncActivityTimer.start();
                                    }                                    
                                } else {
                                    Log.d(TAG, "repeatSyncActivityTimer finished but sync already in progress ....");
                                }
                            } catch (Exception e) {
                                e.printStackTrace();
                            }
                             
                        }
                    }
                }.start();
            }
        });
    }


    private void startAgain() {
        instance.isSyncStarted.set(false);
        instance.pollingIndex = instance.pollingIndex + 1; // start with next
        notifyUI("WAITING FOR NEXT SYNC ROUND ....", " ----> ", LOG_TYPE);
        instance.Stop();
        instance.startAcceptListener();
        if (instance.repeatSyncActivityTimer != null) {
            instance.repeatSyncActivityTimer.cancel();
            instance.repeatSyncActivityTimer.start();
        }           
    }

    @Override
    public void GotConnection(BluetoothSocket socket, BluetoothDevice device, String socketType) {
        synchronized (BluetoothManager.class) {
            Log.d(TAG, "GotConnection connected, Socket Type:" + socketType);
            notifyUI("GotConnection connected, Socket Type:" + socketType, " ----->", LOG_TYPE);

            instance.startHandShakeTimer();

            //stop accept listener
            
            instance.stopAcceptListener();
            instance.reset();
            // Start the thread to manage the connection and perform transmissions
            mConnectedThread = new ConnectedThread(socket, socketType, instance, instance.context);
            mConnectedThread.start();
        }
    }


    private void stopRepeatSyncActivityTimer() {
        if(instance.repeatSyncActivityTimer != null) {
            instance.repeatSyncActivityTimer.cancel();            
        }                   
    }

    @Override
    public void PollSocketFailed(String reason) {
        synchronized (BluetoothManager.class) {
            final String tmp = reason;
            Log.d(TAG, "conn PollSocketFailed: " + tmp);
            instance.HandShakeFailed("conn PollSocketFailed: " + tmp, false);
        }
    }

    @Override
    public void CreateSocketFailed(String reason) {
        synchronized (BluetoothManager.class) {
            final String tmp = reason;
            Log.d(TAG, "CreateSocketFailed Error: " + tmp);
            instance.HandShakeFailed("CreateSocketFailed Error: " + tmp, false);
        }
    }

    @Override
    public void ConnectionFailed(String reason) {
        // Start the service over to restart listening mode
        notifyUI("conn ConnectionFailed: " + reason, " ---->", LOG_TYPE);
        instance.HandShakeFailed("ConnectionFailed --> reason: " + reason, true);
    }

    @Override
    public void ListeningFailed(String reason) {
        synchronized (BluetoothManager.class) {
            final String tmp = reason;
            Log.d(TAG, "LISTEN Error: " + tmp);
            instance.HandShakeFailed("LISTEN Error: " + tmp, false);
        }
    }

    private void startDiscoveryTimer() {
        synchronized (BluetoothManager.class) {
            if(instance.startBluetoothDiscoveryTimer != null) {
                instance.startBluetoothDiscoveryTimer.start();     
            }
           
        }
    }


    private void startListener() {
        synchronized (BluetoothManager.class) {
            if(instance.isBluetoothEnabled()) {
                Log.d(TAG, "in startListener ....");
                instance.reset();
                instance.startAcceptListener();                
            }
        }
    }


    @Override
    public void BluetoothStateChanged(int state) {
        instance.bluetoothState = state;
        if (state == BluetoothAdapter.SCAN_MODE_NONE) {
            instance.stopAllBlueToothActivityTimer.start();
            Log.d(TAG, "Bluetooth DISABLED, stopping");            
            if (instance.repeatSyncActivityTimer != null) {
                instance.repeatSyncActivityTimer.cancel();
            }
        } else if (state == BluetoothAdapter.SCAN_MODE_CONNECTABLE
                || state == BluetoothAdapter.SCAN_MODE_CONNECTABLE_DISCOVERABLE) {
            Log.d(TAG, "Bluetooth enabled, re-starting");
            instance.startBluetoothBased();
        }
    }

    public Context getContext() {
        return context;
    }

    /**
     * Start device discover with the BluetoothAdapter
     */
    private void doDiscovery() {
        if (!isDiscoverying.get()) {
            Log.d(TAG, "doDiscovery()");
            isDiscoverying.set(true);
            // Indicate scanning in the title
            // If we're already discovering, stop it
            // if (instance.mAdapter.isDiscovering()) {
            //     instance.mAdapter.cancelDiscovery();
            // }

            // Request discover from BluetoothAdapter
            // instance.mAdapter.startDiscovery();
        }
    }

    public String getBluetoothMacAddress() {
        String bluetoothMacAddress = null;
        try 
        {
            BluetoothAdapter bluetoothAdapter = instance.getmAdapter();
            if (bluetoothAdapter != null) {                                
                if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.M) {
                    try {
                        Field mServiceField = bluetoothAdapter.getClass().getDeclaredField("mService");
                        mServiceField.setAccessible(true);

                        Object btManagerService = mServiceField.get(bluetoothAdapter);

                        if (btManagerService != null) {
                            bluetoothMacAddress = (String) btManagerService.getClass().getMethod("getAddress").invoke(btManagerService);
                            Log.d(TAG, "inside getBluetoothMacAddress 222: " + bluetoothMacAddress);
                        }
                    } catch (NoSuchFieldException e) {

                    } catch (NoSuchMethodException e) {

                    } catch (IllegalAccessException e) {

                    } catch (InvocationTargetException e) {

                    }
                } else {                    
                    bluetoothMacAddress = bluetoothAdapter.getAddress();
                    Log.d(TAG, "inside getBluetoothMacAddress 222: " + bluetoothMacAddress);
                }
                return bluetoothMacAddress;
            } else {
                return bluetoothMacAddress;
            }
        } catch (Exception e) {
            e.printStackTrace();
            return bluetoothMacAddress;
        }        
    }

    public Set<String> getAllSyncInfosReceived() {
        return allSyncInfosReceived;
    }

    public void addNewMessage(String message) {
        dbSyncManager.addMessage(P2PContext.getLoggedInUser(), null, "Chat", message);
    }

    private final BroadcastReceiver newMessageAddedReceiver = new BroadcastReceiver() {

        public void onReceive(Context context, Intent intent) {
            final P2PSyncInfo info = (P2PSyncInfo) intent.getSerializableExtra(NEW_MESSAGE_ADDED);
            // if (info != null && instance.isBluetoothEnabled()) {
            //     AsyncTask.execute(new Runnable() {
            //         @Override
            //         public void run() {
            //         }
            //     });                    
            // }
        }
    };

    public void sendMulticastMessage(String message) {
        try {
            ConnectedThread r;
            // Synchronize a copy of the ConnectedThread
            synchronized (this) {
                if (mState.get() != STATE_CONNECTED)
                    return;
                r = mConnectedThread;
            }
            message = "START" + message + "END";
            notifyUI("sending message:" + message, " ------> ", LOG_TYPE);
            // Perform the write unsynchronized
            r.write(message.getBytes());
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private final BroadcastReceiver mMessageEventReceiver = new BroadcastReceiver() {
        public void onReceive(Context context, Intent intent) {
            // Get extra data included in the Intent
            String message = intent.getStringExtra("message");
            String fromIP = intent.getStringExtra("fromIP");
            processInComingMessage(message, fromIP);
        }
    };  

    public void updateNetworkConnected(boolean connected) {
        synchronized(BluetoothManager.class) {
            instance.isConnected.set(connected);    

            if(instance.isBluetoothEnabled()) {
                instance.startBluetoothBased();
            }
        }
        
    }

    public void stopBlueToothConnections() {
        Log.d(TAG, "network is connected or bluetooth is not enabled ...stopping all bluetooth activity");        
        if (startAllBlueToothActivityTimer != null) {
            startAllBlueToothActivityTimer.cancel();
        }
        if(instance.stopAllBlueToothActivityTimer != null) {
            instance.stopAllBlueToothActivityTimer.cancel();    
            instance.stopAllBlueToothActivityTimer.start();    
        }                
    }

    public void processInComingMessage(final String message, final String fromIP) {
        if(instance.isBluetoothEnabled()) {
            AsyncTask.execute(new Runnable() {
                @Override
                public void run() {
                    if (instance.isHandShakingMessage(message)) {
                        instance.processInComingHandShakingMessage(message);
                    } else if (instance.isSyncRequestMessage(message)) {
                        List<String> syncInfoMessages = instance.processInComingSyncRequestMessage(message);
                        instance.sendMessages(syncInfoMessages);
                        mHandler.postDelayed(new Runnable() {
                            @Override
                            public void run() {
                                instance.sendMulticastMessage("BLUETOOTH-SYNC-COMPLETED");
                            }
                        }, 1000);

                    } else if (instance.isSyncInfoMessage(message)) {
                        instance.processInComingSyncInfoMessage(message);
                    } else if (instance.isBluetoothSyncCompleteMessage(message)) {
                        instance.createDisconnectTimer();
                    }
                }
            });
        }
    }

    private MessageStatus validIncomingSyncMessage(P2PSyncInfo info, MessageStatus status) {
        // DON'T reject out of order message, send handshaking request for only missing data
        // reject duplicate messages if any
        boolean isValid = true;
        String iKey = info.getDeviceId() + "_" + info.getUserId() + "_" + Long.valueOf(info.getSequence().longValue());
        String iPreviousKey = info.getDeviceId() + "_" + info.getUserId() + "_" + Long.valueOf(info.getSequence().longValue() - 1);
        Log.d(TAG, "validIncomingSyncMessage previousKey" + iPreviousKey);
        // remove duplicates
        if (allSyncInfosReceived.contains(iKey)) {
            Log.d(TAG, "sync data message as key already found" + iKey);
            status.setDuplicateMessage(true);
            status.setOutOfSyncMessage(false);
            isValid = false;
        } else if ((info.getSequence().longValue() - 1) != 0
                && !allSyncInfosReceived.contains(iPreviousKey)) {
            Log.d(TAG, "found sync data message as out of sequence => previous key not found " + iPreviousKey + " for key:" + iKey);
            isValid = false;
            status.setDuplicateMessage(false);
            status.setOutOfSyncMessage(true);
        }

        if (isValid) {
            Log.d(TAG, "validIncomingSyncMessage adding to allSyncInfosReceived for key:" + iKey);
            allSyncInfosReceived.add(iKey);
        }

        return status;
    }

    public void processInComingSyncInfoMessage(final String message) {
        AsyncTask.execute(new Runnable() {
            @Override
            public void run() {
               Log.d(TAG, "processInComingSyncInfoMessage -> " + message);
                Iterator<P2PSyncInfo> infos = p2PDBApiImpl.deSerializeP2PSyncInfoFromJson(message).iterator();
                while (infos.hasNext()) {
                    P2PSyncInfo info = infos.next();
                    MessageStatus status = new MessageStatus(false, false);
                    status = instance.validIncomingSyncMessage(info, status);
                    if (status.isDuplicateMessage()) {
                        notifyUI(info.message + " ---------> duplicate - rejected ", info.getSender(), LOG_TYPE);
                        infos.remove();
                    } else if (status.isOutOfSyncMessage()) {
                        notifyUI(info.message + " with sequence " + info.getSequence() + " ---------> out of sync processed with filling Missing type message ", info.getSender(), LOG_TYPE);
                        String key = info.getDeviceId() + "_" + info.getUserId() + "_" + Long.valueOf(info.getSequence().longValue());
                        Log.d(TAG, "processing out of sync data message for key:" + key + " and sequence:" + info.sequence);
                        String rMessage = p2PDBApiImpl.persistOutOfSyncP2PSyncMessage(info);
                        // generate handshaking request
                        if (status.isOutOfSyncMessage()) {
                            Log.d(TAG, "validIncomingSyncMessage -> out of order -> sendInitialHandShakingMessage");
                            sendInitialHandShakingMessage(true);
                        }
                    } else if (!status.isOutOfSyncMessage() && !status.isDuplicateMessage()) {
                        String key = info.getDeviceId() + "_" + info.getUserId() + "_" + Long.valueOf(info.getSequence().longValue());
                        Log.d(TAG, "processing sync data message for key:" + key + " and message:" + info.message);
                        String rMessage = p2PDBApiImpl.persistP2PSyncInfo(info);
                    } else {
                        infos.remove();
                    }
                }
            }
        });

    }    

    public List<String> processInComingSyncRequestMessage(String message) {
        Log.d(TAG, "processInComingSyncRequestMessage => " + message);
        List<String> jsonRequests = new CopyOnWriteArrayList<String>();
        SyncInfoRequestMessage request = p2PDBApiImpl.buildSyncRequstMessage(message);
        // process only if matching current device id
        if (request != null && request.getmDeviceId().equalsIgnoreCase(P2PContext.getCurrentDevice())) {
            Log.d(TAG, "processInComingSyncRequestMessage => device id matches with: " + P2PContext.getCurrentDevice());
            notifyUI("sync request message received", " ------> ", LOG_TYPE);
            List<SyncInfoItem> items = request.getItems();
            for (SyncInfoItem a : items) {
                Log.d(TAG, "processInComingSyncRequestMessage => adding to jsonRequest for sync messages");
                jsonRequests.addAll(p2PDBApiImpl.fetchP2PSyncInfoBySyncRequest(a));
            }
        }

        return jsonRequests;
    }

    public void processInComingHandShakingMessage(String message) {

        Log.d(TAG, "processInComingHandShakingMessage: " + message);
        notifyUI("handshaking message received", " ------> ", LOG_TYPE);
        //parse message and add to all messages
        HandShakingMessage handShakingMessage = instance.parseHandShakingMessage(message);
        if (handShakingMessage.getBt() != null && handShakingMessage.getFrom() != null) {
            instance.p2PDBApiImpl.saveBtAddress(handShakingMessage.getFrom(), handShakingMessage.getBt());
        }

        boolean shouldSendAck = shouldSendAckForHandShakingMessage(handShakingMessage);

        // send handshaking information if message received "from" first time
        if (shouldSendAck) {
            Log.d(TAG, "replying back with initial hand shaking message with needAck => false");
            notifyUI("handshaking message sent with ack false", " ------> ", LOG_TYPE);
            sendInitialHandShakingMessage(false);
        }

        synchronized (BluetoothManager.class) {
            instance.generateSyncInfoPullRequest(instance.getAllHandShakeMessagesInCurrentLoop());
        }
    }

    public Map<String, HandShakingMessage> getAllHandShakeMessagesInCurrentLoop() {
        synchronized (BluetoothManager.class) {
            Map<String, HandShakingMessage> messagesTillNow = Collections.unmodifiableMap(handShakingMessagesInCurrentLoop);
            CollectionUtils.subtract(handShakingMessagesInCurrentLoop.keySet(), messagesTillNow.keySet());
            return messagesTillNow;
        }
    }

    public List<String> generateSyncInfoPullRequest(final Map<String, HandShakingMessage> messages) {
        List<String> jsons = new ArrayList<String>();
        final Collection<HandShakingInfo> pullSyncInfo = instance.computeSyncInfoRequired(messages);
        Log.d(TAG, "generateSyncInfoPullRequest -> computeSyncInfoRequired ->" + pullSyncInfo.size());
        notifyUI("generateSyncInfoPullRequest -> computeSyncInfoRequired ->" + pullSyncInfo.size(), " ------> ", LOG_TYPE);
        if (pullSyncInfo != null && pullSyncInfo.size() > 0) {
            jsons = p2PDBApiImpl.serializeSyncRequestMessages(pullSyncInfo);
            instance.sendMessages(jsons);
        } else {
            instance.isSyncStarted.set(false);
            Log.d(TAG, "generateSyncInfoPullRequest -> sync completed:" + !instance.isSyncStarted.get());
            if (instance.repeatSyncActivityTimer != null) {
                instance.repeatSyncActivityTimer.cancel();
                instance.repeatSyncActivityTimer.start();
            }
        }
        return jsons;     
    }

    private void sendMessages(List<String> computedMessages) {
        if (computedMessages != null && computedMessages.size() > 0) {
            Iterator<String> it = computedMessages.iterator();
            while (it.hasNext()) {
                String p = it.next();
                instance.sendMulticastMessage(p);
            }
        }
    }


    private Set<HandShakingInfo> sortHandShakingInfos(final Map<String, HandShakingMessage> messages) {
        final Set<HandShakingInfo> allHandShakingInfos = new TreeSet<HandShakingInfo>(new Comparator<HandShakingInfo>() {
            @Override
            public int compare(HandShakingInfo o1, HandShakingInfo o2) {
                if (o1.getDeviceId().equalsIgnoreCase(o2.getDeviceId())) {
                    if (o1.getSequence().longValue() > o2.getSequence().longValue()) {
                        return 1;
                    } else {
                        return -1;
                    }
                }
                return o1.getDeviceId().compareToIgnoreCase(o2.getDeviceId());
            }
        });

        Iterator<Map.Entry<String, HandShakingMessage>> entries = messages.entrySet().iterator();
        while (entries.hasNext()) {
            Map.Entry<String, HandShakingMessage> entry = entries.next();
            Iterator<HandShakingInfo> it = entry.getValue().getInfos().iterator();
            while (it.hasNext()) {
                HandShakingInfo i = it.next();
                i.setFrom(entry.getKey());
            }

            allHandShakingInfos.addAll(entry.getValue().getInfos());
        }
        return allHandShakingInfos;
    }


private Collection<HandShakingInfo> computeSyncInfoRequired(final Map<String, HandShakingMessage> messages) {
        // sort by device id and sequence desc order
        synchronized (BluetoothManager.class) {
            final Set<HandShakingInfo> allHandShakingInfos = sortHandShakingInfos(messages);
            Iterator<HandShakingInfo> itReceived = allHandShakingInfos.iterator();
            final Map<String, HandShakingInfo> uniqueHandShakeInfosReceived = new ConcurrentHashMap<String, HandShakingInfo>();
            final Map<String, HandShakingInfo> photoProfileUpdateInfosReceived = new ConcurrentHashMap<String, HandShakingInfo>();

            while (itReceived.hasNext()) {
                HandShakingInfo info = itReceived.next();
                HandShakingInfo existingInfo = uniqueHandShakeInfosReceived.get(info.getUserId());
                if (existingInfo == null) {
                    uniqueHandShakeInfosReceived.put(info.getUserId(), info);
                } else {
                    if (existingInfo.getSequence().longValue() < info.getSequence().longValue()) {
                        uniqueHandShakeInfosReceived.put(info.getUserId(), info);
                    } else if (existingInfo.getSequence().longValue() == info.getSequence().longValue()) {

                        String myMissingMessageSequences = existingInfo.getMissingMessages();
                        String otherDeviceMissingMessageSequences = info.getMissingMessages();
                        List<String> list1 = new ArrayList<String>();
                        List<String> list2 = new ArrayList<String>();
                        if (myMissingMessageSequences != null) {
                            list1 = Lists.newArrayList(Splitter.on(",").split(myMissingMessageSequences));
                        }
                        if (otherDeviceMissingMessageSequences != null) {
                            list2 = Lists.newArrayList(Splitter.on(",").split(otherDeviceMissingMessageSequences));
                        }
                        if (list1.size() > list2.size()) {
                            uniqueHandShakeInfosReceived.put(info.getUserId(), info);
                        }
                    }
                }
            }

            final Map<String, HandShakingInfo> myHandShakingMessages = p2PDBApiImpl.handShakingInformationFromCurrentDevice();

            Iterator<String> keys = uniqueHandShakeInfosReceived.keySet().iterator();
            while (keys.hasNext()) {
                String userKey = keys.next();
                Log.d(TAG, "computeSyncInfoRequired user key:" + userKey);
                if (myHandShakingMessages.keySet().contains(userKey)) {
                    HandShakingInfo infoFromOtherDevice = uniqueHandShakeInfosReceived.get(userKey);
                    HandShakingInfo infoFromMyDevice = myHandShakingMessages.get(userKey);
                    if(infoFromOtherDevice != null && infoFromMyDevice != null) 
                    {

                        Long latestProfilePhotoInfo = infoFromOtherDevice.getProfileSequence();
                        Long latestUserProfileId = p2PDBApiImpl.findLatestProfilePhotoId(infoFromOtherDevice.getUserId(), infoFromOtherDevice.getDeviceId());

                        if (latestUserProfileId != null && latestUserProfileId != null
                                && latestUserProfileId.longValue() < latestProfilePhotoInfo.longValue()) {
                            photoProfileUpdateInfosReceived.put(infoFromOtherDevice.getUserId(), infoFromOtherDevice);  
                        }

                        final long askedThreshold = infoFromMyDevice.getSequence().longValue() > SYNC_NUMBER_OF_LAST_MESSAGES ? infoFromMyDevice.getSequence().longValue() + 1 - SYNC_NUMBER_OF_LAST_MESSAGES : -1;
                        if (infoFromMyDevice.getSequence().longValue() > infoFromOtherDevice.getSequence().longValue()) {
                            Log.d(TAG, "removing from uniqueHandShakeInfosReceived for key:" + userKey + " as infoFromMyDevice.getSequence()" + infoFromMyDevice.getSequence() + " infoFromOtherDevice.getSequence()" + infoFromOtherDevice.getSequence());
                            uniqueHandShakeInfosReceived.remove(userKey);
                        } else if (infoFromMyDevice.getSequence().longValue() == infoFromOtherDevice.getSequence().longValue()) {
                            //check for missing keys, if the same then remove otherwise only add missing key for infoFromMyDevice
                            String myMissingMessageSequences = infoFromMyDevice.getMissingMessages();
                            String otherDeviceMissingMessageSequences = infoFromOtherDevice.getMissingMessages();
                            List<String> list1 = new ArrayList<String>();
                            List<String> list2 = new ArrayList<String>();
                            if (myMissingMessageSequences != null) {
                                list1 = Lists.newArrayList(Splitter.on(",").split(myMissingMessageSequences));
                            }
                            if (otherDeviceMissingMessageSequences != null) {
                                list2 = Lists.newArrayList(Splitter.on(",").split(otherDeviceMissingMessageSequences));
                            }
                            List<String> missingSequencesToAsk = new ArrayList<>(CollectionUtils.subtract(list1, list2));
                            if (askedThreshold > -1) {
                                CollectionUtils.filter(missingSequencesToAsk, new Predicate<String>() {
                                    @Override
                                    public boolean evaluate(String o) {
                                        return o.compareTo(String.valueOf(askedThreshold)) >= 0;
                                    }
                                });
                            }
                            Set<String> missingMessagesSetToAsk = ImmutableSet.copyOf(missingSequencesToAsk);
                            if (missingMessagesSetToAsk != null && missingMessagesSetToAsk.size() > 0) {
                                infoFromOtherDevice.setMissingMessages(StringUtils.join(missingMessagesSetToAsk, ","));
                                infoFromOtherDevice.setStartingSequence(infoFromOtherDevice.getSequence() + 1);
                            } else {
                                Log.d(TAG, "removing from uniqueHandShakeInfosReceived for key:" + userKey + " as infoFromMyDevice.getSequence()" + infoFromMyDevice.getSequence() + " infoFromOtherDevice.getSequence()" + infoFromOtherDevice.getSequence());
                                uniqueHandShakeInfosReceived.remove(userKey);
                            }
                            missingSequencesToAsk = null;
                            missingMessagesSetToAsk = null;

                        } else {
                            Log.d(TAG, "uniqueHandShakeInfosReceived for key:" + userKey + " as infoFromOtherDevice.setStartingSequence" + infoFromMyDevice.getSequence().longValue());
                            // take other device's missing keys remove
                            // take my missing keys and remove if the same as other device's missing keys
                            // ask for all messages my sequence + 1
                            // ask for all my missing keys messages also

                            String myMissingMessageSequences = infoFromMyDevice.getMissingMessages();
                            String otherDeviceMissingMessageSequences = infoFromOtherDevice.getMissingMessages();
                            List<String> list1 = new ArrayList<String>();
                            List<String> list2 = new ArrayList<String>();
                            if (myMissingMessageSequences != null) {
                                list1 = Lists.newArrayList(Splitter.on(",").split(myMissingMessageSequences));
                            }
                            if (otherDeviceMissingMessageSequences != null) {
                                list2 = Lists.newArrayList(Splitter.on(",").split(otherDeviceMissingMessageSequences));
                            }
                            List<String> missingSequencesToAsk = new ArrayList<>(CollectionUtils.subtract(list1, list2));
                            if (askedThreshold > -1) {
                                CollectionUtils.filter(missingSequencesToAsk, new Predicate<String>() {
                                    @Override
                                    public boolean evaluate(String o) {
                                        return o.compareTo(String.valueOf(askedThreshold)) >= 0;
                                    }
                                });
                            }
                            Set<String> missingMessagesSetToAsk = ImmutableSet.copyOf(missingSequencesToAsk);
                            if (missingMessagesSetToAsk != null && missingMessagesSetToAsk.size() > 0) {
                                infoFromOtherDevice.setMissingMessages(StringUtils.join(missingMessagesSetToAsk, ","));
                            }
                            //infoFromOtherDevice.setStartingSequence(infoFromMyDevice.getSequence().longValue() + 1);
                            if(infoFromOtherDevice.getSequence() > SYNC_NUMBER_OF_LAST_MESSAGES) {
                                infoFromOtherDevice.setStartingSequence(infoFromOtherDevice.getSequence() - SYNC_NUMBER_OF_LAST_MESSAGES + 1);
                            } else {
                                infoFromOtherDevice.setStartingSequence(infoFromMyDevice.getSequence().longValue() + 1);
                            }

                            missingSequencesToAsk = null;
                            missingMessagesSetToAsk = null;
                        }                        
                    }            
                }
            }


            List<HandShakingInfo> valuesToSend = new ArrayList<HandShakingInfo>();

            Collection<HandShakingInfo> photoValues = photoProfileUpdateInfosReceived.values();
            Iterator itPhotoValues = photoValues.iterator();
            while (itPhotoValues.hasNext()) {
                HandShakingInfo t = (HandShakingInfo) itPhotoValues.next();
                HandShakingInfo n = new HandShakingInfo(t.getUserId(), t.getDeviceId(), t.getProfileSequence(), null, null);
                n.setFrom(t.getFrom());
                n.setStartingSequence(Long.valueOf(t.getProfileSequence()));
                n.setSequence(Long.valueOf(t.getProfileSequence()));
                valuesToSend.add(n);
            }


            Collection<HandShakingInfo> values = uniqueHandShakeInfosReceived.values();
            Iterator itValues = values.iterator();
            while (itValues.hasNext()) {
                HandShakingInfo t = (HandShakingInfo) itValues.next();
                Log.d(TAG, "validating : " + t.getUserId() + " " + t.getDeviceId() + " " + t.getStartingSequence() + " " + t.getSequence());

                if (t.getMissingMessages() != null && t.getMissingMessages().length() > 0) {

                    List<String> missingMessages = Lists.newArrayList(Splitter.on(",").split(t.getMissingMessages()));
                    Set<String> missingMessagesSet = ImmutableSet.copyOf(missingMessages);
                    missingMessages = null;
                    for (String m : missingMessagesSet) {
                        HandShakingInfo n = new HandShakingInfo(t.getUserId(), t.getDeviceId(), t.getSequence(), null, null);
                        n.setFrom(t.getFrom());
                        n.setStartingSequence(Long.valueOf(m));
                        n.setSequence(Long.valueOf(m));
                        valuesToSend.add(n);
                    }
                }


                if (t.getStartingSequence() == null) {
                    t.setMissingMessages(null);
                    valuesToSend.add(t);
                } else if (t.getStartingSequence() != null && t.getStartingSequence().longValue() <= t.getSequence().longValue()) {
                    t.setMissingMessages(null);
                    valuesToSend.add(t);
                }
            }
            return valuesToSend;
        }
    }    

    private boolean shouldSendAckForHandShakingMessage(HandShakingMessage handShakingMessage) {
        if (handShakingMessage != null) {
            boolean sendAck = handShakingMessage.getReply().equalsIgnoreCase("true");
            Log.d(TAG, "shouldSendAckForHandShaking: " + handShakingMessage.getFrom() + " sendAck:" + sendAck);
            return sendAck;
        } else {
            return false;
        }
    }


    public HandShakingMessage parseHandShakingMessage(String message) {
        HandShakingMessage handShakingMessage = p2PDBApiImpl.deSerializeHandShakingInformationFromJson(message);
        if (handShakingMessage != null) {
            Log.d(TAG, "storing handShakingMessage from : " + handShakingMessage.getFrom() + " in handShakingMessagesInCurrentLoop");
            instance.handShakingMessagesInCurrentLoop.put(handShakingMessage.getFrom(), handShakingMessage);
        }
        return handShakingMessage;
    }

    private boolean isBluetoothSyncCompleteMessage(String message) {
        boolean isSyncCompletedMessage = false;
        if (message != null && message.equalsIgnoreCase("BLUETOOTH-SYNC-COMPLETED")) {
            isSyncCompletedMessage = true;
        }
        return isSyncCompletedMessage;
    }

    private void broadCastRefreshDevice() {
        Intent intent = new Intent(refreshDevice);
        LocalBroadcastManager.getInstance(this.context).sendBroadcast(intent);
    }

    private final BroadcastReceiver refreshDeviceReceiver = new BroadcastReceiver() {
        public void onReceive(Context context, Intent intent) {
            synchronized (BluetoothManager.class) {
                AsyncTask.execute(new Runnable() {
                    @Override
                    public void run() {
                        notifyUI("Clear ALL...", " ------> ", CLEAR_CONSOLE_TYPE);
                        List<P2PSyncInfo> allInfos = new ArrayList<P2PSyncInfo>();                        
                        try {
                            allInfos = p2PDBApiImpl.refreshAllMessages();
                            if (allInfos != null) {
                            Iterator<P2PSyncInfo> allInfosIt = allInfos.iterator();
                            while (allInfosIt.hasNext()) {
                                P2PSyncInfo p = allInfosIt.next();
                                instance.getAllSyncInfosReceived().add(p.getDeviceId() + "_" + p.getUserId() + "_" + Long.valueOf(p.getSequence().longValue()));
                                String sender = p.getSender().equals(P2PContext.getCurrentDevice()) ? "You" : p.getSender();
                                notifyUI(p.message, sender, CONSOLE_TYPE);
                            }
                        }
                        Log.d(TAG, "rebuild sync info received cache and updated UI");                            
                        } catch (Exception e) {

                        }                                                
                    }

                });
            }
        }
    };

    public void stopHandShakeTimer() {
        if (instance.handShakeFailedTimer != null) {
            instance.handShakeFailedTimer.cancel();
            instance.notifyUI("stopHandShakeTimer", " ------>", LOG_TYPE);
        }
    }

    public void connectedToRemote() {
        instance.notifyUI("connectedToRemote .... waiting for action ...", " ------>", LOG_TYPE);
        // stop polling
        if (instance.nextRoundTimer != null) {
            instance.nextRoundTimer.cancel();
        }
        instance.sendFindBuddyMessage();
    }

    public void sendFindBuddyMessage() {
        instance.sendInitialHandShakingMessage(true);
    }

    private void sendInitialHandShakingMessage(boolean needAck) {
        // construct handshaking message(s)
        // put in queue - TBD
        // send one by one from queue - TBD
        String serializedHandShakingMessage = instance.p2PDBApiImpl.serializeHandShakingMessage(needAck);
        Log.d(TAG, "sending initial handshaking message: " + serializedHandShakingMessage);
        instance.sendMulticastMessage(serializedHandShakingMessage);
    }

    public void startHandShakeTimer() {
        synchronized (BluetoothManager.class) {
            if(instance != null && instance.handShakeFailedTimer != null) {
                instance.handShakeFailedTimer.start();    
            }            
        }
    }


    @Override
    public void HandShakeFailed(String reason, boolean isDisconnectAfterSync) {
        try {
            synchronized (BluetoothManager.class) 
            {
                notifyUI("HandShakeFailed: " + reason, " ----> ", LOG_TYPE);
                if(instance.isBluetoothEnabled()) {
                    notifyUI("peerDevices has  ... " + instance.peerDevices.size(), "---------->", LOG_TYPE);
                    if (peerDevices != null && peerDevices.contains(instance.connectedAddress)) {
                        peerDevices.remove(instance.connectedAddress);
                        notifyUI("peerDevices removed ... " + instance.connectedAddress, "---------->", LOG_TYPE);
                        instance.connectedAddress = "";
                    }
                    instance.stopHandShakeTimer();
                }            
            }
        } catch (Exception ex) {
            ex.printStackTrace();
        } finally {
            try { 
                if(instance.isBluetoothEnabled()) {       
                    //start incoming listener - accept new connections
                    instance.startAcceptListener();

                    if(isDisconnectAfterSync) {
                        notifyUI("Start Next Device to Sync ....", " ----> ", LOG_TYPE);
                        instance.startNextDeviceToSync();
                        instance.isSyncStarted.set(false);
                        if (instance.repeatSyncActivityTimer != null) {
                            instance.repeatSyncActivityTimer.cancel();
                            instance.repeatSyncActivityTimer.start();
                        }
                    } 
                    else  {
                        instance.startAgain();                
                    }
                } else {
                    instance.stopBlueToothConnections();
                }
            }
            catch (Exception ex) {
                ex.printStackTrace();
            }    
        }        
    }

    public void startNextDeviceToSync() {
        synchronized (BluetoothManager.class) {
            if (instance.isBluetoothEnabled()) {
                notifyUI("peerDevices has  ... " + instance.peerDevices.size(), "---------->", LOG_TYPE);
                if (peerDevices != null && peerDevices.contains(instance.connectedAddress)) {
                    peerDevices.remove(instance.connectedAddress);
                    notifyUI("peerDevices removed ... " + instance.connectedAddress, "---------->", LOG_TYPE);
                    instance.connectedAddress = "";
                }                
                instance.startListener();                
                instance.startNextPolling();
                notifyUI("startNextDeviceToSync ...", "---------->", LOG_TYPE);
            } else {
                if (instance.stopAllBlueToothActivityTimer != null) {
                    instance.stopAllBlueToothActivityTimer.cancel();
                    instance.stopAllBlueToothActivityTimer.start();
                }
            }
        }
    }
}
