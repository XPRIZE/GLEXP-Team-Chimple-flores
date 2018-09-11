package org.chimple.flores.application;

import android.content.Context;

public class P2PContext {
    private static P2PContext instance;
    private boolean initialized;

    public static P2PContext getInstance() {
        if (instance == null) {
            synchronized (P2PContext.class) {
                instance = new P2PContext();
            }
        }

        return instance;
    }

    private P2PContext() {
        // Singleton
    }

    public synchronized void initialize(final Context context) {
        if (initialized) {
            return;
        }

        initialized = true;
        Context applicationContext = context.getApplicationContext();
    }

}
