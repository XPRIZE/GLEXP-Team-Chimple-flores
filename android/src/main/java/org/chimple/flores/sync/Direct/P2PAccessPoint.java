package org.chimple.flores.sync.Direct;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.net.NetworkInfo;
import android.net.wifi.p2p.WifiP2pGroup;
import android.net.wifi.p2p.WifiP2pInfo;
import android.net.wifi.p2p.WifiP2pManager;
import android.net.wifi.p2p.nsd.WifiP2pDnsSdServiceInfo;
import android.os.Handler;
import android.util.Log;

import java.lang.annotation.Target;
import java.lang.reflect.Method;
import java.net.InetAddress;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;

import static org.chimple.flores.sync.Direct.P2PSyncManager.P2P_SHARED_PREF;
import static org.chimple.flores.sync.SyncUtils.HandShakeportToUse;
import static org.chimple.flores.sync.SyncUtils.SERVICE_TYPE;

public class P2PAccessPoint implements HandShakeListenerCallBack, WifiP2pManager.ConnectionInfoListener, WifiP2pManager.GroupInfoListener {

    private static final String TAG = P2PAccessPoint.class.getSimpleName();
    private P2PAccessPoint that = this;

    private Context context;
    private WifiP2pManager wifiP2pManager;
    private WifiP2pManager.Channel channel;

    private WifiConnectionUpdateCallBack callBack;
    private Handler mHandler = null;

    String mNetworkName = "";
    String mPassphrase = "";
    String mInetAddress = "";

    int lastError = -1;

    // Receivers
    private P2PAccessPointReceiver receiver;

    // Handlers
    private HandShakeListenerThread mHandShakeListenerThread = null;

    public P2PAccessPoint(Context Context, WifiP2pManager Manager, WifiP2pManager.Channel Channel, Handler handler, WifiConnectionUpdateCallBack callBack) {
        Log.i(TAG, "P2PAccessPoint constructor");
        this.context = Context;
        this.wifiP2pManager = Manager;
        this.channel = Channel;
        this.callBack = callBack;
        this.mHandler = handler;
        this.initialize();
    }

    public int GetLastError() {
        return lastError;
    }


    private void initialize() {
        this.registerP2PAccessPointReceiver();
        this.reStartHandShakeListening(0);

        wifiP2pManager.createGroup(channel, new WifiP2pManager.ActionListener() {
            public void onSuccess() {
                lastError = -1;
                Log.i(TAG, "Creating Local Group ");
            }

            public void onFailure(int reason) {
                lastError = reason;
                Log.i(TAG, "Local Group failed, error code " + reason);
            }
        });
    }

    private void registerP2PAccessPointReceiver() {
        receiver = new P2PAccessPointReceiver();
        IntentFilter intentFilter = new IntentFilter();
        intentFilter.addAction(WifiP2pManager.WIFI_P2P_CONNECTION_CHANGED_ACTION);
        this.context.registerReceiver((receiver), intentFilter);
    }

    private void unregisterP2PAccessPointReceiver() {
        if (receiver != null) {
            this.context.unregisterReceiver(receiver);
            receiver = null;
        }

    }


    @Override
    public void onConnectionInfoAvailable(WifiP2pInfo info) {
        try {
            if (info.isGroupOwner) {
                if (info.groupOwnerAddress != null) {
                    mInetAddress = info.groupOwnerAddress.getHostAddress();
                }

                wifiP2pManager.requestGroupInfo(channel, this);
            } else {
                Log.i(TAG, "we are client !! group owner address is: " + info.groupOwnerAddress.getHostAddress());
            }
        } catch (Exception e) {
            Log.i(TAG, "onConnectionInfoAvailable, error: " + e.toString());
        }
    }

    @Override
    public void onGroupInfoAvailable(WifiP2pGroup group) {
        try {
            this.callBack.GroupInfoAvailable(group);

            if (mNetworkName.equals(group.getNetworkName()) && mPassphrase.equals(group.getPassphrase())) {
                Log.i(TAG, "Already have local service for " + mNetworkName + " ," + mPassphrase);
            } else {
                mNetworkName = group.getNetworkName();
                mPassphrase = group.getPassphrase();
                SharedPreferences pref = this.context.getSharedPreferences(P2P_SHARED_PREF, 0);
                String userId = pref.getString("USER_ID", null); // getting String
                String deviceId = pref.getString("DEVICE_ID", null); // getting String
                startLocalService(userId + ":" + deviceId + ":" + group.getNetworkName() + ":" + group.getPassphrase() + ":" + mInetAddress);
            }
        } catch (Exception e) {
            Log.i(TAG, "onGroupInfoAvailable, error: " + e.toString());
        }
    }


    public void cleanUp() {
        Log.i(TAG, "Stop WifiAccessPoint");
        this.unregisterP2PAccessPointReceiver();
        if (mHandShakeListenerThread != null) {
            mHandShakeListenerThread.cleanUp();
            mHandShakeListenerThread = null;
        }
        stopLocalServices();
        removeGroup();
        removePersistentGroups();
        deletePersistentInfo();
    }

    public void removeGroup() {
        if (wifiP2pManager != null && channel != null) {
            Log.i(TAG, "Calling removeGroup");
            wifiP2pManager.removeGroup(channel, new WifiP2pManager.ActionListener() {
                @Override
                public void onSuccess() {
                    lastError = -1;
                    Log.i(TAG, "removeGroup onSuccess -");
                }

                @Override
                public void onFailure(int reason) {
                    lastError = reason;
                    Log.i(TAG, "removeGroup onFailure -" + reason);
                }
            });
        }
    }

    /**
     * Removes persistent/remembered groups
     * <p>
     * Source: https://android.googlesource.com/platform/cts/+/jb-mr1-dev%5E1%5E2..jb-mr1-dev%5E1/
     * Author: Nick  Kralevich <nnk@google.com>
     * <p>
     * WifiP2pManager.java has a method deletePersistentGroup(), but it is not accessible in the
     * SDK. According to Vinit Deshpande <vinitd@google.com>, it is a common Android paradigm to
     * expose certain APIs in the SDK and hide others. This allows Android to maintain stability and
     * security. As a workaround, this removePersistentGroups() method uses Java reflection to call
     * the hidden method. We can list all the methods in WifiP2pManager and invoke "deletePersistentGroup"
     * if it exists. This is used to remove all possible persistent/remembered groups.
     */
    public void removePersistentGroups() {
        try {
            Method[] methods = WifiP2pManager.class.getMethods();
            for (int i = 0; i < methods.length; i++) {
                if (methods[i].getName().equals("deletePersistentGroup")) {
                    // Remove any persistent group
                    for (int netid = 0; netid < 32; netid++) {
                        methods[i].invoke(wifiP2pManager, channel, netid, null);
                        Log.i(TAG, "deletePersistentGroup groups netid:" + netid);
                    }
                }
            }
            Log.i(TAG, "Persistent groups removed");
        } catch (Exception e) {
            Log.e(TAG, "Failure removing persistent groups: " + e.getMessage());
            e.printStackTrace();
        }
    }


    public void deletePersistentInfo() {
        try {

            Class persistentInterface = null;

            //Iterate and get class PersistentGroupInfoListener
            for (Class<?> classR : WifiP2pManager.class.getDeclaredClasses()) {
                if (classR.getName().contains("PersistentGroupInfoListener")) {
                    persistentInterface = classR;
                    break;
                }

            }

            final Method deletePersistentGroupMethod = WifiP2pManager.class.getDeclaredMethod("deletePersistentGroup", new Class[]{WifiP2pManager.Channel.class, int.class, WifiP2pManager.ActionListener.class});

            //anonymous class to implement PersistentGroupInfoListener which has a method, onPersistentGroupInfoAvailable
            Object persitentInterfaceObject =
                    java.lang.reflect.Proxy.newProxyInstance(persistentInterface.getClassLoader(),
                            new java.lang.Class[]{persistentInterface},
                            new java.lang.reflect.InvocationHandler() {
                                @Override
                                public Object invoke(Object proxy, java.lang.reflect.Method method, Object[] args) throws java.lang.Throwable {
                                    String method_name = method.getName();

                                    if (method_name.equals("onPersistentGroupInfoAvailable")) {
                                        Class wifiP2pGroupListClass = Class.forName("android.net.wifi.p2p.WifiP2pGroupList");
                                        Object wifiP2pGroupListObject = wifiP2pGroupListClass.cast(args[0]);

                                        Collection<WifiP2pGroup> wifiP2pGroupList = (Collection<WifiP2pGroup>) wifiP2pGroupListClass.getMethod("getGroupList", null).invoke(wifiP2pGroupListObject, null);
                                        for (WifiP2pGroup group : wifiP2pGroupList) {
                                            deletePersistentGroupMethod.invoke(wifiP2pManager, channel, (Integer) WifiP2pGroup.class.getMethod("getNetworkId").invoke(group, null), new WifiP2pManager.ActionListener() {
                                                @Override
                                                public void onSuccess() {
                                                    Log.i(TAG, "Persistent Group deleted");

                                                }

                                                @Override
                                                public void onFailure(int i) {
                                                    Log.i(TAG, "Persistent Group deleted");
                                                }
                                            });
                                        }
                                    }

                                    return null;
                                }
                            });

            Method requestPersistentGroupMethod =
                    WifiP2pManager.class.getDeclaredMethod("requestPersistentGroupInfo", new Class[]{WifiP2pManager.Channel.class, persistentInterface});
            requestPersistentGroupMethod.invoke(wifiP2pManager, channel, persitentInterfaceObject);
        } catch (Exception ex) {
            ex.printStackTrace();
        }
    }


    private void startLocalService(String instance) {

        Map<String, String> record = new HashMap<String, String>();
        record.put("available", "visible");


        WifiP2pDnsSdServiceInfo service = WifiP2pDnsSdServiceInfo.newInstance(instance, SERVICE_TYPE, record);

        Log.i(TAG, "Add local service :" + instance);
        wifiP2pManager.addLocalService(channel, service, new WifiP2pManager.ActionListener() {
            public void onSuccess() {
                lastError = -1;
                Log.i(TAG, "Added local service");
            }

            public void onFailure(int reason) {
                lastError = reason;
                Log.i(TAG, "Adding local service failed, error code " + reason);
            }
        });
    }

    private void stopLocalServices() {

        mNetworkName = "";
        mPassphrase = "";

        wifiP2pManager.clearLocalServices(channel, new WifiP2pManager.ActionListener() {
            public void onSuccess() {
                lastError = -1;
                Log.i(TAG, "Cleared local services");
            }

            public void onFailure(int reason) {
                lastError = reason;
                Log.i(TAG, "Clearing local services failed, error code " + reason);
            }
        });
    }

    @Override
    public void GotConnection(InetAddress remote, InetAddress local) {
        final InetAddress remoteTmp = remote;
        final InetAddress localTmp = local;
        Log.i(TAG, "GotConnection remote" + remote);
        Log.i(TAG, "GotConnection local" + local);
        mHandler.post(new Runnable() {
            @Override
            public void run() {
                reStartHandShakeListening(0);
                that.callBack.Connected(remoteTmp, true);
            }
        });
    }

    @Override
    public void ListeningFailed(String reason, int triedTimes) {
        final int trialCountTmp = triedTimes;
        Log.i(TAG, "P2PAccessPoint listening failed: " + reason);
        mHandler.post(new Runnable() {
            @Override
            public void run() {

                if (trialCountTmp < 2) {
                    reStartHandShakeListening((trialCountTmp + 1));
                } else {
                    Log.i(TAG, "P2PAccessPoint listener failed 2 times, starting exit timer");
                    P2PSyncManager.getInstance(that.context).startShutDownTimer();
                }
            }
        });
    }


    private void reStartHandShakeListening(int trialCountTmp) {
        if (mHandShakeListenerThread != null) {
            mHandShakeListenerThread.cleanUp();
            mHandShakeListenerThread = null;
        }

        mHandShakeListenerThread = new HandShakeListenerThread(that, HandShakeportToUse, trialCountTmp);
        mHandShakeListenerThread.start();
    }

    private class P2PAccessPointReceiver extends BroadcastReceiver {
        @Override
        public void onReceive(Context context, Intent intent) {
            String action = intent.getAction();
            if (WifiP2pManager.WIFI_P2P_CONNECTION_CHANGED_ACTION.equals(action)) {
                NetworkInfo networkInfo = (NetworkInfo) intent.getParcelableExtra(WifiP2pManager.EXTRA_NETWORK_INFO);
                if (networkInfo.isConnected()) {
                    Log.i(TAG, "We are connected, will check info now");
                    wifiP2pManager.requestConnectionInfo(channel, that);
                } else {
                    Log.i(TAG, "We are DIS-connected");
                    if (mHandShakeListenerThread != null) {
                        mHandShakeListenerThread.cleanUp();
                    }
                }
            }
        }
    }

    public HandShakeListenerThread getmHandShakeListenerThread() {
        return mHandShakeListenerThread;
    }
}