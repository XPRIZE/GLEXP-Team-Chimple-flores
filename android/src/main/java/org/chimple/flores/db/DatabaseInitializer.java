package org.chimple.flores.db;

import android.content.Context;
import android.content.res.AssetManager;
import android.os.AsyncTask;
import android.support.annotation.NonNull;
import android.util.Log;

import org.chimple.flores.application.P2PContext;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.Date;

public class DatabaseInitializer {

    private static final String TAG = DatabaseInitializer.class.getName();

    public static void populateAsync(@NonNull final AppDatabase db, @NonNull final Context context, @NonNull P2PDBApiImpl api) {
        PopulateDbAsync task = new PopulateDbAsync(db, context, api);
        task.execute();
    }

    public static void populateWithTestData(AppDatabase db, Context context) {
        AssetManager assetManager = context.getAssets();
        InputStream inputStream = null;
        try {
            inputStream = assetManager.open("database_2.csv");
        } catch (IOException e) {
            e.printStackTrace();
        }
        BufferedReader bufferedReader = new BufferedReader(new InputStreamReader(inputStream));

        // message contains userId, deviceId, recipientUserId, message, messageType

        String line = "";
        db.beginTransaction();
        try {
            while ((line = bufferedReader.readLine()) != null) {
                String[] columns = line.split(",");

                if (columns.length < 1) {
                    Log.d(TAG + "AppDatabase", "Skipping bad row");
                }

                String message = columns[0];
                String messageType = columns[1];

                P2PDBApiImpl.getInstance(context).persistMessage(P2PContext.getLoggedInUser(), P2PContext.getCurrentDevice(), "", message, messageType, new Date());
            }

            P2PDBApiImpl.getInstance(context).upsertProfile();
            db.setTransactionSuccessful();

        } catch (Exception e) {
            e.printStackTrace();
        } finally {
            db.endTransaction();
        }
    }

    private static class PopulateDbAsync extends AsyncTask<Void, Void, Void> {

        private final AppDatabase mDb;
        private Context context;
        private P2PDBApiImpl api;

        PopulateDbAsync(AppDatabase db, Context context, P2PDBApiImpl api) {
            mDb = db;
            this.context = context;
            this.api = api;
        }


        protected Void doInBackground(final Void... params) {
            populateWithTestData(mDb, this.context);
            return null;
        }

    }
}