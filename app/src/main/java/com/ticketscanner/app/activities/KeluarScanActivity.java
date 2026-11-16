package com.ticketscanner.app.activities;

import android.Manifest;
import android.content.pm.PackageManager;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.MenuItem;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.widget.SwitchCompat;
import androidx.camera.core.CameraSelector;
import androidx.camera.core.ImageAnalysis;
import androidx.camera.core.Preview;
import androidx.camera.lifecycle.ProcessCameraProvider;
import androidx.camera.view.PreviewView;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import com.google.android.material.button.MaterialButton;
import com.google.android.material.textfield.TextInputEditText;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.mlkit.vision.barcode.BarcodeScanner;
import com.google.mlkit.vision.barcode.BarcodeScannerOptions;
import com.google.mlkit.vision.barcode.BarcodeScanning;
import com.google.mlkit.vision.barcode.common.Barcode;
import com.google.mlkit.vision.common.InputImage;
import com.ticketscanner.app.R;
import com.ticketscanner.app.constants.AppConstants;
import com.ticketscanner.app.database.AppDatabase;
import com.ticketscanner.app.models.KeluarScan;
import com.ticketscanner.app.models.User;
import com.ticketscanner.app.utils.BarcodeParser;
import com.ticketscanner.app.utils.KeluarSyncManager;
import com.ticketscanner.app.utils.ShiftUtils;
import com.ticketscanner.app.utils.SyncManager;

import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Scan tiket DT keluar dari site MIP.
 *  - Tanpa tonnase
 *  - Wajib pilih Tujuan: SDJ / PT MAS / KM107 / KM36 / MERAPI 2
 *  - Setelah simpan, layar tetap terbuka untuk scan berikutnya
 */
public class KeluarScanActivity extends BaseActivity {

    private static final int CAMERA_PERMISSION_CODE = 101;
    private static final int COLOR_MAIN = Color.parseColor("#BF360C");

    private PreviewView previewView;
    private TextView tvInfo, tvCounter, tvStatus, tvTicketLabel, tvSjbPrefix;
    private TextInputEditText etTicket;
    private SwitchCompat switchManual;
    private MaterialButton btnSave;
    private final MaterialButton[] tujuanButtons = new MaterialButton[5];

    private AppDatabase db;
    private BarcodeScanner barcodeScanner;
    private final ExecutorService cameraExecutor = Executors.newSingleThreadExecutor();
    private final ExecutorService dbExecutor     = Executors.newSingleThreadExecutor();
    private final Handler mainHandler = new Handler(Looper.getMainLooper());

    private volatile boolean isProcessing = false;
    private boolean manualMode  = false;
    private String  selectedTujuan = "";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        if (!session.isLoggedIn()) return;
        setContentView(R.layout.activity_scan_keluar);

        if (isGuest()) {
            Toast.makeText(this, "Akses ditolak — role Guest hanya bisa melihat data",
                Toast.LENGTH_SHORT).show();
            finish();
            return;
        }
        if (getSupportActionBar() != null) {
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
            getSupportActionBar().setTitle("Scan Keluar MIP");
            getSupportActionBar().setBackgroundDrawable(
                new android.graphics.drawable.ColorDrawable(COLOR_MAIN));
        }

        db = AppDatabase.getInstance(this);
        barcodeScanner = BarcodeScanning.getClient(
            new BarcodeScannerOptions.Builder().setBarcodeFormats(Barcode.FORMAT_ALL_FORMATS).build());

        previewView   = findViewById(R.id.previewViewKeluar);
        tvInfo        = findViewById(R.id.tvKeluarInfo);
        tvCounter     = findViewById(R.id.tvKeluarCounter);
        tvStatus      = findViewById(R.id.tvKeluarStatus);
        tvTicketLabel = findViewById(R.id.tvKeluarTicketLabel);
        tvSjbPrefix   = findViewById(R.id.tvKeluarSjbPrefix);
        etTicket      = findViewById(R.id.etKeluarTicket);
        switchManual  = findViewById(R.id.switchKeluarManual);
        btnSave       = findViewById(R.id.btnSaveKeluar);

        tujuanButtons[0] = findViewById(R.id.btnTujuanSdj);
        tujuanButtons[1] = findViewById(R.id.btnTujuanMas);
        tujuanButtons[2] = findViewById(R.id.btnTujuanKm107);
        tujuanButtons[3] = findViewById(R.id.btnTujuanKm36);
        tujuanButtons[4] = findViewById(R.id.btnTujuanMerapi);
        for (int i = 0; i < tujuanButtons.length; i++) {
            final String tujuan = AppConstants.LOKASI_PENERIMAAN[i];
            tujuanButtons[i].setText(tujuan);
            tujuanButtons[i].setOnClickListener(v -> selectTujuan(tujuan));
        }
        selectTujuan("");

        // Input manual: admin, supervisor & operator (v77: supervisor ditambahkan)
        String role = session.getRole();
        if (User.ROLE_ADMIN.equals(role) || User.ROLE_SUPERVISOR.equals(role)
                || User.ROLE_OPERATOR.equals(role)) {
            LinearLayout toggle = findViewById(R.id.layoutKeluarManualToggle);
            toggle.setVisibility(View.VISIBLE);
            switchManual.setOnCheckedChangeListener((b, checked) -> applyManualMode(checked));
        }
        lockTicketField(true);

        btnSave.setOnClickListener(v -> saveKeluar());
        refreshHeader();
        checkCameraAndStart();
    }

    // ── Header: tanggal, shift, jumlah tercatat hari ini ─────────
    private void refreshHeader() {
        String opDate = ShiftUtils.getCurrentOperationalDate();
        tvInfo.setText(ShiftUtils.formatOperationalDate(opDate) + "  •  "
            + ShiftUtils.getShiftLabel(ShiftUtils.getCurrentShift()));
        dbExecutor.execute(() -> {
            int n = db.keluarScanDao().countByDate(opDate);
            mainHandler.post(() -> tvCounter.setText("Tercatat hari ini: " + n + " DT"));
        });
    }

    // ── Pilihan tujuan ────────────────────────────────────────────
    private void selectTujuan(String tujuan) {
        selectedTujuan = tujuan;
        for (int i = 0; i < tujuanButtons.length; i++) {
            boolean sel = AppConstants.LOKASI_PENERIMAAN[i].equals(tujuan);
            MaterialButton b = tujuanButtons[i];
            b.setBackgroundTintList(ColorStateList.valueOf(sel ? COLOR_MAIN : Color.WHITE));
            b.setTextColor(sel ? Color.WHITE : COLOR_MAIN);
            b.setStrokeColor(ColorStateList.valueOf(COLOR_MAIN));
            b.setStrokeWidth(sel ? 0 : 3);
        }
    }

    // ── Mode manual ───────────────────────────────────────────────
    private void applyManualMode(boolean enabled) {
        manualMode = enabled;
        etTicket.setText("");
        lockTicketField(!enabled);
        tvSjbPrefix.setVisibility(enabled ? View.VISIBLE : View.GONE);
        tvTicketLabel.setText(enabled ? "No. Tiket (Input Manual — ketik nomornya saja)"
                                      : "No. Tiket (dari Barcode)");
        setStatus(enabled ? "⚠ Mode Manual aktif" : "Kamera aktif — arahkan ke barcode", !enabled);
    }

    private void lockTicketField(boolean locked) {
        etTicket.setFocusable(!locked);
        etTicket.setFocusableInTouchMode(!locked);
        etTicket.setClickable(!locked);
        etTicket.setCursorVisible(!locked);
        if (locked) {
            etTicket.setKeyListener(null);
            etTicket.setHint("Menunggu scan barcode...");
        } else {
            etTicket.setKeyListener(android.text.method.DigitsKeyListener.getInstance(
                "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789-_/"));
            etTicket.setHint("Nomor tiket saja...");
            etTicket.requestFocus();
        }
    }

    // ── Kamera ────────────────────────────────────────────────────
    private void checkCameraAndStart() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA)
                == PackageManager.PERMISSION_GRANTED) startCamera();
        else ActivityCompat.requestPermissions(this,
                new String[]{Manifest.permission.CAMERA}, CAMERA_PERMISSION_CODE);
    }

    @Override
    public void onRequestPermissionsResult(int req, @NonNull String[] perms, @NonNull int[] res) {
        super.onRequestPermissionsResult(req, perms, res);
        if (req == CAMERA_PERMISSION_CODE && res.length > 0
                && res[0] == PackageManager.PERMISSION_GRANTED) startCamera();
        else setStatus("Izin kamera ditolak", false);
    }

    private void startCamera() {
        ListenableFuture<ProcessCameraProvider> future = ProcessCameraProvider.getInstance(this);
        future.addListener(() -> {
            try {
                ProcessCameraProvider cp = future.get();
                Preview preview = new Preview.Builder().build();
                preview.setSurfaceProvider(previewView.getSurfaceProvider());
                ImageAnalysis analysis = new ImageAnalysis.Builder()
                    .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST).build();
                analysis.setAnalyzer(cameraExecutor, imageProxy -> {
                    if (!isProcessing && !manualMode && imageProxy.getImage() != null) {
                        InputImage img = InputImage.fromMediaImage(imageProxy.getImage(),
                            imageProxy.getImageInfo().getRotationDegrees());
                        barcodeScanner.process(img)
                            .addOnSuccessListener(barcodes -> {
                                if (!barcodes.isEmpty()) {
                                    String raw = barcodes.get(0).getRawValue();
                                    if (raw != null && !raw.isEmpty()) {
                                        isProcessing = true;
                                        mainHandler.post(() -> processBarcode(raw));
                                        mainHandler.postDelayed(() -> isProcessing = false, 1500);
                                    }
                                }
                            })
                            .addOnCompleteListener(t -> imageProxy.close());
                    } else {
                        imageProxy.close();
                    }
                });
                cp.unbindAll();
                cp.bindToLifecycle(this, CameraSelector.DEFAULT_BACK_CAMERA, preview, analysis);
                setStatus("Kamera aktif — arahkan ke barcode", true);
            } catch (ExecutionException | InterruptedException e) {
                setStatus("Gagal buka kamera: " + e.getMessage(), false);
            }
        }, ContextCompat.getMainExecutor(this));
    }

    private void processBarcode(String raw) {
        BarcodeParser.ParseResult parsed = BarcodeParser.parse(raw);
        String ticket = (parsed.isValid ? parsed.ticketNumber : raw).trim();
        if (!ticket.toUpperCase().startsWith(AppConstants.TICKET_SJB_PREFIX)) {
            setStatus("✗ Barcode tidak valid — bukan tiket SJB", false);
            Toast.makeText(this, "⚠ Barcode tidak dikenali!\nHarus diawali \"SJB-\"",
                Toast.LENGTH_SHORT).show();
            return;
        }
        // Barcode yang sama terbaca ulang sebelum disimpan → abaikan
        if (ticket.equals(getText(etTicket))) return;
        etTicket.setText(ticket);
        setStatus("✓ " + ticket + " — pilih tujuan lalu SIMPAN", true);
    }

    // ── Simpan ────────────────────────────────────────────────────
    private void saveKeluar() {
        String input = getText(etTicket);
        String ticket = manualMode
            ? (input.isEmpty() ? "" : AppConstants.TICKET_SJB_PREFIX + input)
            : input;

        if (ticket.isEmpty()) {
            Toast.makeText(this, manualMode ? "⚠ Isi nomor tiket!" : "⚠ Scan barcode tiket dulu!",
                Toast.LENGTH_SHORT).show();
            return;
        }
        if (!ticket.toUpperCase().startsWith(AppConstants.TICKET_SJB_PREFIX)) {
            Toast.makeText(this, "⚠ No. Tiket harus diawali \"SJB-\"", Toast.LENGTH_SHORT).show();
            return;
        }
        if (selectedTujuan.isEmpty()) {
            Toast.makeText(this, "⚠ Pilih Tujuan terlebih dahulu!", Toast.LENGTH_SHORT).show();
            setStatus("⚠ Pilih Tujuan: SDJ / PT MAS / KM107 / KM36 / MERAPI 2", false);
            return;
        }

        final String finalTicket = ticket;
        final String tujuan      = selectedTujuan;
        final long   now         = System.currentTimeMillis();
        final String operator    = session.getNamaLengkap() + " (" + session.getUsername() + ")"
                                   + (manualMode ? " [MANUAL]" : "");
        btnSave.setEnabled(false);

        dbExecutor.execute(() -> {
            KeluarScan existing = db.keluarScanDao().getByTicket(finalTicket);
            if (existing != null) {
                mainHandler.post(() -> {
                    btnSave.setEnabled(true);
                    new AlertDialog.Builder(this)
                        .setTitle("⚠ Tiket Sudah Tercatat Keluar")
                        .setMessage(finalTicket + "\nsudah tercatat pada "
                            + ShiftUtils.formatOperationalDate(existing.getOperationalDate())
                            + " tujuan " + existing.getTujuan() + ".")
                        .setPositiveButton("OK", null)
                        .show();
                    setStatus("⚠ " + finalTicket + " sudah tercatat keluar", false);
                    clearForNext();
                });
                return;
            }

            KeluarScan k = new KeluarScan();
            k.setId(now);  // milidetik — unik untuk scan beruntun
            k.setTicketNumber(finalTicket);
            k.setTujuan(tujuan);
            k.setScanTimestamp(now);
            k.setShiftNumber(ShiftUtils.getShiftNumber(now));
            k.setOperationalDate(ShiftUtils.getOperationalDate(now));
            k.setOperator(operator);
            k.setSynced(false);
            db.keluarScanDao().insert(k);

            mainHandler.post(() -> {
                btnSave.setEnabled(true);
                Toast.makeText(this, "✓ " + finalTicket + " → " + tujuan, Toast.LENGTH_SHORT).show();
                setStatus("✓ Tersimpan " + finalTicket + " → " + tujuan + " — scan berikutnya", true);
                clearForNext();
                refreshHeader();
            });

            if (SyncManager.isOnline(getApplicationContext())) {
                KeluarSyncManager.syncPending(getApplicationContext(), null);
            }
        });
    }

    private void clearForNext() {
        etTicket.setText("");
        selectTujuan("");
        if (manualMode) etTicket.requestFocus();
    }

    // ── Helper ────────────────────────────────────────────────────
    private String getText(TextInputEditText et) {
        return et.getText() != null ? et.getText().toString().trim() : "";
    }

    private void setStatus(String msg, boolean ok) {
        Runnable r = () -> {
            tvStatus.setText(msg);
            tvStatus.setBackgroundColor(ok ? 0xFF1B5E20 : 0xFF37474F);
        };
        if (Looper.myLooper() == Looper.getMainLooper()) r.run(); else mainHandler.post(r);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        cameraExecutor.shutdown();
        dbExecutor.shutdown();
        if (barcodeScanner != null) barcodeScanner.close();
    }

    @Override
    public boolean onOptionsItemSelected(@NonNull MenuItem item) {
        if (item.getItemId() == android.R.id.home) { finish(); return true; }
        return super.onOptionsItemSelected(item);
    }
}
