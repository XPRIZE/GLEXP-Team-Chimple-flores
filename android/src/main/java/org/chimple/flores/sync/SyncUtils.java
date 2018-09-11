package org.chimple.flores.sync;

import android.content.Context;
import android.content.DialogInterface;
import android.net.wifi.WifiInfo;
import android.net.wifi.WifiManager;
import android.net.wifi.p2p.WifiP2pDevice;
import android.support.v7.app.AlertDialog;

import java.math.BigInteger;
import java.net.Inet4Address;
import java.net.Inet6Address;
import java.net.InetAddress;
import java.net.NetworkInterface;
import java.net.SocketException;
import java.net.UnknownHostException;
import java.nio.ByteOrder;
import java.util.Collections;
import java.util.List;

import org.chimple.flores.sync.Direct.P2PSyncService;
import org.chimple.flores.sync.NSD.NSDSyncService;

public class SyncUtils {

    public static final int HandShakeportToUse = 38777;
    public static final String SERVICE_TYPE = "_MAUI_p2p_four._tcp";

    public enum DiscoveryState {
        NONE,
        DiscoverPeer,
        DiscoverService,
        NSDServiceLost,
        NSDServiceFound,
        NSDServiceFoundNotServiceType,
        NSDServiceFoundSameMachine,
        NSDServiceFoundDifferentMachine,
        NSDDiscoveryServiceStopped,
        NSDServiceFailed,
        NSDDiscoveryFailed,
        NSDStartDiscoveryFailed,
        NSDStopDiscoveryFailed,
        NSDServiceUnRegistrationFailed,
        NSDServiceUnRegistrationSucceed,
        NSDServiceRegistrationFailed,
        NSDServiceRegistrationSucceed,
        NSDServiceResolved,
        NSDServiceResolvedSameIP,
        NSDServiceResolvedFailed
    }

    public enum SyncHandShakeState {
        NONE,
        Connecting,
        PreConnecting,
        Connected,
        ConnectingFailed,
        DisConnecting,
        Disconnected
    }


    public enum ReportingState {
        Idle,
        NotInitialized,
        WaitingStateChange,
        Listening,
        ConnectedAndListening
    }

    public enum ConnectionState {
        Idle,
        NotInitialized,
        WaitingStateChange,
        FindingPeers,
        FindingServices,
        Connecting,
        HandShaking,
        Connected,
        Disconnecting,
        Disconnected
    }


    static public byte[] ipIntToBytes(int ip) {
        byte[] b = new byte[4];
        b[0] = (byte) (ip & 0xFF);
        b[1] = (byte) ((ip >> 8) & 0xFF);
        b[2] = (byte) ((ip >> 16) & 0xFF);
        b[3] = (byte) ((ip >> 24) & 0xFF);
        return b;
    }

    static public String ipAddressToString(InetAddress ip) {
        return ip.getHostAddress().replaceFirst("%.*", "");
    }

    static public String deviceToString(WifiP2pDevice device) {
        return device.deviceName + " " + device.deviceAddress;
    }

    static public boolean isValidIp4Address(final String hostName) {
        try {
            return Inet4Address.getByName(hostName) != null;
        } catch (UnknownHostException ex) {
            return false;
        }
    }

    static public boolean isValidIp6Address(final String hostName) {
        try {
            return Inet6Address.getByName(hostName) != null;
        } catch (UnknownHostException ex) {
            return false;
        }
    }

    static public boolean isValidIpAddress(String ip) {
        boolean v4 = SyncUtils.isValidIp4Address(ip);
        boolean v6 = SyncUtils.isValidIp6Address(ip);

        if (!v4 && !v6) return false;
        try {
            InetAddress inet = InetAddress.getByName(ip);
            return inet.isLinkLocalAddress() || inet.isSiteLocalAddress();
        } catch (UnknownHostException e) {
            //Log.e(TAG, e.toString());
            return false;
        }
    }

    static public void printLocalIpAddresses(Context context) {
        List<NetworkInterface> ifaces;
        try {
            ifaces = Collections.list(NetworkInterface.getNetworkInterfaces());
        } catch (SocketException e) {
            showDialogBox("Got error: " + e.toString(), context);
            return;
        }
        String stuff = "Local IP addresses: \n";
        for (NetworkInterface iface : ifaces) {
            for (InetAddress addr : Collections.list(iface.getInetAddresses())) {
                String desc = SyncUtils.ipAddressToString(addr);
                if (addr.isLoopbackAddress()) desc += " (loopback)\n";
                if (addr.isLinkLocalAddress()) desc += " (link-local)\n";
                if (addr.isSiteLocalAddress()) desc += " (site-local)\n";
                if (addr.isMulticastAddress()) desc += " (multicast)\n";

                stuff += "\t" + iface.getName() + ": " + desc;
            }
        }

        showDialogBox(stuff, context);
    }
    // Dialog box

    static public void showDialogBox(String message, Context context) {
        AlertDialog.Builder alertDialogBuilder = new AlertDialog.Builder(context);
        alertDialogBuilder.setMessage(message);

        alertDialogBuilder.setNegativeButton("cancel",
                new DialogInterface.OnClickListener() {

                    @Override
                    public void onClick(DialogInterface arg0, int arg1) {

                    }
                });

        AlertDialog alertDialog = alertDialogBuilder.create();
        alertDialog.show();
    }


    public static boolean isWifiConnected(Context context) {
        WifiManager wifiMgr = (WifiManager) context.getSystemService(Context.WIFI_SERVICE);
        if (wifiMgr.isWifiEnabled()) { // WiFi adapter is ON
            WifiInfo wifiInfo = wifiMgr.getConnectionInfo();
            if (wifiInfo != null && wifiInfo.getNetworkId() == -1) {
                return false; // Not connected to an access-Point
            }
            return true;      // Connected to an Access Point
        } else {
            return false; // WiFi adapter is OFF
        }
    }


    public static P2PSyncService createP2PSyncService(String instance, String type, String deviceAddress, String deviceName) {
        return new P2PSyncService(instance, type, deviceAddress, deviceName);
    }

    public static NSDSyncService createNSDSyncService(String instance, String type, InetAddress deviceAddress, int port) {
        return new NSDSyncService(instance, type, deviceAddress, port);
    }


    public static String getWiFiIPAddress(Context context) {
        WifiManager wm = (WifiManager) context.getSystemService(Context.WIFI_SERVICE);
        String ip = getDottedDecimalIP(wm.getConnectionInfo().getIpAddress());
        return ip;
    }

    public static String getDottedDecimalIP(int ipAddr) {

        if (ByteOrder.nativeOrder().equals(ByteOrder.LITTLE_ENDIAN)) {
            ipAddr = Integer.reverseBytes(ipAddr);
        }

        byte[] ipByteArray = BigInteger.valueOf(ipAddr).toByteArray();

        //convert to dotted decimal notation:
        String ipAddrStr = getDottedDecimalIP(ipByteArray);
        return ipAddrStr;
    }

    public static String getDottedDecimalIP(byte[] ipAddr) {
        //convert to dotted decimal notation:
        String ipAddrStr = "";
        for (int i = 0; i < ipAddr.length; i++) {
            if (i > 0) {
                ipAddrStr += ".";
            }
            ipAddrStr += ipAddr[i] & 0xFF;
        }
        return ipAddrStr;
    }
}
