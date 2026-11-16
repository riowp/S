package com.ticketscanner.app.database;

import androidx.room.Dao;
import androidx.room.Delete;
import androidx.room.Insert;
import androidx.room.OnConflictStrategy;
import androidx.room.Query;
import androidx.room.Update;

import com.ticketscanner.app.models.KeluarScan;

import java.util.List;

@Dao
public interface KeluarScanDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    long insert(KeluarScan scan);

    @Update
    void update(KeluarScan scan);

    @Delete
    void delete(KeluarScan scan);

    @Query("SELECT * FROM keluar_scans WHERE operationalDate = :date ORDER BY scanTimestamp ASC")
    List<KeluarScan> getByDate(String date);

    @Query("SELECT * FROM keluar_scans WHERE operationalDate = :date AND shiftNumber = :shift ORDER BY scanTimestamp ASC")
    List<KeluarScan> getByDateAndShift(String date, int shift);

    @Query("SELECT * FROM keluar_scans WHERE synced = 0 ORDER BY scanTimestamp ASC")
    List<KeluarScan> getUnsynced();

    @Query("SELECT * FROM keluar_scans WHERE synced = 0 AND operationalDate = :date ORDER BY scanTimestamp ASC")
    List<KeluarScan> getUnsyncedByDate(String date);

    @Query("SELECT COUNT(*) FROM keluar_scans WHERE synced = 0")
    long getUnsyncedCount();

    /** Cek tiket sudah pernah tercatat keluar di HP ini */
    @Query("SELECT * FROM keluar_scans WHERE ticketNumber = :ticketNo LIMIT 1")
    KeluarScan getByTicket(String ticketNo);

    @Query("SELECT COUNT(*) FROM keluar_scans WHERE operationalDate = :date")
    int countByDate(String date);
}
