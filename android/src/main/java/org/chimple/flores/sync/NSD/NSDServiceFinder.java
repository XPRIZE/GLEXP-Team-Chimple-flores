package org.chimple.flores.sync.NSD;

import android.content.Context;
import android.content.SharedPreferences;
import android.net.nsd.NsdManager;
import android.net.nsd.NsdServiceInfo;
import android.os.Build;
import android.os.CountDownTimer;
import android.os.Handler;
import android.util.Log;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import org.chimple.flores.sync.SyncUtils;

import static org.chimple.flores.sync.Direct.P2PSyncManager.P2P_SHARED_PREF;

public class NSDServiceFinder {

    public static final String TAG = NSDServiceFinder.class.getSimpleName();
    private NSDServiceFinder that;
    Context mContext;
    NsdManager mNsdManager;
    NsdManager.ResolveListener mResolveListener;
    NsdManager.DiscoveryListener mDiscoveryListener;
    NsdManager.RegistrationListener mRegistrationListener;

    private NsdServiceInfo mService;
    private SyncUtils.DiscoveryState discoveryState = SyncUtils.DiscoveryState.NONE;

    // callback
    private NSDWifiConnectionUpdateCallBack callBack;

    // Timers
    private CountDownTimer discoverServiceTimeOutTimer = null;
    private CountDownTimer discoverServiceRegistrationTimer = null;

    private String SERVICE_TYPE;
    private int servicePort = -1;

    // There is an additional dot at the end of service name most probably by os, this is to
    // rectify that problem
    private String SERVICE_TYPE_PLUS_DOT;

    public String mServiceName;

    private List<NSDSyncService> serviceList;

    public NSDServiceFinder(Context context, String serviceType, NSDWifiConnectionUpdateCallBack callBack) {
        this.that = this;
        this.callBack = callBack;
        this.mContext = context;
        this.SERVICE_TYPE = serviceType;
        servicePort = NSDConnectionUtils.getPort(this.mContext);
        SERVICE_TYPE_PLUS_DOT = SERVICE_TYPE + ".";
        this.mServiceName = this.buildServiceName();
        this.mNsdManager = (NsdManager) context.getSystemService(Context.NSD_SERVICE);
        serviceList = new ArrayList<NSDSyncService>();
        this.initialize();
    }

    private String buildServiceName() {
        SharedPreferences pref = this.mContext.getSharedPreferences(P2P_SHARED_PREF, 0);
        String userId = pref.getString("USER_ID", null); // getting String
        String deviceId = pref.getString("DEVICE_ID", null); // getting String
        return userId + ":" + deviceId;
    }

    public void initialize() {
        this.initTimers();
//        this.registerNSDServiceFinderReceiver();
        this.registerNSDService(this.servicePort);
        this.startServiceDiscovery();
        this.initializeResolveListener();
    }

    private void initTimers() {
        this.discoverServiceTimeOutTimer = new CountDownTimer(60000, 1000) {
            public void onTick(long millisUntilFinished) {
                // not using
            }

            public void onFinish() {
                stopDiscovery();
                startServiceDiscovery();
            }
        };

        this.discoverServiceRegistrationTimer = new CountDownTimer(60000, 1000) {
            public void onTick(long millisUntilFinished) {
                // not using
            }

            public void onFinish() {
                that.unregisterRegistrationListener();
                that.initializeRegistrationListener();
            }
        };
    }


    private void startServiceDiscovery() {
        initializeDiscoveryListener();
        discoveryState = SyncUtils.DiscoveryState.DiscoverService;
        final Handler handler = new Handler();
        handler.postDelayed(new Runnable() {
            //There are supposedly a possible race-condition bug with the service discovery
            // thus to avoid it, we are delaying the service discovery start here
            public void run() {
                if(mDiscoveryListener != null) {
                    serviceList.clear();
                    mNsdManager.discoverServices(
                            SERVICE_TYPE, NsdManager.PROTOCOL_DNS_SD, mDiscoveryListener);
                }
            }
        }, 1000);

        if (this.discoverServiceTimeOutTimer != null) {
            this.discoverServiceTimeOutTimer.cancel();
            this.discoverServiceTimeOutTimer.start();
        }
    }


    public void initializeDiscoveryListener() {
        try {
            mDiscoveryListener = new NsdManager.DiscoveryListener() {
                @Override
                public void onDiscoveryStarted(String regType) {
                    Log.d(TAG, "Service discovery started");
                }

                @Override
                public void onServiceFound(NsdServiceInfo service) {
                    Log.d(TAG, "Service discovery success" + service);
                    discoveryState = SyncUtils.DiscoveryState.NSDServiceFound;
                    that.callBack.serviceUpdateStatus(discoveryState);

                    String serviceType = service.getServiceType();
                    Log.d(TAG, "Service discovery success: " + service.getServiceName());

                    // For some reason the service type received has an extra dot with it, hence
                    // handling that case

                    boolean isOurService = serviceType.equals(SERVICE_TYPE) || serviceType.equals
                            (SERVICE_TYPE_PLUS_DOT);

                    if (!isOurService) {
                        Log.d(TAG, "Unknown Service Type: " + service.getServiceType());
                        discoveryState = SyncUtils.DiscoveryState.NSDServiceFoundNotServiceType;
                        that.callBack.serviceUpdateStatus(discoveryState);
                    } else if (service.getServiceName().equals(mServiceName)) {
                        Log.d(TAG, "Same machine: " + mServiceName);
                        discoveryState = SyncUtils.DiscoveryState.NSDServiceFoundSameMachine;
                        that.callBack.serviceUpdateStatus(discoveryState);
                    } else if (!service.getServiceName().equals(mServiceName)) {
                        Log.d(TAG, "different machines. (" + service.getServiceName() + "-" +
                                mServiceName + ")");
                        discoveryState = SyncUtils.DiscoveryState.NSDServiceFoundDifferentMachine;
                        that.callBack.serviceUpdateStatus(discoveryState);
                        if (mResolveListener != null) {
                            try {
                                mNsdManager.resolveService(service, mResolveListener);
                            } catch (Exception ex) {
                                ex.printStackTrace();
                                NSDSyncManager.getInstance(that.mContext).startConnectorsTimer();
                            }
                        }
                    }
                }

                @Override
                public void onServiceLost(NsdServiceInfo service) {
                    Log.e(TAG, "service lost" + service);
                    if (mService == service) {
                        mService = null;
                    }
                    discoveryState = SyncUtils.DiscoveryState.NSDServiceLost;
                    that.callBack.serviceUpdateStatus(discoveryState);
                    if(that.discoverServiceTimeOutTimer != null) {
                        that.discoverServiceTimeOutTimer.start();
                    }
                }

                @Override
                public void onDiscoveryStopped(String serviceType) {
                    Log.i(TAG, "Discovery stopped: " + serviceType);
                    discoveryState = SyncUtils.DiscoveryState.NSDDiscoveryServiceStopped;
                    that.callBack.serviceUpdateStatus(discoveryState);                    
                }

                @Override
                public void onStartDiscoveryFailed(String serviceType, int errorCode) {
                    Log.e(TAG, "Start Discovery failed: Error code:" + errorCode);
                    discoveryState = SyncUtils.DiscoveryState.NSDStartDiscoveryFailed;
                    that.callBack.serviceUpdateStatus(discoveryState);
                    if (that.discoverServiceTimeOutTimer != null) {
                        that.discoverServiceTimeOutTimer.start();
                    }
                }

                @Override
                public void onStopDiscoveryFailed(String serviceType, int errorCode) {
                    Log.e(TAG, "Stop Discovery failed: Error code:" + errorCode);
                    discoveryState = SyncUtils.DiscoveryState.NSDStopDiscoveryFailed;
                    that.callBack.serviceUpdateStatus(discoveryState);
                }
            };
        } catch (Exception e) {
            e.printStackTrace();
            Log.i(TAG, e.getLocalizedMessage());

        }

    }

    public void unInitializeResolveListener() {
        if (mResolveListener != null) {
            mResolveListener = null;
        }
    }

    public void initializeResolveListener() {
        this.unInitializeResolveListener();
        mResolveListener = new NsdManager.ResolveListener() {
            @Override
            public void onResolveFailed(NsdServiceInfo serviceInfo, int errorCode) {
                Log.e(TAG, "Resolve failed" + errorCode);
                discoveryState = SyncUtils.DiscoveryState.NSDServiceResolvedFailed;
                that.callBack.serviceUpdateStatus(discoveryState);
            }

            @Override
            public void onServiceResolved(NsdServiceInfo serviceInfo) {
                Log.v(TAG, "Resolve Succeeded. " + serviceInfo);
                if (serviceInfo.getServiceName().equals(mServiceName)) {
                    Log.i(TAG, "onServiceResolved Same IP.");
                    discoveryState = SyncUtils.DiscoveryState.NSDServiceResolvedSameIP;
                    that.callBack.serviceUpdateStatus(discoveryState);
                    return;
                }
                mService = serviceInfo;
                that.processResolvedServiceInfo(mService);
                Log.i(TAG, "onServiceResolved mService host:" + mService.getHost());
                Log.i(TAG, "onServiceResolved mService port: " + mService.getPort());
                discoveryState = SyncUtils.DiscoveryState.NSDServiceResolved;
                that.callBack.serviceUpdateStatus(discoveryState);
            }
        };
    }

    private void processResolvedServiceInfo(NsdServiceInfo serviceInfo) {
        synchronized (this) {
            Log.i(TAG, "processResolvedServiceInfo:" + serviceInfo.getHost().getHostAddress());
            NSDSyncService service = SyncUtils.createNSDSyncService(serviceInfo.getServiceName(), serviceInfo.getServiceType(), serviceInfo.getHost(), serviceInfo.getPort());
            serviceList.add(service);
            final List<NSDSyncService> unmodifiable = Collections.unmodifiableList(new ArrayList<NSDSyncService>(serviceList));
            this.callBack.processServiceList(unmodifiable);
        }
    }

    public void initializeRegistrationListener() {
        mRegistrationListener = new NsdManager.RegistrationListener() {
            @Override
            public void onServiceRegistered(NsdServiceInfo NsdServiceInfo) {
                mServiceName = NsdServiceInfo.getServiceName();
                Log.d(TAG, "Service registered: " + NsdServiceInfo);
                discoveryState = SyncUtils.DiscoveryState.NSDServiceRegistrationSucceed;
                that.callBack.serviceUpdateStatus(discoveryState);
                if (that.discoverServiceRegistrationTimer != null) {
                    that.discoverServiceRegistrationTimer.cancel();
                }
            }

            @Override
            public void onRegistrationFailed(NsdServiceInfo arg0, int arg1) {
                Log.d(TAG, "Service registration failed: " + arg1);
                discoveryState = SyncUtils.DiscoveryState.NSDServiceRegistrationFailed;
                that.callBack.serviceUpdateStatus(discoveryState);
                if (that.discoverServiceRegistrationTimer != null) {
                    that.discoverServiceRegistrationTimer.start();
                }
            }


            @Override
            public void onServiceUnregistered(NsdServiceInfo arg0) {
                Log.d(TAG, "Service unregistered: " + arg0.getServiceName());
                discoveryState = SyncUtils.DiscoveryState.NSDServiceUnRegistrationSucceed;
                that.callBack.serviceUpdateStatus(discoveryState);
            }

            @Override
            public void onUnregistrationFailed(NsdServiceInfo serviceInfo, int errorCode) {
                Log.d(TAG, "Service unregistration failed: " + errorCode);
                discoveryState = SyncUtils.DiscoveryState.NSDServiceUnRegistrationFailed;
                that.callBack.serviceUpdateStatus(discoveryState);
            }
        };
    }

    public void registerNSDService(int port) {
        initializeRegistrationListener();
        NsdServiceInfo serviceInfo = new NsdServiceInfo();
        serviceInfo.setPort(port);
        serviceInfo.setServiceName(mServiceName);
        serviceInfo.setServiceType(SERVICE_TYPE);
        Log.v(TAG, Build.MANUFACTURER + " registering service: " + port);
        if(mRegistrationListener != null) {
            mNsdManager.registerService(
                serviceInfo, NsdManager.PROTOCOL_DNS_SD, mRegistrationListener);
        }
    }

    public void stopDiscovery() {
        if (mDiscoveryListener != null) {
            try {
                mNsdManager.stopServiceDiscovery(mDiscoveryListener);
            } catch (Exception e) {
                Log.i(TAG, "stopDiscovery failed: " + e.getMessage());
            }
        }
    }

    public void unregisterRegistrationListener() {
        if (mRegistrationListener != null) {
            try {
                mNsdManager.unregisterService(mRegistrationListener);
            } catch (Exception e) {
                Log.i(TAG, "unregisterRegistrationListener failed: " + e.getMessage());
            } finally {
                mRegistrationListener = null;
            }
        }
    }

    public void cleanUp() {
        Log.i(TAG, "clean up....");
        this.stopDiscovery();
        this.unInitializeResolveListener();
        this.unregisterRegistrationListener();
        if (discoverServiceTimeOutTimer != null) {
            this.discoverServiceTimeOutTimer.cancel();
        }

        if (discoverServiceRegistrationTimer != null) {
            this.discoverServiceRegistrationTimer.cancel();
        }

        if (this.discoverServiceTimeOutTimer != null) {
            this.discoverServiceTimeOutTimer = null;
        }

        if (this.discoverServiceRegistrationTimer != null) {
            this.discoverServiceRegistrationTimer = null;
        }
    }
}