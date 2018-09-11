package org.chimple.flores.sync.Direct;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.net.NetworkInfo;
import android.net.wifi.WifiConfiguration;
import android.net.wifi.WifiInfo;
import android.net.wifi.WifiManager;
import android.os.CountDownTimer;
import android.util.Log;
import android.os.Handler;
import java.util.Timer;
import java.util.TimerTask;

import org.chimple.flores.sync.SyncUtils;

public class P2PWifiConnector {

    private static final String TAG = P2PWifiConnector.class.getSimpleName();
    private boolean hadConnected = false;
    private P2PSyncService currentlyTryingToConnectService = null;

    // Context
    private Context context;

    // WifiManager
    private WifiManager wifiManager = null;

    // Wifi Configuration
    private WifiConfiguration wifiConfig = null;

    int netId = 0;
    private WifiConnectionUpdateCallBack callBack;
    P2PWifiConnectorReceiver receiver;
    //private CountDownTimer connectionTimeOutTimer;
    private Timer connectionTimeOutTimer;
    private TimerTask connectionTimeOutTimerTask;

    String inetAddress = "";


    private P2PWifiConnector that = this;


    private SyncUtils.SyncHandShakeState mConectionState = SyncUtils.SyncHandShakeState.NONE;

    public P2PWifiConnector(Context context, WifiConnectionUpdateCallBack callBack) {
        this.context = context;
        this.callBack = callBack;
        this.init();
    }

    private void init() {
        this.wifiManager = (WifiManager) this.context.getSystemService(Context.WIFI_SERVICE);
        this.registerP2PWifiConnectorReceiver();

        //that.connectionTimeOutTimer = that.setConnectionTimeOutTimer();


    }

    private void registerP2PWifiConnectorReceiver() {
        receiver = new P2PWifiConnectorReceiver();
        IntentFilter intentFilter = new IntentFilter();
        intentFilter.addAction(WifiManager.NETWORK_STATE_CHANGED_ACTION);
        this.context.registerReceiver((receiver), intentFilter);
    }

    private void unregisterP2PWifiConnectorReceiver() {
        if (receiver != null) {
            this.context.unregisterReceiver(receiver);
        }

    }

    private CountDownTimer setConnectionTimeOutTimer() {
        return new CountDownTimer(60000, 1000) {
            public void onTick(long millisUntilFinished) {

            }

            public void onFinish() {
                Log.i(TAG, "Cancelling the connection with timeout");
                mConectionState = SyncUtils.SyncHandShakeState.Disconnected;
                that.setCurrentlyTryingToConnectService(null);
                that.callBack.connectionStatusChanged(mConectionState, null, 0, null);
            }
        };
    }


    public void initialize(String SSID, String password) {

        this.wifiConfig = new WifiConfiguration();
        this.wifiConfig.hiddenSSID = true;
        this.wifiConfig.SSID = String.format("\"%s\"", SSID);
        this.wifiConfig.preSharedKey = String.format("\"%s\"", password);

        this.netId = this.wifiManager.addNetwork(this.wifiConfig);
        this.wifiManager.disconnect();
        this.wifiManager.enableNetwork(this.netId, true);
        this.wifiManager.reconnect();
        this.connectionTimeOutTimer = new Timer();
        that.connectionTimeOutTimerTask = new TimerTask() {
            @Override
            public void run() {
                Log.i(TAG, "Cancelling the connection with timeout");
                mConectionState = SyncUtils.SyncHandShakeState.Disconnected;
                that.setCurrentlyTryingToConnectService(null);
                that.callBack.connectionStatusChanged(mConectionState, null, 0, null);

            }
        };
        connectionTimeOutTimer.schedule(that.connectionTimeOutTimerTask, 60 * 1000);
    }

    public void cleanUp(boolean disconnect) {
        if (this.connectionTimeOutTimer != null) {
            this.connectionTimeOutTimer.cancel();
        }
        this.connectionTimeOutTimer = null;
        this.unregisterP2PWifiConnectorReceiver();
        if (disconnect) {
            this.wifiManager.removeNetwork(this.netId);
            this.wifiManager.disableNetwork(this.netId);
            this.wifiManager.disconnect();
            Log.i(TAG, "Disconnected wifi");
        }
    }

    public void updateInetAddress(String address) {
        this.inetAddress = address;
    }

    public String retrieveInetAddress() {
        return this.inetAddress;
    }

    /**
     * WifiManager.NETWORK_STATE_CHANGED_ACTION
     */

    private class P2PWifiConnectorReceiver extends BroadcastReceiver {

        @Override
        public void onReceive(Context context, Intent intent) {
            that.handleNetworkStateChanged(intent);
        }
    }

    private void handleNetworkStateChanged(Intent intent) {
        String action = intent.getAction();
        if (WifiManager.NETWORK_STATE_CHANGED_ACTION.equals(action)) {
            NetworkInfo info = intent.getParcelableExtra(WifiManager.EXTRA_NETWORK_INFO);
            if (info != null) {
                if (info.isConnected()) {
                    if (this.connectionTimeOutTimer != null) {
                        this.connectionTimeOutTimer.cancel();
                    }
                    this.hadConnected = true;
                    mConectionState = SyncUtils.SyncHandShakeState.Connected;
                } else if (info.isConnectedOrConnecting()) {
                    mConectionState = SyncUtils.SyncHandShakeState.Connecting;
                } else {
                    if (this.hadConnected) {
                        mConectionState = SyncUtils.SyncHandShakeState.Disconnected;
                    } else {
                        mConectionState = SyncUtils.SyncHandShakeState.PreConnecting;
                    }
                }
                this.callBack.connectionStatusChanged(mConectionState, info.getDetailedState(), 0, this.getCurrentlyTryingToConnectService());

            }

            WifiInfo wiffo = intent.getParcelableExtra(WifiManager.EXTRA_WIFI_INFO);
            if (wiffo != null) {
                Log.i(TAG, "ip address" + wiffo.getIpAddress());

                // you could get otherparty IP via:
                // http://stackoverflow.com/questions/10053385/how-to-get-each-devices-ip-address-in-wifi-direct-scenario
                // as well if needed
            }
        }
    }

    public P2PSyncService getCurrentlyTryingToConnectService() {
        return currentlyTryingToConnectService;
    }

    public void setCurrentlyTryingToConnectService(P2PSyncService currentlyTryingToConnectService) {
        this.currentlyTryingToConnectService = currentlyTryingToConnectService;
    }
}