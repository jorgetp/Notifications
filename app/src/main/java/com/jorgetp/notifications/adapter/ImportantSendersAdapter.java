package com.jorgetp.notifications.adapter;

import static com.jorgetp.notifications.MainActivity.IMPORTANT_SENDERS_PREFS;

import android.content.Context;
import android.content.SharedPreferences;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.drawable.Drawable;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.recyclerview.widget.RecyclerView;

import com.jorgetp.notifications.R;

import java.io.FileInputStream;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

public class ImportantSendersAdapter extends RecyclerView.Adapter<ImportantSendersAdapter.ViewHolder> {
    private final List<ImportantSender> senders;
    private final Context context;
    private final SharedPreferences prefs;

    public ImportantSendersAdapter(Context context) {
        this.context = context;
        senders = new ArrayList<>(10);
        prefs = context.getSharedPreferences(IMPORTANT_SENDERS_PREFS, Context.MODE_PRIVATE);
        for (String key : prefs.getAll().keySet()) {
            String value = prefs.getString(key, null);
            String[] parts = key.split("/");
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
        View view = LayoutInflater.from(context).inflate(R.layout.item_important_sender, parent, false);
        return new ImportantSendersAdapter.ViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull ImportantSendersAdapter.ViewHolder holder, int i) {
        ImportantSender sender = senders.get(i);
        holder.tvSender.setText(sender.sender);

        // Load app name and icon
        try {
            ApplicationInfo appInfo = context.getPackageManager().getApplicationInfo(sender.packageName, 0);
            Drawable appIcon = context.getPackageManager().getApplicationIcon(appInfo);
            holder.ivIcon.setImageDrawable(appIcon);
            holder.ivIconSecondary.setVisibility(View.GONE);

            // load sender icon from internal storage if it exists
            //Executors.newSingleThreadExecutor().execute(() -> {
            try (FileInputStream fis = context
                    .openFileInput("notification_icon_" + sender.notificationId + ".png")) {
                Bitmap iconBitmap = BitmapFactory.decodeStream(fis);
                //((Activity) context).runOnUiThread(() -> {
                holder.ivIcon.setImageBitmap(iconBitmap);
                holder.ivIconSecondary.setVisibility(View.VISIBLE);
                holder.ivIconSecondary.setImageDrawable(appIcon);
                //});
            } catch (Exception e) {
                Log.e("NotificationsAdapter", "Icon not found", e);
            }
            //});

        } catch (PackageManager.NameNotFoundException e) {
            Log.e("ImportantSendersAdapter", "App not found", e);
        }

        holder.itemView.setOnClickListener(v -> new AlertDialog.Builder(context)
                .setMessage(R.string.unset_as_important_confirmation)
                .setPositiveButton(android.R.string.yes, (dialog, id) -> {
                    prefs.edit().remove(sender.packageName + "/" + sender.sender).apply();
                    senders.remove(i);
                    notifyItemRemoved(i);

                    Toast.makeText(context, context.getString(R.string.unset_as_important),
                            Toast.LENGTH_SHORT).show();
                })
                .setNegativeButton(android.R.string.cancel, null)
                .create()
                .show());
    }

    public static class ViewHolder extends RecyclerView.ViewHolder {
        ImageView ivIcon;
        ImageView ivIconSecondary;
        TextView tvSender;

        public ViewHolder(@NonNull View itemView) {
            super(itemView);
            ivIcon = itemView.findViewById(R.id.ivIcon);
            ivIconSecondary = itemView.findViewById(R.id.ivIconSecondary);
            tvSender = itemView.findViewById(R.id.tvSender);
        }
    }

    public static class ImportantSender {
        private final String packageName;
        private final String sender;
        private final String notificationId;

        public ImportantSender(String packageName, String sender, String notificationId) {
            this.packageName = packageName;
            this.sender = sender;
            this.notificationId = notificationId;
        }
    }
}

