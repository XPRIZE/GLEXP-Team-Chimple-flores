package org.chimple.flores.multicast;

import android.content.Context;
import android.net.wifi.WifiInfo;
import android.net.wifi.WifiManager;
import android.os.Handler;

import java.io.IOException;
import java.net.BindException;
import java.net.InetAddress;
import java.net.MulticastSocket;
import java.net.NetworkInterface;
import java.util.concurrent.atomic.AtomicBoolean;

public class MulticastThread extends Thread {

    final AtomicBoolean running = new AtomicBoolean(true);
    final Context context;
    final String multicastIP;
    final int multicastPort;
    final Handler handler;

    MulticastSocket multicastSocket;
    private InetAddress inetAddress;

    MulticastThread(String threadName, Context context, String multicastIP, int multicastPort, Handler handler) {
        super(threadName);
        this.context = context;
        this.multicastIP = multicastIP;
        this.multicastPort = multicastPort;
        this.handler = handler;
    }


    public void run() {
        try {
            WifiManager wifiManager = (WifiManager) context.getSystemService(Context.WIFI_SERVICE);
            WifiInfo wifiInfo = wifiManager.getConnectionInfo();
            int wifiIPInt = wifiInfo.getIpAddress();
            byte[] wifiIPByte = new byte[]{
                    (byte) (wifiIPInt & 0xff),
                    (byte) (wifiIPInt >> 8 & 0xff),
                    (byte) (wifiIPInt >> 16 & 0xff),
                    (byte) (wifiIPInt >> 24 & 0xff)};
            this.inetAddress = InetAddress.getByAddress(wifiIPByte);
            NetworkInterface networkInterface = NetworkInterface.getByInetAddress(inetAddress);

            this.multicastSocket = new MulticastSocket(multicastPort);
            multicastSocket.setNetworkInterface(networkInterface);
            multicastSocket.joinGroup(InetAddress.getByName(multicastIP));
        } catch (BindException e) {
            e.printStackTrace();
            handler.post(new Runnable() {
                public void run() {
                    MulticastManager.getInstance(context).stopListening();
                }
            });
            String error = "Error: Cannot bind Address or Port.";
            if (multicastPort < 1024)
                error += "\nTry binding to a port larger than 1024.";
        } catch (IOException e) {
            e.printStackTrace();
            handler.post(new Runnable() {
                public void run() {
                    MulticastManager.getInstance(context).stopListening();
                }
            });
        } catch (Exception e) {
            e.printStackTrace();
            handler.post(new Runnable() {
                public void run() {
                    MulticastManager.getInstance(context).stopListening();
                }
            });
        }
    }

    public String getLocalIP() {
        return this.inetAddress.getHostAddress();
    }

    void stopRunning() {
        this.running.set(false);
    }
}

