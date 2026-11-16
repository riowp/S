package com.ticketscanner.app.utils;

import android.app.Activity;

import androidx.appcompat.app.AlertDialog;

import com.ticketscanner.app.constants.AppConstants;

/**
 * Dialog pilih lokasi kerja — hanya untuk user dengan Lokasi Tugas = SEMUA (admin).
 * User lain lokasinya dikunci dari sheet Users dan tidak melihat dialog ini.
 */
public final class LokasiChooser {

    private LokasiChooser() {}

    public interface OnChosen { void onChosen(String lokasi); }

    /** Urutan pilihan: Keluar MIP lalu 5 lokasi penerimaan */
    public static String[] options() {
        String[] opts = new String[AppConstants.LOKASI_PENERIMAAN.length + 1];
        opts[0] = AppConstants.LOKASI_KELUAR;
        System.arraycopy(AppConstants.LOKASI_PENERIMAAN, 0, opts, 1,
                         AppConstants.LOKASI_PENERIMAAN.length);
        return opts;
    }

    public static void show(Activity activity, SessionManager session,
                            boolean cancelable, OnChosen callback) {
        final String[] opts = options();
        String[] labels = new String[opts.length];
        int checked = -1;
        String current = session.getActiveLokasi();
        for (int i = 0; i < opts.length; i++) {
            labels[i] = AppConstants.lokasiLabel(opts[i]);
            if (opts[i].equals(current)) checked = i;
        }

        AlertDialog.Builder b = new AlertDialog.Builder(activity)
            .setTitle("Pilih Lokasi Kerja")
            .setSingleChoiceItems(labels, checked, (d, which) -> {
                session.setActiveLokasi(opts[which]);
                d.dismiss();
                if (callback != null) callback.onChosen(opts[which]);
            })
            .setCancelable(cancelable);
        if (cancelable) b.setNegativeButton("Batal", null);
        b.show();
    }
}
