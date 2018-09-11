package org.chimple.flores.sync.Direct;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.net.wifi.p2p.WifiP2pDevice;
import android.net.wifi.p2p.WifiP2pDeviceList;
import android.net.wifi.p2p.WifiP2pManager;
import android.net.wifi.p2p.nsd.WifiP2pDnsSdServiceRequest;
import android.os.CountDownTimer;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.Timer;
import java.util.TimerTask;
import java.util.UUID;

import org.chimple.flores.sync.SyncUtils;

public class P2PServiceFinder {

    private static final String TAG = P2PServiceFinder.class.getSimpleName();

    private P2PServiceFinder that;
    // Context
    private Context context;

    // Receivers
    private P2PServiceFinderReceiver receiver;


    private String SERVICE_TYPE;
    private List<P2PSyncService> serviceList;
    private List<P2PSyncService> highPriorityServiceList;

    // P2P

    private WifiP2pManager wifiP2pManager;
    private WifiP2pManager.Channel channel;
    private WifiP2pManager.DnsSdServiceResponseListener serviceListener;
    private WifiP2pManager.DnsSdTxtRecordListener txtListener;
    private WifiP2pManager.PeerListListener peerListListener;
    private WifiP2pDeviceList wifiP2pDeviceList;

    // CallBack
    private final WifiConnectionUpdateCallBack callBack;

    // Timers
//    private CountDownTimer peerDiscoveryTimer = null;
//    private CountDownTimer discoverServiceTimeOutTimer = null;

    private TimerTask peerDiscoveryTimerTask;
    private Timer peerDiscoverTimer;

    private TimerTask discoverServiceTimeOutTimerTask;
    private Timer discoverServiceTimeOutTimer;

    private SyncUtils.DiscoveryState discoveryState = SyncUtils.DiscoveryState.NONE;


    private Object wifiP2pDeviceLock = new Object();

    public P2PServiceFinder(Context context, WifiP2pManager wifiP2pManager, WifiP2pManager.Channel Channel, WifiConnectionUpdateCallBack callBack, String serviceType) {
        Log.i(TAG,"P2PServiceFinder constructor");
        this.that = this;
        this.context = context;
        this.wifiP2pManager = wifiP2pManager;
        this.channel = Channel;
        this.callBack = callBack;
        this.SERVICE_TYPE = serviceType;
        this.highPriorityServiceList = new ArrayList<P2PSyncService>();
        this.serviceList = new ArrayList<P2PSyncService>();
        this.initialize();
    }


    public void initialize() {
        this.initTimers();
        this.registerP2PServiceFinderReceiver();
        this.registerPeerListeners();
        this.registerDnsSdServiceResponseListener();
        this.startPeerDiscovery();
    }

    private void registerPeerListeners() {
        peerListListener = new WifiP2pManager.PeerListListener() {
            public void onPeersAvailable(WifiP2pDeviceList peers) {

                synchronized (wifiP2pDeviceLock) {
                    wifiP2pDeviceList = peers;
                }

                if (wifiP2pDeviceList.getDeviceList().size() > 0) {
                    if (discoveryState != SyncUtils.DiscoveryState.DiscoverService) {
                        boolean doContinue = true;
                        if (callBack != null) {
                            Log.i(TAG,"onPeersAvailable => callBack != null");
                            doContinue = callBack.gotPeersList(wifiP2pDeviceList.getDeviceList());
                        }
                        if (doContinue) {
                            if (that.discoverServiceTimeOutTimer != null) {
                                that.discoverServiceTimeOutTimer.cancel();
                                that.discoverServiceTimeOutTimer = null;
                            }
                            that.discoverServiceTimeOutTimer = new Timer("discover Service Timer" + UUID.randomUUID());
                            that.discoverServiceTimeOutTimerTask = that.createDiscoverServiceTask();
                            that.discoverServiceTimeOutTimer.schedule(that.discoverServiceTimeOutTimerTask, 30 * 1000);
                            startServiceDiscovery();
                        } else {
                            if (discoverServiceTimeOutTimer != null) {
                                that.discoverServiceTimeOutTimer.cancel();
                                that.discoverServiceTimeOutTimer = null;
                            }
                        }
                    }
                }
            }
        };
    }

    //Add current User id into DNS SD Record
    private void registerDnsSdServiceResponseListener() {
        serviceListener = new WifiP2pManager.DnsSdServiceResponseListener() {

            public void onDnsSdServiceAvailable(String instanceName, String serviceType, WifiP2pDevice device) {

                if (serviceType.startsWith(SERVICE_TYPE)) {

                    if (that.discoverServiceTimeOutTimer != null) {
                        that.discoverServiceTimeOutTimer.cancel();
                        that.discoverServiceTimeOutTimer = null;
                    }

                    boolean addService = true;
                    Log.i(TAG, "Found Service, : " + instanceName + ", type : " + serviceType + ":");
                    for (int i = 0; i < serviceList.size(); i++) {
                        if (serviceList.get(i).getDeviceAddress().equals(device.deviceAddress)) {
                            addService = false;
                        }
                    }
                    if (addService) {
                        Log.i(TAG,"Added Found Service to the servicelist");
                        serviceList.add(SyncUtils.createP2PSyncService(instanceName, serviceType, device.deviceAddress, device.deviceName));
                    }

                    that.peerDiscoverTimer = new Timer("Peer discover Timer" + UUID.randomUUID());

                    that.peerDiscoveryTimerTask = that.createPeerDiscoveryTimerTask();
                    long millisInFuture = 5000 + (new Random(System.currentTimeMillis()).nextInt(5000));
                    that.peerDiscoverTimer.schedule(that.peerDiscoveryTimerTask, millisInFuture);

                } else {
                    Log.i(TAG, "Not our Service, :" + SERVICE_TYPE + "!=" + serviceType + ":");


                    if (that.peerDiscoverTimer != null) {
                        that.peerDiscoverTimer.cancel();
                        that.peerDiscoverTimer = null;
                    }
                }



            }
        };

        wifiP2pManager.setDnsSdResponseListeners(channel, serviceListener, null);
//        startPeerDiscovery();
    }

    private TimerTask createDiscoverServiceTask() {
        return new TimerTask() {
            @Override
            public void run() {
                Log.i(TAG,"TimerTask createDiscoverServiceTask ");
                that.stopDiscovery();
                that.startPeerDiscovery();
            }
        };
    }

    private TimerTask createPeerDiscoveryTimerTask() {
        return new TimerTask() {
            @Override
            public void run() {
                Log.i(TAG, "serviceFoundTimeOutTimerTask starting....");
                discoveryState = SyncUtils.DiscoveryState.NONE;
                if (that.callBack != null) {
                    stopDiscovery();
                    that.callBack.processServiceList(serviceList);
                    that.callBack.foundNeighboursList(serviceList);
                } else {
                    startPeerDiscovery();
                }
            }
        };
    }

    private void initTimers() {

//        this.discoverServiceTimeOutTimer = new CountDownTimer(60000, 1000) {
//            public void onTick(long millisUntilFinished) {
//                // not using
//            }
//
//            public void onFinish() {
//                stopDiscovery();
//                startServiceDiscovery();
//            }
//        };

//        this.peerDiscoveryTimer = new CountDownTimer(millisInFuture, 1000) {
//            public void onTick(long millisUntilFinished) {
//                // not using
//            }
//
//            public void onFinish() {
//                discoveryState = SyncUtils.DiscoveryState.NONE;
//                if (that.callBack != null) {
//                    stopDiscovery();
//                    that.callBack.processServiceList(serviceList);
//                    that.callBack.foundNeighboursList(serviceList);
//                } else {
//                    startPeerDiscovery();
//                }
//            }
//        };

    }

    private void registerP2PServiceFinderReceiver() {
        receiver = new P2PServiceFinderReceiver();
        IntentFilter intentFilter = new IntentFilter();
        intentFilter.addAction(WifiP2pManager.WIFI_P2P_DISCOVERY_CHANGED_ACTION);
        intentFilter.addAction(WifiP2pManager.WIFI_P2P_PEERS_CHANGED_ACTION);
        this.context.registerReceiver((receiver), intentFilter);
    }

    private void unregisterP2PServiceFinderReceiver() {
        if (receiver != null) {
            this.context.unregisterReceiver(receiver);
            receiver = null;
        }

    }


    public List<P2PSyncService> serviceList() {
        return serviceList;
    }

    public void cleanUp() {
        this.unregisterP2PServiceFinderReceiver();

        if (this.discoverServiceTimeOutTimerTask != null) {
            this.discoverServiceTimeOutTimerTask.cancel();
        }
        this.discoverServiceTimeOutTimerTask = null;

        if (this.discoverServiceTimeOutTimer != null) {
            this.discoverServiceTimeOutTimer.cancel();
        }
        this.discoverServiceTimeOutTimer = null;

        if (this.peerDiscoveryTimerTask != null) {
            this.peerDiscoveryTimerTask.cancel();
        }
        this.peerDiscoveryTimerTask = null;
        if (this.peerDiscoverTimer != null) {
            this.peerDiscoverTimer.cancel();
        }
        this.peerDiscoverTimer = null;
        this.stopDiscovery();
        this.stopPeerDiscovery();
    }

    private void startPeerDiscovery() {
        wifiP2pManager.discoverPeers(channel, new WifiP2pManager.ActionListener() {
            public void onSuccess() {
                discoveryState = SyncUtils.DiscoveryState.DiscoverPeer;
                Log.i(TAG, "Started peer discovery");
            }

            public void onFailure(int reason) {
                discoveryState = SyncUtils.DiscoveryState.NONE;
                Log.i(TAG, "Starting peer discovery failed, error code " + reason);
                if (reason == 2) {
                    stopPeerDiscovery();
                }
                //lets try again after 1 minute time-out !

                if (that.discoverServiceTimeOutTimer != null) {
                    that.discoverServiceTimeOutTimer.cancel();
                }
                that.discoverServiceTimeOutTimer = new Timer();
                that.discoverServiceTimeOutTimerTask = that.createDiscoverServiceTask();
                that.discoverServiceTimeOutTimer.schedule(that.discoverServiceTimeOutTimerTask, 30 * 1000);
            }
        });
    }

    private void stopPeerDiscovery() {
        wifiP2pManager.stopPeerDiscovery(channel, new WifiP2pManager.ActionListener() {
            public void onSuccess() {
                Log.i(TAG, "Stopped peer discovery");
            }

            public void onFailure(int reason) {
                Log.i(TAG, "Stopping peer discovery failed, error code " + reason);
            }
        });
    }

    private void startServiceDiscovery() {

        discoveryState = SyncUtils.DiscoveryState.DiscoverService;

        WifiP2pDnsSdServiceRequest request = WifiP2pDnsSdServiceRequest.newInstance(SERVICE_TYPE);
        final Handler handler = new Handler(Looper.getMainLooper());
        wifiP2pManager.addServiceRequest(channel, request, new WifiP2pManager.ActionListener() {

            public void onSuccess() {
                Log.i(TAG, "Added service request");
                handler.postDelayed(new Runnable() {
                    //There are supposedly a possible race-condition bug with the service discovery
                    // thus to avoid it, we are delaying the service discovery start here
                    public void run() {
                        wifiP2pManager.discoverServices(channel, new WifiP2pManager.ActionListener() {
                            public void onSuccess() {
                                serviceList.clear();
                                Log.i(TAG, "Started service discovery");
                                discoveryState = SyncUtils.DiscoveryState.DiscoverService;
                            }

                            public void onFailure(int reason) {
                                try {
                                    stopDiscovery();
                                    discoveryState = SyncUtils.DiscoveryState.NONE;
                                    Log.i(TAG, "Starting service discovery failed, error code " + reason);
                                    //lets try again after 1 minute time-out !

                                    if (that.discoverServiceTimeOutTimer != null) {
                                        that.discoverServiceTimeOutTimer.cancel();
                                    }
                                    that.discoverServiceTimeOutTimer = new Timer();
                                    that.discoverServiceTimeOutTimerTask = that.createDiscoverServiceTask();
                                    that.discoverServiceTimeOutTimer.schedule(that.discoverServiceTimeOutTimerTask, 30 * 1000);
                                } catch (Exception e) {
                                    Log.e(TAG, e.getMessage());
                                }
                            }
                        });
                    }
                }, 1000);
            }

            public void onFailure(int reason) {
                discoveryState = SyncUtils.DiscoveryState.NONE;
                Log.i(TAG, "Adding service request failed, error code " + reason);
                //lets try again after 1 minute time-out !
                if (that.discoverServiceTimeOutTimer != null) {
                    that.discoverServiceTimeOutTimer.cancel();
                }
                that.discoverServiceTimeOutTimer = new Timer();
                that.discoverServiceTimeOutTimerTask = that.createDiscoverServiceTask();
                that.discoverServiceTimeOutTimer.schedule(that.discoverServiceTimeOutTimerTask, 30 * 1000);
            }
        });

    }

    private void stopDiscovery() {
        wifiP2pManager.clearServiceRequests(channel, new WifiP2pManager.ActionListener() {
            public void onSuccess() {
                Log.i(TAG, "Cleared service requests");
            }

            public void onFailure(int reason) {
                Log.i(TAG, "Clearing service requests failed, error code " + reason);
            }
        });
    }

    private class P2PServiceFinderReceiver extends BroadcastReceiver {

        @Override
        public void onReceive(Context context, Intent intent) {
            String action = intent.getAction();
            if (WifiP2pManager.WIFI_P2P_PEERS_CHANGED_ACTION.equals(action)) {
                if (discoveryState != SyncUtils.DiscoveryState.DiscoverService) {
                    wifiP2pManager.requestPeers(channel, peerListListener);
                }
            } else if (WifiP2pManager.WIFI_P2P_DISCOVERY_CHANGED_ACTION.equals(action)) {
                that.handleP2PDiscoveryChangedAction(intent);
            }
        }
    }

    private void handleP2PDiscoveryChangedAction(Intent intent) {
        int state = intent.getIntExtra(WifiP2pManager.EXTRA_DISCOVERY_STATE, WifiP2pManager.WIFI_P2P_DISCOVERY_STOPPED);
        String status = "Discovery state changed to ";

        if (state == WifiP2pManager.WIFI_P2P_DISCOVERY_STOPPED) {
            status = status + "Stopped.";
            startPeerDiscovery();
        } else if (state == WifiP2pManager.WIFI_P2P_DISCOVERY_STARTED) {
            status = status + "Started.";
        } else {
            status = status + "unknown  " + state;
        }
        Log.i(TAG, status);
    }

    public List<P2PSyncService> getHighPriorityServiceList() {
        return highPriorityServiceList;
    }

    public void setHighPriorityServiceList(List<P2PSyncService> highPriorityServiceList) {
        this.highPriorityServiceList = highPriorityServiceList;
    }

}
