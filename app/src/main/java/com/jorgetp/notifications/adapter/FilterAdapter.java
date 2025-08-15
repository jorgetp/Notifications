package com.jorgetp.notifications.adapter;

import android.content.Context;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.jorgetp.notifications.R;

import java.util.List;
import java.util.Map;

public class FilterAdapter extends RecyclerView.Adapter<FilterAdapter.ViewHolder> {
    private final Context context;
    private final List<Map.Entry<String, Integer>> counts;
    private final OnAppFilterClickListener onAppFilterClickListener;
    private int selectedPosition = 0;

    public FilterAdapter(Context context, List<Map.Entry<String, Integer>> counts, OnAppFilterClickListener onAppFilterClickListener) {
        this.context = context;
        if (counts == null || counts.isEmpty()) {
            this.counts = List.of(Map.entry("all", 0));
        } else {
            this.counts = counts;
        }
        this.onAppFilterClickListener = onAppFilterClickListener;
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(context).inflate(R.layout.item_filter, parent, false);
        return new ViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        Map.Entry<String, Integer> entry = counts.get(position);
        String packageName = entry.getKey();

        holder.itemView.setSelected(selectedPosition == position);

        if ("all".equals(packageName)) {
            holder.ivIcon.setVisibility(View.GONE);
            holder.tvApp.setText(R.string.all);
        } else {
            holder.ivIcon.setVisibility(View.VISIBLE);
            PackageManager pm = context.getPackageManager();
            try {
                ApplicationInfo appInfo = pm.getApplicationInfo(packageName, 0);
                holder.ivIcon.setImageDrawable(pm.getApplicationIcon(appInfo));
                holder.tvApp.setText(pm.getApplicationLabel(appInfo));
            } catch (PackageManager.NameNotFoundException e) {
                holder.ivIcon.setImageResource(R.mipmap.ic_launcher);
                holder.tvApp.setText(packageName);
            }
            //holder.tvApp.setText(String.format("%d", entry.getValue()));
        }

        holder.itemView.setOnClickListener(v -> {
            if (onAppFilterClickListener != null) {
                onAppFilterClickListener.onAppFilterClick(packageName, holder.getAdapterPosition());
            }
        });
    }

    @Override
    public int getItemCount() {
        return counts.size();
    }

    public void setSelectedPosition(int position) {
        int previousPosition = selectedPosition;
        selectedPosition = position;
        notifyItemChanged(previousPosition);
        notifyItemChanged(selectedPosition);
    }

    public interface OnAppFilterClickListener {
        void onAppFilterClick(String packageName, int position);
    }

    public static class ViewHolder extends RecyclerView.ViewHolder {
        ImageView ivIcon;
        TextView tvApp;

        public ViewHolder(@NonNull View itemView) {
            super(itemView);
            ivIcon = itemView.findViewById(R.id.ivIcon);
            tvApp = itemView.findViewById(R.id.tvApp);
        }
    }
}