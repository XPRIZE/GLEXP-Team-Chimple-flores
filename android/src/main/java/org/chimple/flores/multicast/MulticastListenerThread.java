package org.chimple.flores.multicast;

import android.content.Context;
import android.content.Intent;
import android.os.Handler;
import android.os.Looper;
import android.support.v4.content.LocalBroadcastManager;
import android.util.Log;

import java.io.IOException;
import java.net.DatagramPacket;

import static org.chimple.flores.application.P2PContext.messageEvent;

public class MulticastListenerThread extends MulticastThread {
    private static final String TAG = MulticastListenerThread.class.getSimpleName();

    MulticastListenerThread(Context context, String multicastIP, int multicastPort) {
        super(TAG, context, multicastIP, multicastPort, new Handler(Looper.getMainLooper()));
    }


    public void run() {
        super.run();

        DatagramPacket packet = new DatagramPacket(new byte[512], 512);

        while (running.get()) {
            packet.setData(new byte[61440]);
            Log.d(TAG, "MulticastListenerThread run loop " + (multicastSocket != null));
            try {
                if (multicastSocket != null && !multicastSocket.isClosed()) {
                    Log.d(TAG, "MulticastListenerThread running ->" + running.get());
                    multicastSocket.receive(packet);
                } else {
                    break;
                }
            } catch (IOException ignored) {
                ignored.printStackTrace();
                continue;
            }
            String data = new String(packet.getData()).trim();
            boolean isLoopBackMessage = getLocalIP().equals(packet.getAddress().getHostAddress()) ? true : false;
            this.broadcastIncomingMessage(data, packet.getAddress().getHostAddress(), isLoopBackMessage);
            data = null;
        }
    }

    private void broadcastIncomingMessage(String message, String fromIP, boolean isLoopback) {
        if (!isLoopback) {
            Log.d(TAG, "received incoming message:" + message + " from IP:" + fromIP);
            Intent intent = new Intent(messageEvent);
            // You can also include some extra data.
            intent.putExtra("message", message);
            intent.putExtra("fromIP", fromIP);
            LocalBroadcastManager.getInstance(this.context).sendBroadcast(intent);
        }
    }

    public void cleanUp() {
        if (multicastSocket != null && !running.get() && !multicastSocket.isClosed()) {
            try {
                Log.d(TAG, "MulticastListenerThread -> multicastSocket -> closed");
                this.multicastSocket.close();
            } catch (Exception e) {
                e.printStackTrace();
            }
        }

    }
}

