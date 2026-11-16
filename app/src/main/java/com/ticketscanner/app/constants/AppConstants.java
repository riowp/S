package com.ticketscanner.app.constants;

/**
 * Semua konstanta aplikasi di satu tempat.
 * Jika perlu ubah nilai (stockpile baru, warna, dsb) → cukup ubah di sini.
 */
public final class AppConstants {

    private AppConstants() {} // Tidak boleh di-instantiate

    // ── Shift ────────────────────────────────────────────────
    public static final int SHIFT_1 = 1;
    public static final int SHIFT_2 = 2;
    public static final int SHIFT_1_START_HOUR = 7;   // 07:00
    public static final int SHIFT_2_START_HOUR = 19;  // 19:00

    // ── Stockpile ────────────────────────────────────────────
    public static final String STOCKPILE_PLACEHOLDER = "-- Pilih Lokasi Stockpile --";
    public static final String[] STOCKPILE_OPTIONS = {
        STOCKPILE_PLACEHOLDER, "TC2", "1C", "1B", "Jetty 2", "Hopper 3.1", "Stockpile 3B"
    };
    public static final String[] STOCKPILE_EDIT_OPTIONS = {
        "TC2", "1C", "1B", "Jetty 2", "Hopper 3.1", "Stockpile 3B"
    };

    // ── Area dumping PT MAS (v78) ────────────────────────────
    public static final String[] STOCKPILE_PT_MAS = { "ROM", "Hopper" };

    /** Pilihan stockpile / area dumping per lokasi (tanpa placeholder).
     *  null = lokasi hanya 1 titik dumping (stockpile = nama lokasi). */
    public static String[] stockpileEditOptions(String lokasi) {
        String l = normLokasi(lokasi);
        if (LOKASI_SDJ.equals(l)) return STOCKPILE_EDIT_OPTIONS;
        if ("PT MAS".equals(l))   return STOCKPILE_PT_MAS;
        return null;
    }

    /** Sama seperti stockpileEditOptions, dengan placeholder di index 0 */
    public static String[] stockpileOptions(String lokasi) {
        String[] o = stockpileEditOptions(lokasi);
        if (o == null) return null;
        String[] r = new String[o.length + 1];
        r[0] = "-- Pilih " + stockpileLabel(lokasi) + " --";
        System.arraycopy(o, 0, r, 1, o.length);
        return r;
    }

    public static boolean hasStockpileChoice(String lokasi) {
        return stockpileEditOptions(lokasi) != null;
    }

    /** Label: SDJ = "Lokasi Stockpile", PT MAS = "Area Dumping" */
    public static String stockpileLabel(String lokasi) {
        return LOKASI_SDJ.equals(normLokasi(lokasi)) ? "Lokasi Stockpile" : "Area Dumping";
    }

    // ── Asal Batubara (Input Manual) ─────────────────────────
    public static final String ASAL_PLACEHOLDER   = "-- Pilih Asal Batubara --";
    public static final String ASAL_STOCKPILE_107 = "Stockpile 107";
    public static final String ASAL_STOCKPILE_36  = "Stockpile 36";
    public static final String[] ASAL_OPTIONS = {
        ASAL_PLACEHOLDER, ASAL_STOCKPILE_107, ASAL_STOCKPILE_36
    };

    // ── Lokasi Tugas (kolom F sheet Users) ───────────────────
    // Menentukan mode aplikasi & lokasi pencatatan untuk tiap user.
    public static final String LOKASI_SDJ     = "SDJ";
    public static final String LOKASI_KELUAR  = "KELUAR MIP";
    public static final String LOKASI_SEMUA   = "SEMUA";
    /** Lokasi penerimaan (juga daftar Tujuan untuk Keluar MIP) */
    public static final String[] LOKASI_PENERIMAAN = {
        "SDJ", "PT MAS", "KM107", "KM36", "MERAPI 2"
    };
    /** Pilihan Lokasi Tugas di Kelola User */
    public static final String[] LOKASI_TUGAS_OPTIONS = {
        "SDJ", "PT MAS", "KM107", "KM36", "MERAPI 2", "KELUAR MIP", "SEMUA"
    };

    /** Rapikan nilai lokasi: huruf besar, spasi tunggal, kosong → SDJ */
    public static String normLokasi(String val) {
        String v = val == null ? "" : val.trim().toUpperCase(java.util.Locale.ROOT)
                                          .replaceAll("\\s+", " ");
        return v.isEmpty() ? LOKASI_SDJ : v;
    }

    /** Label tampilan untuk lokasi aktif */
    public static String lokasiLabel(String lokasi) {
        String l = normLokasi(lokasi);
        return LOKASI_KELUAR.equals(l) ? "Keluar MIP" : "Penerimaan " + l;
    }

    /** Warna tema per lokasi — supaya operator langsung tahu sedang di area mana */
    public static int lokasiColor(String lokasi) {
        switch (normLokasi(lokasi)) {
            case "KELUAR MIP": return 0xFFBF360C; // oranye
            case "PT MAS":     return 0xFF00695C; // teal
            case "KM107":      return 0xFF4A148C; // ungu
            case "KM36":       return 0xFF5D4037; // cokelat
            case "MERAPI 2":   return 0xFF2E7D32; // hijau
            default:           return 0xFF1A237E; // SDJ — biru tua
        }
    }

    // ── Script Keluar MIP (spreadsheet Keluar_MIP) ───────────
    public static final String KELUAR_SCRIPT_URL =
        "https://script.google.com/macros/s/AKfycbxdwrBxoMS8Y5STifutP9NJR2TAGKEWRTRq3y005NuQGgS9To_kuafrkBa9RCD_Z-uH/exec";

    // ── Ticket Prefix ────────────────────────────────────────
    public static final String TICKET_SJB_PREFIX  = "SJB-";
    public static final String TICKET_107_PREFIX  = "MNL-";  // prefix auto-generated manual

    // ── Session ──────────────────────────────────────────────
    public static final long SESSION_DURATION_MS = 24 * 60 * 60 * 1000L; // 24 jam
    public static final long SESSION_WARNING_MS  = 30 * 60 * 1000L;      // 30 menit

    // ── Sync ─────────────────────────────────────────────────
    /** Menit sebelum pergantian shift untuk reminder sync */
    public static final int SYNC_REMINDER_MINUTES_BEFORE_SHIFT = 30;
    /** Maksimal data pending sebelum tampil warning */
    public static final int MAX_PENDING_WARNING = 5;

    // ── Roles ────────────────────────────────────────────────
    public static final String ROLE_ADMIN      = "admin";
    public static final String ROLE_SUPERVISOR = "supervisor";
    public static final String ROLE_OPERATOR   = "operator";
    public static final String ROLE_GUEST      = "guest";

    // ── Notification Channels ────────────────────────────────
    public static final String NOTIF_CHANNEL_SYNC    = "channel_sync";
    public static final String NOTIF_CHANNEL_SESSION = "channel_session";
    public static final String NOTIF_CHANNEL_DATA    = "channel_data";

    // ── Notification IDs ─────────────────────────────────────
    public static final int NOTIF_ID_SYNC_SUCCESS  = 1001;
    public static final int NOTIF_ID_SYNC_FAILED   = 1002;
    public static final int NOTIF_ID_SYNC_PENDING  = 1003;
    public static final int NOTIF_ID_DATA_NEW      = 1004;
    public static final int NOTIF_ID_SESSION_WARN  = 1005;
    public static final int NOTIF_ID_DUPLICATE     = 1006; // tiket duplikat di-rollback

    // ── Database ─────────────────────────────────────────────
    public static final String DB_NAME    = "penerimaan_sdj.db";
    public static final int    DB_VERSION = 10;

    // ── Barcode ──────────────────────────────────────────────
    public static final String BARCODE_SEPARATOR = "|";

    // ── UI ───────────────────────────────────────────────────
    public static final int SCAN_BACK_DELAY_MS = 800;  // delay kembali ke dashboard
}
