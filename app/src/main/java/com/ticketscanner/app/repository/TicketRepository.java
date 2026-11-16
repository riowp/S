package com.ticketscanner.app.repository;

import android.content.Context;
import android.util.Log;

import com.ticketscanner.app.constants.AppExecutors;
import com.ticketscanner.app.database.AppDatabase;
import com.ticketscanner.app.database.TicketScanDao;
import com.ticketscanner.app.models.TicketScan;
import com.ticketscanner.app.utils.ShiftUtils;
import com.ticketscanner.app.utils.SyncManager;

import java.util.List;

/**
 * Satu-satunya pintu masuk untuk semua operasi data tiket.
 *
 * Sebelumnya setiap Activity query database sendiri-sendiri.
 * Sekarang semua logika data ada di sini — Activity cukup panggil method.
 *
 * Keuntungan:
 * - Logika bisnis berubah? Ubah di sini saja, tidak perlu cari di 5 Activity
 * - Mudah dites secara terpisah
 * - Tidak ada duplikasi query
 */
public class TicketRepository {

    private static final String TAG = "TicketRepository";
    private static TicketRepository instance;

    private final TicketScanDao dao;
    private final Context appContext;

    private TicketRepository(Context context) {
        this.appContext = context.getApplicationContext();
        this.dao = AppDatabase.getInstance(appContext).ticketScanDao();
    }

    public static synchronized TicketRepository getInstance(Context context) {
        if (instance == null) instance = new TicketRepository(context);
        return instance;
    }

    // ── READ ─────────────────────────────────────────────────

    public interface DataCallback<T> {
        void onResult(T data);
    }

    /** Lokasi penerimaan yang sedang aktif di HP ini */
    private String activeLokasi() {
        return new com.ticketscanner.app.utils.SessionManager(appContext).getActiveLokasi();
    }

    /**
     * Statistik dashboard untuk satu tanggal — HANYA lokasi penerimaan aktif.
     * v75: dihitung dari daftar tiket yang sudah disaring lokasi, sehingga data
     * lokasi lain yang masih tersimpan di HP tidak ikut terhitung.
     */
    public void getTodayStats(String opDate, DataCallback<TodayStats> callback) {
        AppExecutors.runIO(() -> {
            try {
                String lokasi = activeLokasi();
                String[] spByLokasi = com.ticketscanner.app.constants.AppConstants.stockpileEditOptions(lokasi);
                String[] stockpiles = spByLokasi != null ? spByLokasi
                    : com.ticketscanner.app.constants.AppConstants.STOCKPILE_EDIT_OPTIONS;
                TodayStats stats = new TodayStats();
                stats.shift1List    = dao.getScansByDateShiftLokasi(opDate, 1, lokasi);
                stats.shift2List    = dao.getScansByDateShiftLokasi(opDate, 2, lokasi);
                stats.unsyncedCount = dao.getUnsyncedCount();

                stats.shift1StockpileTon   = new double[stockpiles.length];
                stats.shift2StockpileTon   = new double[stockpiles.length];
                stats.totalStockpileTon    = new double[stockpiles.length];
                stats.shift1StockpileCount = new int[stockpiles.length];
                stats.shift2StockpileCount = new int[stockpiles.length];
                stats.totalStockpileCount  = new int[stockpiles.length];

                for (int sh = 1; sh <= 2; sh++) {
                    List<TicketScan> list = sh == 1 ? stats.shift1List : stats.shift2List;
                    for (TicketScan t : list) {
                        double ton = t.getEffectiveTonnage();
                        if (sh == 1) stats.shift1Ton += ton; else stats.shift2Ton += ton;
                        for (int i = 0; i < stockpiles.length; i++) {
                            if (!stockpiles[i].equals(t.getStockpile())) continue;
                            if (sh == 1) { stats.shift1StockpileTon[i] += ton; stats.shift1StockpileCount[i]++; }
                            else         { stats.shift2StockpileTon[i] += ton; stats.shift2StockpileCount[i]++; }
                            stats.totalStockpileTon[i] += ton;
                            stats.totalStockpileCount[i]++;
                        }
                        String asal = t.getAsalBatubara();
                        if (com.ticketscanner.app.constants.AppConstants.ASAL_STOCKPILE_107.equals(asal)) {
                            stats.asal107Count++; stats.asal107Ton += ton;
                        } else if (com.ticketscanner.app.constants.AppConstants.ASAL_STOCKPILE_36.equals(asal)) {
                            stats.asal36Count++; stats.asal36Ton += ton;
                        }
                    }
                }
                stats.totalTon     = stats.shift1Ton + stats.shift2Ton;
                stats.totalTickets = stats.shift1List.size() + stats.shift2List.size();

                AppExecutors.runMain(() -> callback.onResult(stats));
            } catch (Exception e) {
                Log.e(TAG, "getTodayStats error: " + e.getMessage(), e);
                AppExecutors.runMain(() -> callback.onResult(new TodayStats()));
            }
        });
    }

    /** Ambil scan berdasarkan tanggal dan shift */
    public void getByDateAndShift(String date, int shift, DataCallback<List<TicketScan>> callback) {
        AppExecutors.runIO(() -> {
            try {
                String lokasi = activeLokasi();
                List<TicketScan> result = shift == 0
                    ? dao.getScansByDateLokasi(date, lokasi)
                    : dao.getScansByDateShiftLokasi(date, shift, lokasi);
                AppExecutors.runMain(() -> callback.onResult(result));
            } catch (Exception e) {
                Log.e(TAG, "getByDateAndShift error: " + e.getMessage(), e);
            }
        });
    }

    /** Ambil semua scan hari ini untuk export */
    public void getForExport(String date, DataCallback<ExportData> callback) {
        AppExecutors.runIO(() -> {
            try {
                String lokasi = activeLokasi();
                ExportData data = new ExportData();
                data.shift1 = dao.getScansByDateShiftLokasi(date, 1, lokasi);
                data.shift2 = dao.getScansByDateShiftLokasi(date, 2, lokasi);
                data.totalTickets = data.shift1.size() + data.shift2.size();
                for (TicketScan t : data.shift1) data.totalTonnage += t.getEffectiveTonnage();
                for (TicketScan t : data.shift2) data.totalTonnage += t.getEffectiveTonnage();
                AppExecutors.runMain(() -> callback.onResult(data));
            } catch (Exception e) {
                Log.e(TAG, "getForExport error: " + e.getMessage(), e);
            }
        });
    }

    /** Cek apakah tiket sudah pernah ada (lokal) */
    public void checkDuplicateLocal(String ticketNo, DataCallback<Boolean> callback) {
        AppExecutors.runIO(() -> {
            try {
                boolean exists = dao.countTicketEver(ticketNo) > 0;
                AppExecutors.runMain(() -> callback.onResult(exists));
            } catch (Exception e) {
                Log.e(TAG, "checkDuplicate error: " + e.getMessage(), e);
                AppExecutors.runMain(() -> callback.onResult(false));
            }
        });
    }

    /** Jumlah data yang belum tersync */
    public void getUnsyncedCount(DataCallback<Long> callback) {
        AppExecutors.runIO(() -> {
            try {
                long count = dao.getUnsyncedCount();
                AppExecutors.runMain(() -> callback.onResult(count));
            } catch (Exception e) {
                AppExecutors.runMain(() -> callback.onResult(0L));
            }
        });
    }

    // ── WRITE ────────────────────────────────────────────────

    public interface SaveCallback {
        void onSuccess();
        void onError(String message);
    }

    /** Simpan tiket baru ke DB lokal */
    public void saveTicket(TicketScan scan, SaveCallback callback) {
        AppExecutors.runIO(() -> {
            try {
                dao.insert(scan);
                Log.d(TAG, "Tiket tersimpan: " + scan.getTicketNumber());
                AppExecutors.runMain(() -> callback.onSuccess());
            } catch (Exception e) {
                Log.e(TAG, "saveTicket error: " + e.getMessage(), e);
                AppExecutors.runMain(() -> callback.onError("Gagal simpan: " + e.getMessage()));
            }
        });
    }

    /** Update tiket yang sudah ada */
    public void updateTicket(TicketScan scan, SaveCallback callback) {
        AppExecutors.runIO(() -> {
            try {
                dao.update(scan);
                AppExecutors.runMain(() -> callback.onSuccess());
            } catch (Exception e) {
                Log.e(TAG, "updateTicket error: " + e.getMessage(), e);
                AppExecutors.runMain(() -> callback.onError("Gagal update: " + e.getMessage()));
            }
        });
    }

    /** Hapus tiket */
    public void deleteTicket(TicketScan scan, SaveCallback callback) {
        AppExecutors.runIO(() -> {
            try {
                dao.delete(scan);
                AppExecutors.runMain(() -> callback.onSuccess());
            } catch (Exception e) {
                Log.e(TAG, "deleteTicket error: " + e.getMessage(), e);
                AppExecutors.runMain(() -> callback.onError("Gagal hapus: " + e.getMessage()));
            }
        });
    }

    // ── DATA CLASSES ─────────────────────────────────────────

    public static class TodayStats {
        public List<TicketScan> shift1List;
        public List<TicketScan> shift2List;
        public double shift1Ton     = 0;
        public double shift2Ton     = 0;
        public double totalTon      = 0;
        public int    totalTickets  = 0;
        public long   unsyncedCount = 0;

        // Tonnage per stockpile: index sesuai AppConstants.stockpileEditOptions(lokasi)
        public double[] shift1StockpileTon; // per stockpile shift 1
        public double[] shift2StockpileTon; // per stockpile shift 2
        public double[] totalStockpileTon;  // per stockpile total

        // Jumlah unit DT per stockpile
        public int[] shift1StockpileCount; // per stockpile shift 1
        public int[] shift2StockpileCount; // per stockpile shift 2
        public int[] totalStockpileCount;  // per stockpile total

        // Ritase & tonnase dari Stockpile 107 dan 36
        public int    asal107Count = 0;
        public double asal107Ton   = 0;
        public int    asal36Count  = 0;
        public double asal36Ton    = 0;

        public int shift1Count() { return shift1List != null ? shift1List.size() : 0; }
        public int shift2Count() { return shift2List != null ? shift2List.size() : 0; }
    }

    public static class ExportData {
        public List<TicketScan> shift1;
        public List<TicketScan> shift2;
        public int    totalTickets = 0;
        public double totalTonnage = 0;
    }
}
