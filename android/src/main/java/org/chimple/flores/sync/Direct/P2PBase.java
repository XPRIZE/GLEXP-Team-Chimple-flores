package org.chimple.flores.sync.Direct;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.net.NetworkInfo;
import android.net.wifi.WifiManager;
import android.net.wifi.p2p.WifiP2pManager;
import android.util.Log;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class P2PBase implements WifiP2pManager.ChannelListener {
    private static final String TAG = P2PBase.class.getSimpleName();

    private boolean wifiDirectEnabledDevice = false;

    // list of devices which are connected (now or sometime in past)
    private List<P2PSyncService> connectedDevices = new ArrayList<P2PSyncService>();

    // Wifi P2P Manager
    private WifiP2pManager wifiP2pManager = null;

    // Wifi Channel
    private WifiP2pManager.Channel channel = null;

    // Context
    private Context context;

    // Receivers
    private P2PBaseReceiver receiver;

    // CallBack
    private WifiConnectionUpdateCallBack callback;


    public P2PBase(Context Context, WifiConnectionUpdateCallBack callback) {
        this.context = Context;
        this.callback = callback;
        this.initialize();
    }

    // Initialize
    private void initialize() {
        this.initializeP2PWifiManager();
        this.registerP2PBaseReceiver();
    }

    private void initializeP2PWifiManager() {
        wifiP2pManager = (WifiP2pManager) this.context.getSystemService(Context.WIFI_P2P_SERVICE);
        if (wifiP2pManager == null) {
            Log.d("P2PBase", "This device does not support Wifi Direct");
            wifiDirectEnabledDevice = false;
        } else {
            wifiDirectEnabledDevice = true;
            channel = wifiP2pManager.initialize(this.context, this.context.getMainLooper(), this);
        }
    }

    private void registerP2PBaseReceiver() {
        receiver = new P2PBaseReceiver();
        IntentFilter intentFilter = new IntentFilter();
        intentFilter.addAction(WifiP2pManager.WIFI_P2P_CONNECTION_CHANGED_ACTION);
        intentFilter.addAction(WifiP2pManager.WIFI_P2P_STATE_CHANGED_ACTION);
        this.context.registerReceiver((receiver), intentFilter);
    }

    private void unregisterP2PBaseReceiver() {
        if (receiver != null) {
            this.context.unregisterReceiver(receiver);
            receiver = null;
        }

    }

    public boolean isWifiEnabled() {
        WifiManager wifiManager = (WifiManager) this.context.getSystemService(Context.WIFI_SERVICE);
        if (wifiManager != null) {
            return wifiManager.isWifiEnabled();
        } else {
            return false;
        }
    }

    public void setWifiEnabled(boolean enabled) {
        WifiManager wifiManager = (WifiManager) this.context.getSystemService(Context.WIFI_SERVICE);
        if (wifiManager != null && !wifiManager.isWifiEnabled()) {
            wifiManager.setWifiEnabled(enabled);
        }
    }

    public boolean isWifiDirectEnabledDevice() {
        return wifiDirectEnabledDevice;
    }

    // CleanUp
    public void cleanUp() {
        this.unregisterP2PBaseReceiver();
    }

    // P2P

    @Override
    public void onChannelDisconnected() {

    }

    public WifiP2pManager.Channel getP2PChannel() {
        return channel;
    }

    public WifiP2pManager getWifiP2pManager() {
        return wifiP2pManager;
    }


    /**
     * WIFI_P2P_CONNECTION_CHANGED_ACTION
     * WIFI_P2P_STATE_CHANGED_ACTION
     */
    private class P2PBaseReceiver extends BroadcastReceiver {
        @Override
        public void onReceive(Context context, Intent intent) {
            String action = intent.getAction();
            if (WifiP2pManager.WIFI_P2P_STATE_CHANGED_ACTION.equals(action)) {
                int state = intent.getIntExtra(WifiP2pManager.EXTRA_WIFI_STATE, -1);
                if (callback != null) {
                    callback.handleWifiP2PStateChange(state);
                }
            } else if (WifiP2pManager.WIFI_P2P_CONNECTION_CHANGED_ACTION.equals(action)) {
                if (callback != null) {
                    NetworkInfo networkInfo = (NetworkInfo) intent.getParcelableExtra(WifiP2pManager.EXTRA_NETWORK_INFO);
                    callback.handleWifiP2PConnectionChange(networkInfo);
                }
            }
        }
    }


    public List<P2PSyncService> connectedDevices() {
        return this.connectedDevices;
    }

    // TO DO - Unit Test Required
    public P2PSyncService selectServiceToConnect(List<P2PSyncService> available, List<P2PSyncService> highPriorityDevices) {

        P2PSyncService ret = null;

        List<P2PSyncService> list = Collections.synchronizedList(this.connectedDevices);

        synchronized (list) {

            if (highPriorityDevices != null) {
                available.addAll(0, highPriorityDevices);
            }

            if (list.size() > 0 && available.size() > 0) {

                int firstNewMatch = -1;
                int firstOldMatch = -1;

                for (int i = 0; i < available.size(); i++) {
                    if (firstNewMatch >= 0) {
                        break;
                    }
                    for (int ii = 0; ii < list.size(); ii++) {
                        if (available.get(i).getDeviceAddress().equals(list.get(ii).getDeviceAddress())) {
                            if (firstOldMatch < 0 || firstOldMatch > ii) {
                                //find oldest one available that we have connected previously
                                firstOldMatch = ii;
                            }
                            firstNewMatch = -1;
                            break;
                        } else {
                            if (firstNewMatch < 0) {
                                firstNewMatch = i; // select first not connected device
                            }
                        }
                    }
                }

                if (firstNewMatch >= 0) {
                    ret = available.get(firstNewMatch);
                } else if (firstOldMatch >= 0) {
                    ret = list.get(firstOldMatch);
                    // we move this to last position
                    list.remove(firstOldMatch);
                }

                Log.i(TAG + "EEE", "firstNewMatch " + firstNewMatch + ", firstOldMatch: " + firstOldMatch);

            } else if (available.size() > 0) {
                ret = available.get(0);
                Log.i(TAG + "EEE", "selecting first available address:" + ret.getDeviceAddress() + " name:" + ret.getDeviceName());
            }
            if (ret != null) {
                list.add(ret);

                //remove ret if exists in highPriorityDevices - may be connection is confirmed!!!?
                if(highPriorityDevices.contains(ret)) {
                    highPriorityDevices.remove(ret);
                }

                Log.i(TAG + "EEE", "adding to connected devices address" + ret.getDeviceAddress() + " name:" + ret.getDeviceName());
                // just to set upper limit for the amount of remembered contacts
                // when we have 101, we remove the oldest (that's the top one)
                // from the array
                if (list.size() > 100) {
                    list.remove(0);
                }
            }
            Log.i(TAG + "EEE", "Chosed connected devices address" + ret.getDeviceAddress() + " name:" + ret.getDeviceName());
            return ret;
        }
    }
}
