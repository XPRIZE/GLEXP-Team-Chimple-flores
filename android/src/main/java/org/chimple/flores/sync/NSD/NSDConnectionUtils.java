package org.chimple.flores.sync.NSD;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.Build;
import android.util.Log;

import java.io.IOException;
import java.net.ServerSocket;

public class NSDConnectionUtils {
    public static int getPort(Context context) {
        int localPort = getInt(context, "NSD_LOCAL_PORT");
        if (localPort < 0) {
            localPort = getNextFreePort();
            saveInt(context, "NSD_LOCAL_PORT", localPort);
        }
        return localPort;
    }

    public static int getCommunicationPort(Context context) {
        int localPort = getInt(context, "NSD_LOCAL_COM_PORT");
        if (localPort < 0) {
            localPort = getNextFreePort();
            saveInt(context, "NSD_LOCAL_COM_PORT", localPort);
        }
        return localPort;
    }


    public static int getNextFreePort() {
        int localPort = -1;
        try {
            ServerSocket s = new ServerSocket(0);
            localPort = s.getLocalPort();

            //closing the port
            if (s != null && !s.isClosed()) {
                s.close();
            }
        } catch (IOException e) {
            e.printStackTrace();
        }

        Log.v("DXDXD", Build.MANUFACTURER + ": free port requested: " + localPort);

        return localPort;
    }

    public static void clearPort(Context context) {
        clearKey(context, "NSD_LOCAL_PORT");
    }

    public static int getInt(Context cxt, String key) {
        SharedPreferences prefs = cxt.getSharedPreferences("NSD_PORT", Context.MODE_PRIVATE);
        int val = prefs.getInt(key, -1);
        return val;
    }


    public static void saveInt(Context cxt, String key, int value) {
        SharedPreferences.Editor prefsEditor = cxt.getSharedPreferences("NSD_PORT", Context.MODE_PRIVATE).edit();
        prefsEditor.putInt(key, value);
        prefsEditor.commit();
    }


    public static void clearKey(Context cxt, String key) {
        SharedPreferences.Editor prefsEditor = cxt.getSharedPreferences("NSD_PORT", Context.MODE_PRIVATE).edit();
        prefsEditor.remove(key);
        prefsEditor.commit();
    }


}
