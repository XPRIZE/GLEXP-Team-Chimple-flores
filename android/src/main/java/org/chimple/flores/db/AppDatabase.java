package org.chimple.flores.db;

import android.content.Context;
import android.arch.persistence.room.Database;
import android.arch.persistence.room.Room;
import android.arch.persistence.room.RoomDatabase;
import android.arch.persistence.room.TypeConverters;
import android.support.annotation.VisibleForTesting;


import org.chimple.flores.db.converter.DateConverter;
import org.chimple.flores.db.dao.P2PSyncDeviceStatusDao;
import org.chimple.flores.db.dao.P2PSyncInfoDao;
import org.chimple.flores.db.entity.P2PSyncDeviceStatus;
import org.chimple.flores.db.entity.P2PSyncInfo;

@Database(entities = {P2PSyncInfo.class, P2PSyncDeviceStatus.class},
        version = 1
)
@TypeConverters(
        DateConverter.class)
public abstract class AppDatabase extends RoomDatabase {
    public static final String DATABASE_NAME = "p2p_db";

    /**
     * The only instance
     */
    private static AppDatabase sInstance;

    public abstract P2PSyncInfoDao p2pSyncDao();

    public abstract P2PSyncDeviceStatusDao p2pSyncDeviceStatusDao();

    public static synchronized AppDatabase getInstance(Context context) {
        if (sInstance == null) {
            sInstance = Room
                    .databaseBuilder(context.getApplicationContext(), AppDatabase.class, DATABASE_NAME)
                    .allowMainThreadQueries()
                    .build();
            DatabaseInitializer.populateAsync(sInstance, context, P2PDBApiImpl.getInstance(context));
        }
        return sInstance;
    }

    /**
     * Switches the internal implementation with an empty in-memory database.
     *
     * @param context The context.
     */
    @VisibleForTesting
    public static void switchToInMemory(Context context) {
        sInstance = Room.inMemoryDatabaseBuilder(context.getApplicationContext(),
                AppDatabase.class).build();
    }

    public static void destroyInstance() {
        sInstance = null;
    }
}
