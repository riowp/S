package com.ticketscanner.app.models;

import androidx.room.Entity;
import androidx.room.PrimaryKey;

/**
 * Satu tiket DT yang keluar dari site MIP (tanpa tonnase).
 * Disinkronkan ke script Keluar MIP (spreadsheet Keluar_MIP).
 */
@Entity(tableName = "keluar_scans")
public class KeluarScan {

    @PrimaryKey(autoGenerate = false)
    private long id;
    private String ticketNumber;
    private String tujuan;           // SDJ / PT MAS / KM107 / KM36 / MERAPI 2
    private long scanTimestamp;
    private int shiftNumber;
    private String operationalDate;  // yyyy-MM-dd
    private String operator;
    private boolean synced = false;

    public KeluarScan() {}

    public long getId()                       { return id; }
    public void setId(long v)                 { this.id = v; }
    public String getTicketNumber()           { return ticketNumber != null ? ticketNumber : ""; }
    public void setTicketNumber(String v)     { this.ticketNumber = v; }
    public String getTujuan()                 { return tujuan != null ? tujuan : ""; }
    public void setTujuan(String v)           { this.tujuan = v; }
    public long getScanTimestamp()            { return scanTimestamp; }
    public void setScanTimestamp(long v)      { this.scanTimestamp = v; }
    public int getShiftNumber()               { return shiftNumber; }
    public void setShiftNumber(int v)         { this.shiftNumber = v; }
    public String getOperationalDate()        { return operationalDate != null ? operationalDate : ""; }
    public void setOperationalDate(String v)  { this.operationalDate = v; }
    public String getOperator()               { return operator != null ? operator : ""; }
    public void setOperator(String v)         { this.operator = v; }
    public boolean isSynced()                 { return synced; }
    public void setSynced(boolean v)          { this.synced = v; }
}
