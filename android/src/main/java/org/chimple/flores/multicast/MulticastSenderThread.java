package org.chimple.flores.multicast;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;

import java.io.IOException;
import java.net.DatagramPacket;
import java.net.InetAddress;

public class MulticastSenderThread extends MulticastThread {

    private static final String TAG = MulticastSenderThread.class.getSimpleName();
    private String messageToSend;

    public MulticastSenderThread(Context context, String multicastIP, int multicastPort, String messageToSend) {
        super("MulticastSenderThread", context, multicastIP, multicastPort, new Handler(Looper.getMainLooper()));
        this.messageToSend = messageToSend;
    }


    public void run() {
        super.run();
        try {
            if(messageToSend != null) {
                byte[] bytesToSend = messageToSend.getBytes();
                multicastSocket.send(new DatagramPacket(bytesToSend, bytesToSend.length, InetAddress.getByName(multicastIP), multicastPort));
                multicastSocket.close();
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public void cleanUp() {
    }
}
