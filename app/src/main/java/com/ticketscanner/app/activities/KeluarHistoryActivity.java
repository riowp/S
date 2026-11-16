package com.ticketscanner.app.activities;

import android.app.DatePickerDialog;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.MenuItem;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.ProgressBar;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.button.MaterialButton;
import com.google.android.material.textfield.TextInputEditText;
import com.ticketscanner.app.R;
import com.ticketscanner.app.adapters.KeluarAdapter;
import com.ticketscanner.app.constants.AppConstants;
import com.ticketscanner.app.database.AppDatabase;
import com.ticketscanner.app.models.KeluarScan;
import com.ticketscanner.app.utils.KeluarSyncManager;
import com.ticketscanner.app.utils.ShiftUtils;
import com.ticketscanner.app.utils.SyncManager;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/** Riwayat tiket Keluar MIP — memakai layout activity_history.xml */
public class KeluarHistoryActivity extends BaseActivity {

    private MaterialButton btnPickDate;
    private Spinner spinnerShift;
    private TextView tvSummary, tvSelectedDate, tvSyncInfo;
    private ProgressBar progressBar;
    private TextInputEditText etSearch;

    private AppDatabase db;
    private KeluarAdapter adapter;
    private final ExecutorService executor = Executors.newSingleThreadExecutor();
    private final Handler mainHandler = new Handler(Looper.getMainLooper());

    private String selectedDate;
    private int selectedShift = 0;
    private String searchQuery = "";
    private List<KeluarScan> all = new ArrayList<>();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        if (!session.isLoggedIn()) return;
        setContentView(R.layout.activity_history);
        if (getSupportActionBar() != null) {
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
            getSupportActionBar().setTitle("Riwayat Keluar MIP");
        }

        db             = AppDatabase.getInstance(this);
        btnPickDate    = findViewById(R.id.btnPickDate);
        spinnerShift   = findViewById(R.id.spinnerShift);
        RecyclerView rv = findViewById(R.id.rvHistory);
        tvSummary      = findViewById(R.id.tvSummary);
        tvSelectedDate = findViewById(R.id.tvSelectedDate);
        tvSyncInfo     = findViewById(R.id.tvSyncInfo);
        progressBar    = findViewById(R.id.progressBar);
        etSearch       = findViewById(R.id.etSearch);

        adapter = new KeluarAdapter();
        adapter.setCanEdit(canEdit());
        adapter.setOnActionListener(new KeluarAdapter.OnActionListener() {
            @Override public void onEdit(KeluarScan k)   { showEditDialog(k); }
            @Override public void onDelete(KeluarScan k) { showDeleteDialog(k); }
        });
        rv.setLayoutManager(new LinearLayoutManager(this));
        rv.setAdapter(adapter);

        selectedDate = ShiftUtils.getCurrentOperationalDate();
        tvSelectedDate.setText(ShiftUtils.formatOperationalDate(selectedDate));

        String[] shiftOptions = {"Semua Shift", "Shift 1 (07:00-19:00)", "Shift 2 (19:00-07:00)"};
        ArrayAdapter<String> shiftAdapter = new ArrayAdapter<String>(this,
                R.layout.item_spinner_dark, shiftOptions) {
            @Override public View getView(int p, View cv, android.view.ViewGroup parent) {
                View v = super.getView(p, cv, parent);
                if (v instanceof TextView) {
                    ((TextView) v).setTextColor(0xFF212121);
                    ((TextView) v).setBackgroundColor(0xFFFFFFFF);
                }
                return v;
            }
            @Override public View getDropDownView(int p, View cv, android.view.ViewGroup parent) {
                View v = super.getDropDownView(p, cv, parent);
                if (v instanceof TextView) {
                    ((TextView) v).setTextColor(0xFF212121);
                    ((TextView) v).setBackgroundColor(0xFFFFFFFF);
                    ((TextView) v).setPadding(32, 24, 32, 24);
                }
                return v;
            }
        };
        shiftAdapter.setDropDownViewResource(R.layout.item_spinner_dark);
        spinnerShift.setAdapter(shiftAdapter);
        spinnerShift.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override public void onItemSelected(AdapterView<?> p, View v, int pos, long id) {
                selectedShift = pos;
                loadData();
            }
            @Override public void onNothingSelected(AdapterView<?> p) {}
        });

        btnPickDate.setOnClickListener(v -> showDatePicker());
        etSearch.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int a, int b, int c) {}
            @Override public void onTextChanged(CharSequence s, int a, int b, int c) {}
            @Override public void afterTextChanged(Editable s) {
                searchQuery = s.toString().trim().toLowerCase(Locale.ROOT);
                applyFilter();
            }
        });

        pullAndLoad();
    }

    private void showDatePicker() {
        Calendar cal = Calendar.getInstance();
        try { cal.setTime(new SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).parse(selectedDate)); }
        catch (Exception ignored) {}
        new DatePickerDialog(this, (view, y, m, d) -> {
            selectedDate = String.format(Locale.getDefault(), "%04d-%02d-%02d", y, m + 1, d);
            tvSelectedDate.setText(ShiftUtils.formatOperationalDate(selectedDate));
            etSearch.setText("");
            pullAndLoad();
        }, cal.get(Calendar.YEAR), cal.get(Calendar.MONTH), cal.get(Calendar.DAY_OF_MONTH)).show();
    }

    private void pullAndLoad() {
        if (!SyncManager.isOnline(this)) { loadData(); return; }
        progressBar.setVisibility(View.VISIBLE);
        tvSyncInfo.setText("⏳ Menyinkronkan...");
        tvSyncInfo.setVisibility(View.VISIBLE);
        KeluarSyncManager.sync(this, selectedDate, new SyncManager.SyncCallback() {
            @Override public void onSuccess(int count) {
                progressBar.setVisibility(View.GONE);
                tvSyncInfo.setText("☁ Up-to-date");
                loadData();
            }
            @Override public void onError(String message) {
                progressBar.setVisibility(View.GONE);
                tvSyncInfo.setText("⚠ " + message);
                loadData();
            }
        });
    }

    private void loadData() {
        executor.execute(() -> {
            List<KeluarScan> list = selectedShift == 0
                ? db.keluarScanDao().getByDate(selectedDate)
                : db.keluarScanDao().getByDateAndShift(selectedDate, selectedShift);
            mainHandler.post(() -> { all = list; applyFilter(); });
        });
    }

    private void applyFilter() {
        List<KeluarScan> filtered = new ArrayList<>();
        for (KeluarScan k : all) {
            if (searchQuery.isEmpty()
                || k.getTicketNumber().toLowerCase(Locale.ROOT).contains(searchQuery)
                || k.getTujuan().toLowerCase(Locale.ROOT).contains(searchQuery)
                || k.getOperator().toLowerCase(Locale.ROOT).contains(searchQuery)) {
                filtered.add(k);
            }
        }
        adapter.setData(filtered);

        // Ringkasan per tujuan
        StringBuilder sb = new StringBuilder();
        for (String t : AppConstants.LOKASI_PENERIMAAN) {
            int n = 0;
            for (KeluarScan k : filtered) if (t.equals(k.getTujuan())) n++;
            if (n > 0) sb.append(sb.length() > 0 ? " | " : "").append(t).append(": ").append(n);
        }
        if (filtered.isEmpty()) {
            tvSummary.setText(searchQuery.isEmpty() ? "Tidak ada data"
                : "Tidak ditemukan: \"" + searchQuery + "\"");
        } else {
            tvSummary.setText(filtered.size() + " DT keluar\n" + sb);
        }
    }

    private void showEditDialog(KeluarScan k) {
        final String[] opts = AppConstants.LOKASI_PENERIMAAN;
        int checked = -1;
        for (int i = 0; i < opts.length; i++) if (opts[i].equals(k.getTujuan())) checked = i;
        final int[] chosen = {checked};
        new AlertDialog.Builder(this)
            .setTitle("Ubah Tujuan — " + k.getTicketNumber())
            .setSingleChoiceItems(opts, checked, (d, w) -> chosen[0] = w)
            .setPositiveButton("Simpan", (d, w) -> {
                if (chosen[0] < 0) return;
                final String tujuan = opts[chosen[0]];
                executor.execute(() -> {
                    k.setTujuan(tujuan);
                    db.keluarScanDao().update(k);
                    if (k.isSynced() && SyncManager.isOnline(this)) {
                        KeluarSyncManager.syncUpdate(this, k.getId(), tujuan, k.getOperationalDate(),
                            new SyncManager.SyncCallback() {
                                @Override public void onSuccess(int c) {}
                                @Override public void onError(String m) {
                                    Toast.makeText(KeluarHistoryActivity.this,
                                        "Gagal update di Sheets: " + m, Toast.LENGTH_LONG).show();
                                }
                            });
                    }
                    mainHandler.post(() -> {
                        loadData();
                        Toast.makeText(this, "✓ Tujuan diubah ke " + tujuan, Toast.LENGTH_SHORT).show();
                    });
                });
            })
            .setNegativeButton("Batal", null)
            .show();
    }

    private void showDeleteDialog(KeluarScan k) {
        new AlertDialog.Builder(this)
            .setTitle("Hapus Data Keluar")
            .setMessage("Hapus tiket " + k.getTicketNumber() + " (tujuan " + k.getTujuan() + ")?\n"
                + "Setelah dihapus, tiket ini bisa discan keluar lagi.")
            .setPositiveButton("Hapus", (d, w) -> executor.execute(() -> {
                db.keluarScanDao().delete(k);
                if (k.isSynced() && SyncManager.isOnline(this)) {
                    KeluarSyncManager.syncDelete(this, k.getId(), k.getOperationalDate(), null);
                }
                mainHandler.post(() -> {
                    loadData();
                    Toast.makeText(this, "Data dihapus", Toast.LENGTH_SHORT).show();
                });
            }))
            .setNegativeButton("Batal", null)
            .show();
    }

    @Override
    public boolean onOptionsItemSelected(@NonNull MenuItem item) {
        if (item.getItemId() == android.R.id.home) { finish(); return true; }
        return super.onOptionsItemSelected(item);
    }
}
