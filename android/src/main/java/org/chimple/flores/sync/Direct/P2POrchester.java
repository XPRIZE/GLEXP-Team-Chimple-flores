package org.chimple.flores.sync.Direct;

import android.content.Context;
import android.content.Intent;
import android.net.NetworkInfo;
import android.net.wifi.p2p.WifiP2pDevice;
import android.net.wifi.p2p.WifiP2pGroup;
import android.net.wifi.p2p.WifiP2pManager;
import android.os.CountDownTimer;
import android.os.Handler;
import android.os.Looper;
import android.support.v4.content.LocalBroadcastManager;
import android.util.Log;


import java.net.InetAddress;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Timer;
import java.util.TimerTask;
import java.util.UUID;

import org.chimple.flores.db.P2PDBApi;
import org.chimple.flores.db.P2PDBApiImpl;
import org.chimple.flores.db.entity.P2PSyncDeviceStatus;
import org.chimple.flores.sync.SyncUtils;

import static org.chimple.flores.sync.SyncUtils.HandShakeportToUse;
import static org.chimple.flores.sync.SyncUtils.SERVICE_TYPE;

public class P2POrchester implements HandShakeInitiatorCallBack, WifiConnectionUpdateCallBack {
    private static final String TAG = P2POrchester.class.getSimpleName();
    private P2POrchester that = this;
    private boolean wifiIsEnabled = false;


    private SyncUtils.ReportingState reportingState = SyncUtils.ReportingState.NotInitialized;
    private SyncUtils.ConnectionState connectionState = SyncUtils.ConnectionState.NotInitialized;

    private P2PBase mWifiBase = null;
    private P2PServiceFinder mWifiServiceSearcher = null;
    private P2PWifiConnector mWifiConnection = null;
    private P2PAccessPoint mWifiAccessPoint = null;
    HandShakerThread handShakeThread = null;
    private P2POrchesterCallBack callBack = null;
    private Context context = null;
    private Handler mHandler = null;
    // private CountDownTimer serviceFoundTimeOutTimer;
    private TimerTask serviceFoundTimeOutTimerTask;
    private Timer serviceFoundTimer;

    public static final String neighboursUpdateEvent = "neighbours-update-event";
    public static final String allMessageExchangedForP2P = "p2p-all-messages-exchanged";

    public P2POrchester(Context context, P2POrchesterCallBack callBack, Handler handler) {
        Log.i(TAG, "in P2P Orchester constructor");
        this.context = context;
        this.callBack = callBack;
        this.mHandler = handler;
        this.connectionState = SyncUtils.ConnectionState.NotInitialized;
        this.reportingState = SyncUtils.ReportingState.NotInitialized;

//        this.serviceFoundTimeOutTimer = new CountDownTimer(4 * 60 * 1000, 1000) {
//            public void onTick(long millisUntilFinished) {
//                // not using
//            }
//
//            public void onFinish() {
//                Log.i(TAG, "serviceFoundTimeOutTimer timeout");
//                reStartTheSearch();
//            }
//        };


        this.initialize();
    }


    private void initialize() {
        cleanUp();
        //initialize the system, and
        // make sure Wifi is enabled before we start running
        mWifiBase = new P2PBase(this.context, this);
        boolean isWifiDirectEnabled = mWifiBase.isWifiDirectEnabledDevice();

        if (!isWifiDirectEnabled) {
            Log.i(TAG, "Wifi Direct NOT available:");
            wifiIsEnabled = false;
            setConnectionState(SyncUtils.ConnectionState.NotInitialized);
            setListeningState(SyncUtils.ReportingState.NotInitialized);
        } else if (mWifiBase.isWifiEnabled()) {
            Log.i(TAG, "All stuff available and enabled");
            wifiIsEnabled = true;
            reStartAll();
        } else {
            wifiIsEnabled = false;
            setConnectionState(SyncUtils.ConnectionState.WaitingStateChange);
            setListeningState(SyncUtils.ReportingState.WaitingStateChange);
        }
    }

    public void cleanUp() {
        Log.i(TAG, "Stopping all");
        if (mWifiAccessPoint != null && mWifiAccessPoint.getmHandShakeListenerThread() != null) {
            mWifiAccessPoint.getmHandShakeListenerThread().cleanUp();
        }
        stopHandShakerThread();
        stopWifiConnection();
        stopWifiAccessPoint();
        stopServiceSearcher();
        stopWifiBase();
        setConnectionState(SyncUtils.ConnectionState.NotInitialized);
        setListeningState(SyncUtils.ReportingState.NotInitialized);
    }


    private void reStartAll() {
        reStartTheSearch();
        reInitializeP2PAccessPoint();
    }

    private void reStartTheSearch() {
//        if (this.serviceFoundTimeOutTimer != null) {
//            this.serviceFoundTimeOutTimer.cancel();
//        }

        if (this.serviceFoundTimer != null) {
            this.serviceFoundTimer.cancel();
            this.serviceFoundTimer = null;            
        }

        if (this.serviceFoundTimeOutTimerTask != null) {
            this.serviceFoundTimeOutTimerTask.cancel();
            this.serviceFoundTimeOutTimerTask = null;            
        }

        //to get fresh situation, lets close all stuff before continuing
        stopServiceSearcher();
        reInitializeServiceFinder();
    }

    private void reInitializeServiceFinder() {
        WifiP2pManager.Channel channel = null;
        WifiP2pManager p2p = null;
        if (mWifiBase != null) {
            channel = mWifiBase.getP2PChannel();
            p2p = mWifiBase.getWifiP2pManager();

        }

        if (channel != null && p2p != null && mWifiServiceSearcher == null) {
            Log.i(TAG, "Starting WifiServiceSearcher");
            setConnectionState(SyncUtils.ConnectionState.FindingPeers);
            mWifiServiceSearcher = new P2PServiceFinder(this.context, p2p, channel, this, SERVICE_TYPE);
        }

    }

    private void reInitializeP2PAccessPoint() {

        if (mWifiAccessPoint != null) {
            setListeningState(SyncUtils.ReportingState.Listening);
        } else {
            if (mWifiBase != null) {
                WifiP2pManager.Channel channel = mWifiBase.getP2PChannel();
                WifiP2pManager p2p = mWifiBase.getWifiP2pManager();

                setListeningState(SyncUtils.ReportingState.Listening);
                mWifiAccessPoint = new P2PAccessPoint(this.context, p2p, channel, this.mHandler, this);
            }
        }
    }

    private void startHandShakerThread(String Address, int trialNum) {
        Log.i(TAG, "startHandShakerThread addreess: " + Address + ", port : " + HandShakeportToUse);

        handShakeThread = new HandShakerThread(that, Address, HandShakeportToUse, trialNum);
        handShakeThread.start();
    }

    private void stopHandShakerThread() {
        Log.i(TAG, "stopHandShakerThread");

        if (handShakeThread != null) {
            handShakeThread.cleanUp();
            handShakeThread = null;
        }
    }


    private void stopWifiAccessPoint() {
        Log.i(TAG, "stopWifiAccessPoint");

        if (mWifiAccessPoint != null) {
            mWifiAccessPoint.cleanUp();
            mWifiAccessPoint = null;
        }
    }

    private void stopWifiConnection() {
        Log.i(TAG, "stopWifiConnection");

        if (mWifiConnection != null) {
            mWifiConnection.cleanUp(true); // do we need to disconnect ?
            mWifiConnection = null;
        }
    }

    private void stopServiceSearcher() {
        Log.i(TAG, "stopServiceSearcher");

//        if (this.serviceFoundTimeOutTimer != null) {
//            this.serviceFoundTimeOutTimer.cancel();
//        }

        if (mWifiServiceSearcher != null) {
            mWifiServiceSearcher.cleanUp();
            mWifiServiceSearcher = null;
        }
    }

    private void stopWifiBase() {
        Log.i(TAG, "stopWifiBase");

        if (mWifiBase != null) {
            mWifiBase.cleanUp();
            mWifiBase = null;
        }
    }


    private void setConnectionState(SyncUtils.ConnectionState newState) {
        if (connectionState != newState) {
            final SyncUtils.ConnectionState tmpState = newState;
            connectionState = tmpState;
            mHandler.post(new Runnable() {
                @Override
                public void run() {
                    that.callBack.ConnectionStateChanged(tmpState);
                }
            });
        }
    }

    private void setListeningState(SyncUtils.ReportingState newState) {
        if (reportingState != newState) {
            final SyncUtils.ReportingState tmpState = newState;
            reportingState = tmpState;
            mHandler.post(new Runnable() {
                @Override
                public void run() {
                    that.callBack.ListeningStateChanged(tmpState);
                }
            });
        }
    }


    // interfaces

    @Override
    public void Connected(InetAddress remote, InetAddress local) {
        Connected(remote, false);
    }

    @Override
    public void ConnectionFailed(String reason, int trialCount) {
        final int trialCountTmp = trialCount;
        final String reasonTmp = reason;
        mHandler.post(new Runnable() {
            @Override
            public void run() {
                Log.i(TAG + " HandSS:", "HandShake(" + trialCountTmp + ") ConnectionFailed " + reasonTmp);
                //lets do 3 re-tries, we could also have logic that waits that remove group is finihed before
                // doing handshake, or we could try getting it removed earlier
                // anyhow, untill we change the ways, we might get Connection refuced, since our listening is cancelled
                // but our group, might still be having same IP as the remote party has :)
                if (trialCountTmp < 2) {
                    final Handler handler = new Handler();
                    handler.postDelayed(new Runnable() {
                        //There are supposedly a possible race-condition bug with the service discovery
                        // thus to avoid it, we are delaying the service discovery start here
                        public void run() {
                            if (mWifiConnection != null) {
                                Log.i(TAG + "shake:", "re shaking.");
                                String address = mWifiConnection.retrieveInetAddress();
                                setConnectionState(SyncUtils.ConnectionState.HandShaking);
                                startHandShakerThread(address, (trialCountTmp + 1));
                            }
                        }
                    }, 2000);
                } else {
                    connectionStatusChanged(SyncUtils.SyncHandShakeState.ConnectingFailed, null, 123456, null);
                }
            }
        });
    }

    @Override
    public void handleWifiP2PStateChange(int state) {
        if (state == WifiP2pManager.WIFI_P2P_STATE_ENABLED) {
            Log.i(TAG + " WB:", "Wifi is now enabled !");

            // to avoid getting this event on starting, we we already know the state
//            if (!wifiIsEnabled) {
//                reStartAll();
//            }
            wifiIsEnabled = true;
        } else {
            //no wifi availavble, thus we need to stop doing anything;
            Log.i(TAG + " WB:", "Wifi is DISABLEd !!");

            wifiIsEnabled = false;
            stopServiceSearcher();
            stopWifiAccessPoint();

            // indicate the waiting with state change
            setConnectionState(SyncUtils.ConnectionState.WaitingStateChange);
            setListeningState(SyncUtils.ReportingState.WaitingStateChange);
        }
    }

    @Override
    public void handleWifiP2PConnectionChange(NetworkInfo networkInfo) {
        if (networkInfo.isConnected()) {
            Log.i(TAG, "We are CONNECTED, will check info now");
        } else {
            NetworkInfo.DetailedState status = networkInfo.getDetailedState();
            if (status == NetworkInfo.DetailedState.FAILED
                    || status == NetworkInfo.DetailedState.DISCONNECTED) {
                if (status == NetworkInfo.DetailedState.FAILED) {
                    Log.i(TAG, "P2P Connection Change => FAILED, status: " + status);
                } else {
                    Log.i(TAG, "P2P Connection Change => DISCONNECTED, status: " + status);
                }
                Log.i(TAG, "P2P Connection Change =>  status: " + status);
            } else {
                Log.i(TAG, "P2P Connection Change => Not Connected: " + status);
            }
        }
    }

    @Override
    public Map<String, P2PSyncService> foundNeighboursList(List<P2PSyncService> list) {
        HashMap<String, P2PSyncService> neighbours = new HashMap<String, P2PSyncService>();
        List<P2PSyncService> devices = Collections.synchronizedList(list);
        synchronized (devices) {
            if (devices != null && devices.size() > 0) {
                for (P2PSyncService device : devices) {
                    Log.i(TAG, "foundNeighboursList Selected device address: " + device.getInstanceName());
                    String[] separated = device.getInstanceName().split(":");
                    String userUUID = separated[0];
                    String deviceUUID = separated[1];
                    Log.i(TAG + "foundNeighboursList SS:", "found User UUID:" + userUUID);
                    Log.i(TAG + "foundNeighboursList SS:", "found User deviceUUID:" + deviceUUID);
                    Log.i(TAG + "foundNeighboursList SS:", "found SSID:" + separated[2] + ", pwd:" + separated[3] + "IP: " + separated[4]);
                    neighbours.put(userUUID, device);
                }
            }
            this.broadcastNeighboursUpdatedEvent(neighbours);
        }
        return neighbours;
    }

    private void broadcastNeighboursUpdatedEvent(HashMap<String, P2PSyncService> neighbours) {
        Log.d("sender", "Broadcasting message NeighboursUpdatedEvent");
        Intent intent = new Intent(neighboursUpdateEvent);
        intent.putExtra("neighbours", neighbours);
        LocalBroadcastManager.getInstance(this.context).sendBroadcast(intent);
    }


    @Override
    public void processServiceList(List<P2PSyncService> list) {
        synchronized (P2POrchester.class) {
            P2PDBApi api = P2PDBApiImpl.getInstance(this.context);
            List<String> deviceIds = new ArrayList<String>();
            Map<String, P2PSyncService> serviceList = new HashMap<String, P2PSyncService>();
            if (mWifiBase != null && list != null && list.size() > 0) {
                Iterator<P2PSyncService> items = list.iterator();
                while (items.hasNext()) {
                    P2PSyncService service = (P2PSyncService) items.next();
                    Log.i(TAG, "Selected device address: " + service.getInstanceName());
                    String[] separated = service.getInstanceName().split(":");
                    String userUUID = separated[0];
                    String deviceUUID = separated[1];
                    Log.i(TAG + "CONTAINS SS:", "found User UUID:" + userUUID + ", found Device UUID:" + deviceUUID + ", found SSID:" + separated[2] + ", pwd:" + separated[3] + "IP: " + separated[4]);
                    api.addDeviceToSync(deviceUUID, false);
                    deviceIds.add(deviceUUID);
                    serviceList.put(deviceUUID, service);
                }

                if (mWifiConnection != null) {
                    Log.i(TAG, "Already connecting !!");
                    return;
                } else if (deviceIds != null){
                    Log.i(TAG, "Selecting from deviceIds: " + deviceIds);
                    P2PSyncDeviceStatus status = api.getLatestDeviceToSyncFromDevices(deviceIds);
                    P2PSyncService selItem = null;
                    if (status != null) {
                        Log.i(TAG, "Selected device: " + status.print());
                        selItem = serviceList.get(status.deviceId);
                        if (selItem != null) {
                            Log.i(TAG, "Selected device address: " + selItem.getInstanceName());
                            String[] separated = selItem.getInstanceName().split(":");
                            String userUUID = separated[0];
                            String deviceUUID = separated[1];
                            Log.i(TAG + " SS:", "found User UUID:" + userUUID);
                            Log.i(TAG + " SS:", "found Device UUID:" + deviceUUID);
                            Log.i(TAG + " SS:", "found SSID:" + separated[2] + ", pwd:" + separated[3] + "IP: " + separated[4]);

//                            stopServiceSearcher();

                            setConnectionState(SyncUtils.ConnectionState.Connecting);

                            final String networkSSID = separated[2];
                            final String networkPass = separated[3];
                            final String ipAddress = separated[4];

                            P2PSyncManager.CURRENT_CONNECTED_DEVICE = deviceUUID;

                            Log.i(TAG, "Starting to connect now.");
                            mWifiConnection = new P2PWifiConnector(that.context, that);
                            mWifiConnection.setCurrentlyTryingToConnectService(selItem);
                            mWifiConnection.updateInetAddress(ipAddress);
                            mWifiConnection.initialize(networkSSID, networkPass);
                        } else {
                            // we'll get discovery stopped event soon enough
                            // and it starts the discovery again, so no worries :)
                            Log.i(TAG, "No devices selected");
                            mWifiConnection.setCurrentlyTryingToConnectService(null);
                        }
                    }
                }
            }
        }
    }

    public boolean gotPeersList(Collection<WifiP2pDevice> list) {

        boolean cont = true;
        if (mWifiConnection != null) {
            Log.i(TAG, "gotPeersList, while connecting!!");
            cont = false;
        } else {
            if (that.serviceFoundTimer != null) {
                that.serviceFoundTimer.cancel();
                that.serviceFoundTimer = null;
            }

            if (that.serviceFoundTimeOutTimerTask != null) {
                that.serviceFoundTimeOutTimerTask.cancel();
                that.serviceFoundTimeOutTimerTask = null;
            }

            that.serviceFoundTimer = new Timer("Service Found Timer" + UUID.randomUUID());
            this.serviceFoundTimeOutTimerTask = new TimerTask() {
                @Override
                public void run() {
                    Log.i(TAG, "gotPeersList => serviceFoundTimeOutTimerTask starting....");
                    // need to clear the connection here.
                    stopHandShakerThread();
                    stopWifiConnection();

                    // to make sure advertising is ok, lets clear the old out at this point
                    stopWifiAccessPoint();

                    // we have no connections, so lets make sure we do advertise us, as well as do active discovery
                    reStartAll();
                }
            };
            that.serviceFoundTimer.schedule(that.serviceFoundTimeOutTimerTask, 2 * 60 * 1000);
            Log.i(TAG + " SS:", "Found " + list.size() + " peers.");
            int numm = 0;
            for (WifiP2pDevice peer : list) {
                numm++;
                Log.i(TAG + " SS:", "Peer(" + numm + "): " + peer.deviceName + " " + peer.deviceAddress);
            }

            setConnectionState(SyncUtils.ConnectionState.FindingServices);
        }
        return cont;
    }

    @Override
    public void GroupInfoAvailable(WifiP2pGroup group) {
        Log.i(TAG + " CONN:", "GroupInfoAvailable: " + group.getNetworkName() + " ,cunt: " + group.getClientList().size());
        //do we have connections to our Group

        this.callBack.GroupInfoChanged(group);

        if (group.getClientList().size() > 0) {
            // we are ok, we just got new connections coming aiin
        } else {
            // note that we get this when we create a new group, so we also do need to check what the state is we are in

            // if we got zero clients then we are  not conncted anymore, so we can start doing stuff
            if (reportingState == SyncUtils.ReportingState.ConnectedAndListening) {

//                reInitializeP2PAccessPoint();
//
//                //also if we did stop all searching, lets start it again
//                if (connectionState == SyncUtils.ConnectionState.Idle) {
//                    reStartTheSearch();
//                }

                // need to clear the connection here.
                stopHandShakerThread();
                stopWifiConnection();

                // to make sure advertising is ok, lets clear the old out at this point
                stopWifiAccessPoint();

                // we have no connections, so lets make sure we do advertise us, as well as do active discovery
                reStartAll();
            }

        }
    }

    @Override
    public void connectionStatusChanged(SyncUtils.SyncHandShakeState
                                                state, NetworkInfo.DetailedState detailedState, int Error, P2PSyncService currentDevice) {
        Log.i(TAG + " COM:", "State " + state + ", detailed state: " + detailedState + " , Error: " + Error);
//        Log.i(TAG, "currentDevice info:" + currentDevice.print());
        String conStatus = "";
        if (state == SyncUtils.SyncHandShakeState.NONE) {
            conStatus = "NONE";
        } else if (state == SyncUtils.SyncHandShakeState.Connecting) {
            conStatus = "Connecting";
            setConnectionState(SyncUtils.ConnectionState.Connecting);
        } else if (state == SyncUtils.SyncHandShakeState.PreConnecting) {
            conStatus = "PreConnecting";
            setConnectionState(SyncUtils.ConnectionState.Connecting);
        } else if (state == SyncUtils.SyncHandShakeState.Connected) {
            conStatus = "Connected";
            if (mWifiConnection != null && handShakeThread == null) {
                String address = mWifiConnection.retrieveInetAddress();
                if (mWifiServiceSearcher != null && currentDevice != null && mWifiServiceSearcher.getHighPriorityServiceList().contains(currentDevice)) {
                    mWifiServiceSearcher.getHighPriorityServiceList().remove(currentDevice);
                }
                stopServiceSearcher();
                stopWifiAccessPoint();
                setListeningState(SyncUtils.ReportingState.Idle);

                setConnectionState(SyncUtils.ConnectionState.HandShaking);
                startHandShakerThread(address, 0);
            } else {
                conStatus = "already handshaking";
            }
        } else if (state == SyncUtils.SyncHandShakeState.DisConnecting) {
            conStatus = "DisConnecting";
            setConnectionState(SyncUtils.ConnectionState.Disconnecting);
        } else if (state == SyncUtils.SyncHandShakeState.ConnectingFailed
                || state == SyncUtils.SyncHandShakeState.Disconnected) {
            Log.i(TAG + "CON", "We are disconnected, re-starting the search");
            setConnectionState(SyncUtils.ConnectionState.Disconnected);
            conStatus = "Disconnected";
            // need to clear the connection here.
            stopHandShakerThread();
            stopWifiConnection();

            // to make sure advertising is ok, lets clear the old out at this point
            stopWifiAccessPoint();

            // we have no connections, so lets make sure we do advertise us, as well as do active discovery
            reStartAll();
        }

        Log.i(TAG + " COM:", "State change-out with status : " + conStatus);
    }

    @Override
    public void Connected(InetAddress remote, boolean ListeningStill) {
        final InetAddress remoteTmp = remote;
        final boolean ListeningStillTmp = ListeningStill;

        if (ListeningStill) {
            stopWifiConnection();
            stopServiceSearcher();
        }

        mHandler.post(new Runnable() {
            @Override
            public void run() {
//                stopHandShakerThread();

                that.callBack.Connected(remoteTmp.getHostAddress(), ListeningStillTmp);

                if (ListeningStillTmp) {
                    // we did not make connection, not would we attempt to make any new ones
                    // we are just listening for more connections, until we lose all connected clients
                    setConnectionState(SyncUtils.ConnectionState.Idle);
                    setListeningState(SyncUtils.ReportingState.ConnectedAndListening);
                } else {
                    setConnectionState(SyncUtils.ConnectionState.Connected);
                    //Clients can not accept connections, thus we are not listening for more either
                    setListeningState(SyncUtils.ReportingState.Idle);
                }


            }
        });
    }

    // TO DO - Unit Test required
    public void addHighPriorityConnection(P2PSyncService device) {
        synchronized (this) {
            if (mWifiServiceSearcher != null && mWifiBase != null && mWifiServiceSearcher.serviceList() != null) {
                mWifiBase.connectedDevices().remove(device);
                //add only if doesn't exists
                if (!mWifiServiceSearcher.getHighPriorityServiceList().contains(device)) {
                    mWifiServiceSearcher.getHighPriorityServiceList().add(0, device);
                }
            }
        }
    }
}