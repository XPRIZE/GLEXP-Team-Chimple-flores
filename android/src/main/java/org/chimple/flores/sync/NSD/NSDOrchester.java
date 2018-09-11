package org.chimple.flores.sync.NSD;

import android.content.Context;
import android.os.Handler;
import android.util.Log;

import java.net.InetAddress;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

import org.chimple.flores.db.P2PDBApi;
import org.chimple.flores.db.P2PDBApiImpl;
import org.chimple.flores.db.entity.P2PSyncDeviceStatus;
import org.chimple.flores.sync.SyncUtils;
import org.chimple.flores.sync.NSD.NSDSyncManager;
import static org.chimple.flores.sync.SyncUtils.SERVICE_TYPE;

public class NSDOrchester implements NSDHandShakeInitiatorCallBack, NSDWifiConnectionUpdateCallBack {
    private static final String TAG = NSDOrchester.class.getSimpleName();
    private NSDOrchester that = this;
    private SyncUtils.ReportingState reportingState = SyncUtils.ReportingState.NotInitialized;
    private SyncUtils.ConnectionState connectionState = SyncUtils.ConnectionState.NotInitialized;
    private NSDServiceFinder nsdServiceFinder = null;
    private NSDOrchesterCallBack callBack;
    private NSDAccessPoint mWifiAccessPoint = null;
    private Context context;
    private Handler mHandler;
    private NSDHandShakerThread handShakeThread = null;

    public static final String neighboursUpdateEvent = "neighbours-update-event";
    public static final String allMessageExchangedForNSD = "nsd-all-messages-exchanged";

    public NSDOrchester(Context context, NSDOrchesterCallBack callBack, Handler handler) {
        this.context = context;
        this.callBack = callBack;
        this.mHandler = handler;
        this.connectionState = SyncUtils.ConnectionState.NotInitialized;
        this.reportingState = SyncUtils.ReportingState.NotInitialized;
        this.initialize();
    }


    private void initialize() {
        // this cleanUp function can be removed if socket null error occurs.
        cleanUp();
        if (SyncUtils.isWifiConnected(this.context)) {
            setConnectionState(SyncUtils.ConnectionState.WaitingStateChange);
            setListeningState(SyncUtils.ReportingState.WaitingStateChange);
            reStartAll();
            Log.i(TAG, "All stuff available and enabled");
        } else {
            Log.i(TAG, "Wifi NOT available:");
            setConnectionState(SyncUtils.ConnectionState.NotInitialized);
            setListeningState(SyncUtils.ReportingState.NotInitialized);
        }
    }

    private void reStartAll() {
        reStartTheSearch();
        reInitializeNSDAccessPoint();
    }

    private void reInitializeNSDAccessPoint() {

        if (mWifiAccessPoint != null) {
            setListeningState(SyncUtils.ReportingState.Listening);
        } else {
            setListeningState(SyncUtils.ReportingState.Listening);
            mWifiAccessPoint = new NSDAccessPoint(this.context, this.mHandler, this);
        }
    }


    private void reStartTheSearch() {
        setListeningState(SyncUtils.ReportingState.Listening);
        this.stopServiceSearcher();
        this.reInitializeServiceFinder();
    }

    private void reInitializeServiceFinder() {
        Log.i(TAG, "Starting WifiServiceSearcher");
        setConnectionState(SyncUtils.ConnectionState.FindingServices);
        nsdServiceFinder = new NSDServiceFinder(this.context, SERVICE_TYPE, this);
    }

    private void stopServiceSearcher() {
        Log.i(TAG, "stop nsdServiceFinder");
        if (nsdServiceFinder != null) {
            nsdServiceFinder.cleanUp();
            nsdServiceFinder = null;
        }
    }

    private void setConnectionState(SyncUtils.ConnectionState newState) {
        if (connectionState != newState) {
            final SyncUtils.ConnectionState tmpState = newState;
            connectionState = tmpState;
            mHandler.post(new Runnable() {
                @Override
                public void run() {
                    that.callBack.NSDConnectionStateChanged(tmpState);
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
                    that.callBack.NSDListeningStateChanged(tmpState);
                }
            });
        }
    }


    public void cleanUp() {
        Log.i(TAG, "Stopping all");
        if (mWifiAccessPoint != null && mWifiAccessPoint.getmHandShakeListenerThread() != null) {
            mWifiAccessPoint.getmHandShakeListenerThread().cleanUp();
        }
        stopNSDHandShakerThread();
        stopServiceSearcher();
        setConnectionState(SyncUtils.ConnectionState.NotInitialized);
        setListeningState(SyncUtils.ReportingState.NotInitialized);
    }

    @Override
    public void NSDConnected(InetAddress remote, InetAddress local) {
        Connected(remote, false);
    }

    @Override
    public void NSDConnectionFailed(String reason, int trialCount) {
        Log.i(TAG, "NSDConnectionFailed:" + reason);
        this.callBack.ListeningSocketFailed(reason);
    }

    @Override
    public void Connected(InetAddress remote, boolean ListeningStill) {
        Log.i(TAG, "NSD Connected remote: " + remote.getHostAddress());
        Log.i(TAG, "NSD Connected remote ListeningStill: " + ListeningStill);
        final InetAddress remoteTmp = remote;
        final boolean ListeningStillTmp = ListeningStill;

        if (ListeningStill) {
            stopServiceSearcher();
        }

        mHandler.post(new Runnable() {
            @Override
            public void run() {
                stopNSDHandShakerThread();
                that.callBack.NSDConnected(remoteTmp.getHostAddress(), ListeningStillTmp);

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

    private void startNSDHandShakerThread(String Address, int port, int trialNum) {
        Log.i(TAG, "startNSDHandShakerThread addreess: " + Address + ", port : " + port);

        handShakeThread = new NSDHandShakerThread(that, Address, port, trialNum);
        handShakeThread.start();
    }

    private void stopNSDHandShakerThread() {
        Log.i(TAG, "stopNSDHandShakerThread");

        if (handShakeThread != null) {
            handShakeThread.cleanUp();
            handShakeThread = null;
        }
    }


    @Override
    public void processServiceList(List<NSDSyncService> list) {
        Log.i(TAG, "processServiceList:" + list.size());
        synchronized (NSDOrchester.class) {
            P2PDBApi api = P2PDBApiImpl.getInstance(this.context);
            List<String> deviceIds = new ArrayList<String>();
            Map<String, NSDSyncService> nsdServiceList = new HashMap<String, NSDSyncService>();
            if (list != null && list.size() > 0) {
                Iterator<NSDSyncService> items = list.iterator();
                while (items.hasNext()) {
                    NSDSyncService service = (NSDSyncService) items.next();
                    Log.i(TAG, "Selected instance name: " + service.getInstanceName());
                    String[] separated = service.getInstanceName().split(":");
                    String userUUID = separated[0];
                    String deviceUUID = separated[1];
                    Log.i(TAG + "CONTAINS SS:", "found User UUID:" + userUUID + ", found Device UUID:" + deviceUUID + ", found IP ADDRESS:" + service.getDeviceAddress());
                    api.addDeviceToSync(deviceUUID, false);
                    deviceIds.add(deviceUUID);
                    nsdServiceList.put(deviceUUID, service);
                }
                Log.i(TAG, "Selecting from deviceIds: " + deviceIds);
                P2PSyncDeviceStatus status = api.getLatestDeviceToSyncFromDevices(deviceIds);
                NSDSyncService selItem = null;
                if (status != null) {
                    Log.i(TAG, "Selected device: " + status.print());
                    selItem = nsdServiceList.get(status.deviceId);
                    if (selItem != null) {
                        Log.i(TAG, "Selected device address: " + selItem.getInstanceName());
                        String[] separated = selItem.getInstanceName().split(":");
                        String userUUID = separated[0];
                        String deviceUUID = separated[1];
                        Log.i(TAG + " SS:", "found User UUID:" + userUUID);
                        Log.i(TAG + " SS:", "found Device UUID:" + deviceUUID);

                        stopServiceSearcher();
                        setConnectionState(SyncUtils.ConnectionState.Connecting);

                        NSDSyncManager.CURRENT_CONNECTED_DEVICE = deviceUUID;

                        String host = selItem.getDeviceAddress().getHostAddress();
                        int port = selItem.getPort();
                        Log.i(TAG, "Starting to connect now to host:" + host + "and port:" + port);

                        setConnectionState(SyncUtils.ConnectionState.HandShaking);
                        startNSDHandShakerThread(host, port, 0);

                    } else {
                        Log.i(TAG, "No Device found to Connect");
                    }
                }
            } else {
                Log.i(TAG, "No Device found to Connect - size 0");
            }
        }
    }
    
    @Override
    public void serviceUpdateStatus(SyncUtils.DiscoveryState newState) {
        this.callBack.NSDDiscovertyStateChanged(newState);
    }
}