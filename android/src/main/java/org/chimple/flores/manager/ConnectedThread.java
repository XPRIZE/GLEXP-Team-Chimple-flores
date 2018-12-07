package org.chimple.flores.manager;

import android.bluetooth.BluetoothSocket;
import android.content.Context;
import android.content.Intent;
import android.support.v4.content.LocalBroadcastManager;
import android.util.Log;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

import static org.chimple.flores.application.P2PContext.CONSOLE_TYPE;
import static org.chimple.flores.application.P2PContext.LOG_TYPE;
import static org.chimple.flores.application.P2PContext.bluetoothMessageEvent;
import static org.chimple.flores.manager.BluetoothManager.MESSAGE_READ;
import static org.chimple.flores.manager.BluetoothManager.MESSAGE_WRITE;
import static org.chimple.flores.manager.BluetoothManager.STATE_CONNECTED;

public class ConnectedThread extends Thread {
    private static final String TAG = ConnectedThread.class.getSimpleName();
    private final BluetoothSocket mmSocket;
    private final BluetoothManager mManager;
    private final InputStream mmInStream;
    private final OutputStream mmOutStream;
    private final BtCallback mCallback;    

    public ConnectedThread(BluetoothSocket socket, String socketType, BtCallback callback, Context context) {
        Log.d(TAG, "create ConnectedThread: " + socketType);
        mmSocket = socket;
        mManager = BluetoothManager.getInstance(context);
        mManager.notifyUI("create ConnectedThread", " ------->", LOG_TYPE);
        mCallback = callback;
        InputStream tmpIn = null;
        OutputStream tmpOut = null;


        // Get the BluetoothSocket input and output streams
        try {            
            tmpIn = socket.getInputStream();
            tmpOut = socket.getOutputStream();            
        } catch (IOException e) {
            Log.e(TAG, "temp sockets not created", e);
            mManager.notifyUI("temp sockets not created" + e.toString(), " ------>", LOG_TYPE);
        } catch (Exception e) {
            Log.e(TAG, "temp sockets not created", e);
            mManager.notifyUI("temp sockets not created" + e.toString(), " ------>", LOG_TYPE);
            mCallback.PollSocketFailed(e.toString());
        }
        mmInStream = tmpIn;
        mmOutStream = tmpOut;
        mManager.setmState(STATE_CONNECTED);        
    }    

    public void run() {
        Log.i(TAG, "BEGIN mConnectedThread");
        if(mmSocket != null) {
            mManager.notifyUI("BEGIN mConnectedThread to remote" + mmSocket.getRemoteDevice().getAddress(), "------->", LOG_TYPE);    
        }
        
        byte[] buffer = new byte[64 * 1024];
        int bytes;
        StringBuffer sBuffer = null;

        // Keep listening to the InputStream while connected
        if (mManager.getmState() == STATE_CONNECTED) {
            mManager.stopHandShakeTimer();
            mManager.connectedToRemote();
        }
        while (mManager.getmState() == STATE_CONNECTED) {
            try {
                if (sBuffer == null) {
                    sBuffer = new StringBuffer();
                }
                // Read from the InputStream                
                bytes = mmInStream.read(buffer);    
                
                synchronized (ConnectedThread.class) {
                    this.broadcastIncomingMessage(sBuffer, new String(buffer, 0, bytes));
                }
            } catch (Exception e) {
                Log.e(TAG, "disconnected", e);
                mManager.notifyUI("disconnected: " + e.toString(), " ------>", LOG_TYPE);
                mCallback.ConnectionFailed(e.toString());
                break;
            }
        }
    }

    private void broadcastIncomingMessage(StringBuffer sBuffer, String message) {
        Log.d(TAG, "message got:" + message);
        boolean shouldProcess = false;
        if (message.startsWith("START") && message.endsWith("END")) {
            sBuffer.append(message);
            shouldProcess = true;
        } else if (message.startsWith("START") && !message.endsWith("END")) {
            sBuffer.append(message);
            shouldProcess = false;
        } else if (!message.startsWith("START") && message.endsWith("END")) {
            sBuffer.append(message);
            shouldProcess = true;
        } else {
            sBuffer.append(message);
            shouldProcess = false;
        }

        if (shouldProcess) {
            String finalMessage = sBuffer.toString();
            sBuffer.setLength(0);
            if (finalMessage != null) {
                finalMessage = finalMessage.replaceAll("^START", "");
                finalMessage = finalMessage.replaceAll("END$", "");
                Log.d(TAG, "received incoming message:" + finalMessage);

                String[] subMessages = finalMessage.split("ENDSTART");
                if(subMessages != null && subMessages.length > 0) {
                    for (int i = 0; i < subMessages.length; i++) {
                        Intent intent = new Intent(bluetoothMessageEvent);
                        // You can also include some extra data.
                        intent.putExtra("message", subMessages[i]);
                        LocalBroadcastManager.getInstance(mManager.getContext()).sendBroadcast(intent);
                    }
                } else {
                    Intent intent = new Intent(bluetoothMessageEvent);
                    // You can also include some extra data.
                    intent.putExtra("message", finalMessage);
                    LocalBroadcastManager.getInstance(mManager.getContext()).sendBroadcast(intent);
                }
                finalMessage = null;

            }
        }
    }

    /**
     * Write to the connected OutStream.
     *
     * @param buffer The bytes to write
     */
    public void write(byte[] buffer) {
        try {
            mmOutStream.write(buffer);
        } catch (IOException e) {
            Log.e(TAG, "Exception during write", e);
            mManager.HandShakeFailed("Connected Thread --> SOCKET_DISCONNEDTED", false);
        }
    }

    public void Stop() {
        try {
            if (mmInStream != null) {
                mmInStream.close();
            }
        } catch (Exception ex) {
            Log.e(TAG, "close() of connect mmInStream failed", ex);
        }

        try {
            if (mmOutStream != null) {
                mmOutStream.close();
            }
        } catch (Exception ex) {
            Log.e(TAG, "close() of connect mmOutStream failed", ex);
        }
        try {            
            mmSocket.close();
        } catch (Exception e) {
            Log.e(TAG, "close() of connect socket failed", e);
        }
    }


}