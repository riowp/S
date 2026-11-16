package com.ticketscanner.app.utils;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import com.ticketscanner.app.constants.AppConstants;
import com.ticketscanner.app.database.AppDatabase;
import com.ticketscanner.app.database.KeluarScanDao;
import com.ticketscanner.app.models.KeluarScan;
import com.ticketscanner.app.notifications.NotificationHelper;

import org.json.JSONArray;
import org.json.JSONObject;

import java.text.SimpleDateFormat;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Sinkronisasi data Keluar MIP ↔ script Keluar MIP (spreadsheet Keluar_MIP).
 * Pola sama dengan SyncManager penerimaan: PUSH pending → PULL per tanggal → REWRITE.
 */
public class KeluarSyncManager {

    private static final String TAG = "KeluarSyncManager";
    private static final String URL = AppConstants.KELUAR_SCRIPT_URL;

    private static final ExecutorService executor = Executors.newSingleThreadExecutor();
    private static final Handler mainHandler      = new Handler(Looper.getMainLooper());
    private static volatile boolean isSyncing     = false;

    // ── PUSH ONLY — dipanggil setelah scan keluar ─────────────────
    public static void syncPending(Context context, SyncManager.SyncCallback callback) {
        if (!SyncManager.isOnline(context)) { post(callback, false, 0, "Tidak ada koneksi"); return; }
        if (isSyncing) { post(callback, true, 0, null); return; }
        final Context app = context.getApplicationContext();
        executor.execute(() -> {
            isSyncing = true;
            try {
                KeluarScanDao dao = AppDatabase.getInstance(app).keluarScanDao();
                int pushed = pushList(app, dao, dao.getUnsynced());
                post(callback, true, pushed, null);
            } catch (Exception e) {
                post(callback, false, 0, e.getMessage());
            } finally {
                isSyncing = false;
            }
        });
    }

    // ── FULL SYNC per tanggal — dashboard & riwayat ───────────────
    public static void sync(Context context, String date, SyncManager.SyncCallback callback) {
        if (!SyncManager.isOnline(context)) { post(callback, false, 0, "Tidak ada koneksi internet"); return; }
        if (isSyncing) { post(callback, true, 0, null); return; }
        final Context app = context.getApplicationContext();
        executor.execute(() -> {
            isSyncing = true;
            try {
                KeluarScanDao dao = AppDatabase.getInstance(app).keluarScanDao();

                // 1. PUSH semua pending (semua tanggal)
                int pushed = pushList(app, dao, dao.getUnsynced());

                // 2. PULL data tanggal ini dari Sheets
                String resp = SyncManager.request(URL + "?action=getDataByDate&date=" + SyncManager.enc(date));
                if (resp == null) { post(callback, true, pushed, null); return; }
                JSONObject json = new JSONObject(resp);
                if (!"ok".equals(json.optString("status"))) { post(callback, true, pushed, null); return; }
                JSONArray records = json.getJSONArray("records");

                // 3. WIPE selektif — hanya yang sudah synced
                for (KeluarScan local : dao.getByDate(date)) {
                    if (local.isSynced()) dao.delete(local);
                }

                // 4. REWRITE dari Sheets
                SimpleDateFormat sdf = new SimpleDateFormat("dd/MM/yyyy HH:mm:ss", Locale.getDefault());
                for (int i = 0; i < records.length(); i++) {
                    JSONObject r = records.getJSONObject(i);
                    String ticket = r.optString("ticketNumber", "").trim();
                    if (ticket.isEmpty()) continue;
                    KeluarScan k = new KeluarScan();
                    long sid = parseLong(r.optString("id", "0"));
                    k.setId(sid > 0 ? sid : (System.currentTimeMillis() / 1000 + i));
                    k.setTicketNumber(ticket);
                    k.setTujuan(r.optString("tujuan", ""));
                    k.setShiftNumber(parseInt(r.optString("shiftNumber", "1"), 1));
                    k.setOperationalDate(r.optString("operationalDate", date));
                    k.setOperator(r.optString("operator", ""));
                    long ts;
                    try { ts = sdf.parse(r.optString("scanTime", "")).getTime(); }
                    catch (Exception e) { ts = System.currentTimeMillis(); }
                    k.setScanTimestamp(ts);
                    k.setSynced(true);
                    dao.insert(k);
                }
                post(callback, true, pushed, null);
            } catch (Exception e) {
                Log.e(TAG, "sync error: " + e.getMessage(), e);
                post(callback, false, 0, e.getMessage());
            } finally {
                isSyncing = false;
            }
        });
    }

    /** Kirim daftar pending. Dijalankan di thread executor. */
    private static int pushList(Context app, KeluarScanDao dao, List<KeluarScan> pending) {
        if (pending == null || pending.isEmpty()) return 0;
        String deviceId = android.provider.Settings.Secure.getString(
            app.getContentResolver(), android.provider.Settings.Secure.ANDROID_ID);
        int pushed = 0;
        for (KeluarScan k : pending) {
            String params = "action=add"
                + "&id="              + k.getId()
                + "&ticketNumber="    + SyncManager.enc(k.getTicketNumber())
                + "&tujuan="          + SyncManager.enc(k.getTujuan())
                + "&shiftNumber="     + k.getShiftNumber()
                + "&scanTime="        + SyncManager.enc(ShiftUtils.formatDateTime(k.getScanTimestamp()))
                + "&operationalDate=" + SyncManager.enc(k.getOperationalDate())
                + "&operator="        + SyncManager.enc(k.getOperator())
                + "&deviceId="        + SyncManager.enc(deviceId);
            String resp = SyncManager.request(URL + "?" + params);
            if (resp == null) continue; // gagal jaringan → coba lagi nanti
            try {
                JSONObject json = new JSONObject(resp);
                String status = json.optString("status");
                String msg    = json.optString("message", "");
                if ("ok".equals(status)) {
                    k.setSynced(true);
                    dao.update(k);
                    pushed++;
                } else if ("duplicate".equals(status)) {
                    if (msg.startsWith("ID ")) {
                        // Data ini sudah masuk sebelumnya (retry) → tandai tersync
                        k.setSynced(true);
                        dao.update(k);
                    } else {
                        // Tiket sudah tercatat keluar dari HP lain → batalkan di HP ini
                        Log.w(TAG, "Duplikat dari server: " + msg);
                        dao.delete(k);
                        final String tn = k.getTicketNumber();
                        mainHandler.post(() ->
                            NotificationHelper.notifyDuplicateTicketRolledBack(app, tn));
                    }
                } else {
                    Log.w(TAG, "Ditolak server (" + k.getTicketNumber() + "): " + msg);
                }
            } catch (Exception e) {
                Log.e(TAG, "parse error: " + e.getMessage());
            }
        }
        return pushed;
    }

    // ── EDIT TUJUAN ───────────────────────────────────────────────
    public static void syncUpdate(Context context, long id, String tujuan, String opDate,
                                  SyncManager.SyncCallback callback) {
        executor.execute(() -> {
            String resp = SyncManager.request(URL + "?action=update&id=" + id
                + "&tujuan=" + SyncManager.enc(tujuan)
                + "&operationalDate=" + SyncManager.enc(opDate));
            boolean ok = resp != null && resp.contains("\"ok\"");
            post(callback, ok, ok ? 1 : 0, ok ? null : "Gagal update di Sheets");
        });
    }

    // ── HAPUS ─────────────────────────────────────────────────────
    public static void syncDelete(Context context, long id, String opDate,
                                  SyncManager.SyncCallback callback) {
        executor.execute(() -> {
            String resp = SyncManager.request(URL + "?action=delete&id=" + id
                + "&operationalDate=" + SyncManager.enc(opDate));
            boolean ok = resp != null && resp.contains("\"ok\"");
            post(callback, ok, ok ? 1 : 0, ok ? null : "Gagal hapus di Sheets");
        });
    }

    // ── Helper ────────────────────────────────────────────────────
    private static long parseLong(String s) {
        try { return Long.parseLong(s.trim()); } catch (Exception e) { return 0; }
    }
    private static int parseInt(String s, int fb) {
        try { return Integer.parseInt(s.trim()); } catch (Exception e) { return fb; }
    }
    private static void post(SyncManager.SyncCallback cb, boolean ok, int count, String err) {
        if (cb == null) return;
        if (ok) mainHandler.post(() -> cb.onSuccess(count));
        else    mainHandler.post(() -> cb.onError(err != null ? err : "Error"));
    }
}
