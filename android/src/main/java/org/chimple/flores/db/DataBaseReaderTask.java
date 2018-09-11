package org.chimple.flores.db;

import android.content.Context;
import android.os.AsyncTask;

import java.lang.ref.WeakReference;

public class DataBaseReaderTask extends AsyncTask<String, Void, String> {
    public DataBaseReaderResponse delegate = null;
    private final WeakReference<Context> ctx;
    private final AppDatabase db;
    private P2PDBApi api;

    public DataBaseReaderTask(Context ctx) {
        this.ctx = new WeakReference<Context>(ctx);
        this.db = AppDatabase.getInstance(ctx);
        this.api = P2PDBApiImpl.getInstance(this.ctx.get());
    }

    @Override
    protected String doInBackground(String... params) {
        String command = params[0];
        String result = null;
        switch (command) {
            case "initialHandShakingMessage":
                result = this.api.serializeHandShakingMessage();
                break;

        }
        return result;

    }

    @Override
    protected void onPostExecute(String result) {
        delegate.processDBResponse(result);
    }
}
