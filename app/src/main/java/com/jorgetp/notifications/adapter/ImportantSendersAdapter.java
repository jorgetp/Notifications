package com.jorgetp.notifications.adapter;

import static com.jorgetp.notifications.MainActivity.IMPORTANT_SENDERS_PREFS;

import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.drawable.Drawable;
import android.util.Log;
import android.util.Pair;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.recyclerview.widget.RecyclerView;

import com.jorgetp.notifications.MainActivity;
import com.jorgetp.notifications.R;

import java.io.FileInputStream;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Map;

public class ImportantSendersAdapter extends RecyclerView.Adapter<ImportantSendersAdapter.ViewHolder> {
    private final Context context;
    private final ArrayList<ImportantSender> senders;

    public ImportantSendersAdapter(Context context) {
        this.context = context;
        senders = new ArrayList<>(10);
        SharedPreferences prefs = MainActivity.getPrefs(context, IMPORTANT_SENDERS_PREFS);
        for (Map.Entry<String, ?> entry : prefs.getAll().entrySet()) {
            String key = entry.getKey();
            String value = entry.getValue().toString();
            String[] parts = key.split("/", 2);
            senders.add(new ImportantSender(parts[0], parts[1], value));
        }
        senders.sort(Comparator.comparing(sender -> sender.sender));
    }

    @Override
    public int getItemCount() {
        return senders.size();
    }

    @Override
    public long getItemId(int position) {
        return position;
    }

    @NonNull
    @Override
    public ImportantSendersAdapter.ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(context).inflate(R.layout.item, parent, false);
        return new ImportantSendersAdapter.ViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull ImportantSendersAdapter.ViewHolder holder, int i) {
        ImportantSender sender = senders.get(i);

        // load app name and icon
        Pair<CharSequence, Drawable> appInfo = MainActivity.getAppInfo(context, sender.packageName);
        holder.tvApp.setText(appInfo.first);
        holder.tvSender.setText(sender.sender);

        // small icon
        if (appInfo.second != null)
            holder.ivSmallIcon.setImageDrawable(appInfo.second);
        else
            holder.ivSmallIcon.setImageResource(android.R.drawable.sym_def_app_icon);

        // large icon
        // holder.ivLargeIcon.setImageBitmap(MainActivity.createIconBitmap(sender.packageName, sender.sender));
        holder.ivLargeIcon.setVisibility(View.GONE);
        try (FileInputStream fis = context
                .openFileInput("notification_icon_" + sender.uuid + ".png")) {
            Bitmap iconBitmap = BitmapFactory.decodeStream(fis);
            holder.ivLargeIcon.setImageBitmap(iconBitmap);
            holder.ivLargeIcon.setVisibility(View.VISIBLE);
        } catch (Exception e) {
            Log.e("NotificationsAdapter", "Icon not found", e);
        }

        holder.itemView.setOnClickListener(v -> new AlertDialog.Builder(context)
                .setMessage(R.string.unset_as_important_confirmation)
                .setPositiveButton(android.R.string.yes, (dialog, id) -> {
                    MainActivity.getPrefs(context, IMPORTANT_SENDERS_PREFS)
                            .edit()
                            .remove(sender.packageName + "/" + sender.sender)
                            .apply();
                    senders.remove(holder.getBindingAdapterPosition());
                    notifyItemRemoved(holder.getBindingAdapterPosition());
                })
                .setNegativeButton(android.R.string.cancel, null)
                .create()
                .show());
    }

    public static class ViewHolder extends RecyclerView.ViewHolder {
        ImageView ivSmallIcon;
        ImageView ivLargeIcon;
        TextView tvApp;
        TextView tvSender;

        public ViewHolder(@NonNull View itemView) {
            super(itemView);
            ivSmallIcon = itemView.findViewById(R.id.ivSmallIcon);
            ivLargeIcon = itemView.findViewById(R.id.ivLargeIcon);
            tvApp = itemView.findViewById(R.id.tvApp);
            tvSender = itemView.findViewById(R.id.tvTitle);

            TextView tvTime = itemView.findViewById(R.id.tvTime);
            TextView tvTap = itemView.findViewById(R.id.tvText);
            tvTime.setVisibility(View.GONE);
            tvTap.setText(R.string.tap_to_unset_as_important);
        }
    }

    private static class ImportantSender {
        private final String packageName;
        private final String sender;
        private final String uuid;

        public ImportantSender(String packageName, String sender, String uuid) {
            this.packageName = packageName;
            this.sender = sender;
            this.uuid = uuid;
        }
    }
}
