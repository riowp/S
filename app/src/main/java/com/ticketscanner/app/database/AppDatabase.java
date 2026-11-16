package com.ticketscanner.app.database;

import android.content.Context;

import androidx.annotation.NonNull;
import androidx.room.Database;
import androidx.room.Room;
import androidx.room.RoomDatabase;
import androidx.room.migration.Migration;
import androidx.sqlite.db.SupportSQLiteDatabase;

import com.ticketscanner.app.models.KeluarScan;
import com.ticketscanner.app.models.TicketScan;

@Database(entities = {TicketScan.class, KeluarScan.class}, version = 10, exportSchema = false)
public abstract class AppDatabase extends RoomDatabase {

    private static volatile AppDatabase INSTANCE;

    public abstract TicketScanDao ticketScanDao();
    public abstract KeluarScanDao keluarScanDao();

    /**
     * v9 (APK v73) → v10 (APK v74).
     * Data lokal TIDAK dihapus: hanya tambah kolom lokasi + tabel keluar.
     */
    static final Migration MIGRATION_9_10 = new Migration(9, 10) {
        @Override
        public void migrate(@NonNull SupportSQLiteDatabase db) {
            db.execSQL("ALTER TABLE `ticket_scans` ADD COLUMN `lokasi_penerimaan` TEXT");
            db.execSQL("CREATE TABLE IF NOT EXISTS `keluar_scans` ("
                + "`id` INTEGER NOT NULL, "
                + "`ticketNumber` TEXT, "
                + "`tujuan` TEXT, "
                + "`scanTimestamp` INTEGER NOT NULL, "
                + "`shiftNumber` INTEGER NOT NULL, "
                + "`operationalDate` TEXT, "
                + "`operator` TEXT, "
                + "`synced` INTEGER NOT NULL, "
                + "PRIMARY KEY(`id`))");
        }
    };

    public static AppDatabase getInstance(Context context) {
        if (INSTANCE == null) {
            synchronized (AppDatabase.class) {
                if (INSTANCE == null) {
                    INSTANCE = Room.databaseBuilder(
                            context.getApplicationContext(),
                            AppDatabase.class,
                            "ticket_scanner.db"
                    )
                    .addMigrations(MIGRATION_9_10)
                    // Versi lebih lama dari v73 (DB < 9) → dibuat ulang
                    .fallbackToDestructiveMigration()
                    .build();
                }
            }
        }
        return INSTANCE;
    }
}
