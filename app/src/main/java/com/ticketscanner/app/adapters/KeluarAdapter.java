package com.ticketscanner.app.adapters;

import android.content.res.ColorStateList;
import android.graphics.Color;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.button.MaterialButton;
import com.ticketscanner.app.R;
import com.ticketscanner.app.models.KeluarScan;
import com.ticketscanner.app.utils.ShiftUtils;

import java.util.ArrayList;
import java.util.List;

/** Daftar tiket keluar MIP — memakai layout item_scan.xml (tanpa tonnase). */
public class KeluarAdapter extends RecyclerView.Adapter<KeluarAdapter.ViewHolder> {

    public interface OnActionListener {
        void onEdit(KeluarScan scan);
        void onDelete(KeluarScan scan);
    }

    private List<KeluarScan> data = new ArrayList<>();
    private OnActionListener listener;
    private boolean canEdit = false;

    public void setData(List<KeluarScan> d) { data = d != null ? d : new ArrayList<>(); notifyDataSetChanged(); }
    public void setOnActionListener(OnActionListener l) { listener = l; }
    public void setCanEdit(boolean v) { canEdit = v; notifyDataSetChanged(); }

    @NonNull @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        return new ViewHolder(LayoutInflater.from(parent.getContext())
            .inflate(R.layout.item_scan, parent, false));
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder h, int pos) {
        KeluarScan k = data.get(pos);
        h.tvNo.setText(String.valueOf(pos + 1));
        h.tvTicket.setText(k.getTicketNumber());

        // Baris tonnase dipakai untuk menampilkan tujuan
        h.tvTonnage.setText("→ " + k.getTujuan());
        h.tvTonnage.setTextColor(Color.parseColor("#BF360C"));
        h.tvTonnageOriginal.setVisibility(View.GONE);
        h.tvStockpile.setVisibility(View.GONE);
        h.viewRemarkStrip.setVisibility(View.GONE);
        h.layoutRemarkInfo.setVisibility(View.GONE);

        h.tvTime.setText(ShiftUtils.formatDateTime(k.getScanTimestamp()));
        h.tvOperator.setText(k.getOperator().isEmpty() ? "-" : k.getOperator());
        h.tvSyncStatus.setText(k.isSynced() ? "☁✓" : "⏳");
        h.tvSyncStatus.setTextColor(k.isSynced() ? Color.parseColor("#2E7D32") : Color.parseColor("#E65100"));

        h.tvShift.setText("Shift " + k.getShiftNumber());
        h.tvShift.setBackgroundTintList(ColorStateList.valueOf(k.getShiftNumber() == 1
            ? Color.parseColor("#1565C0") : Color.parseColor("#E65100")));

        int vis = canEdit ? View.VISIBLE : View.GONE;
        h.btnEdit.setVisibility(vis);
        h.btnDelete.setVisibility(vis);
        h.btnEdit.setOnClickListener(v -> { if (listener != null) listener.onEdit(k); });
        h.btnDelete.setOnClickListener(v -> { if (listener != null) listener.onDelete(k); });
    }

    @Override public int getItemCount() { return data.size(); }

    static class ViewHolder extends RecyclerView.ViewHolder {
        TextView tvNo, tvTicket, tvTonnage, tvTonnageOriginal, tvShift, tvTime, tvSyncStatus, tvStockpile, tvOperator;
        View viewRemarkStrip, layoutRemarkInfo;
        MaterialButton btnEdit, btnDelete;
        ViewHolder(@NonNull View v) {
            super(v);
            tvNo              = v.findViewById(R.id.tvNo);
            tvTicket          = v.findViewById(R.id.tvTicket);
            tvTonnage         = v.findViewById(R.id.tvTonnage);
            tvTonnageOriginal = v.findViewById(R.id.tvTonnageOriginal);
            tvShift           = v.findViewById(R.id.tvShift);
            tvTime            = v.findViewById(R.id.tvTime);
            tvSyncStatus      = v.findViewById(R.id.tvSyncStatus);
            tvStockpile       = v.findViewById(R.id.tvStockpile);
            tvOperator        = v.findViewById(R.id.tvOperator);
            viewRemarkStrip   = v.findViewById(R.id.viewRemarkStrip);
            layoutRemarkInfo  = v.findViewById(R.id.layoutRemarkInfo);
            btnEdit           = v.findViewById(R.id.btnEdit);
            btnDelete         = v.findViewById(R.id.btnDelete);
        }
    }
}
