package com.jorgetp.notifications.adapter;

import static com.jorgetp.notifications.MainActivity.ALWAYS;
import static com.jorgetp.notifications.MainActivity.IMPORTANT_SENDERS_PREFS;
import static com.jorgetp.notifications.MainActivity.NON_BUSINESS;
import static com.jorgetp.notifications.MainActivity.SILENCED_APPS_PREFS;
import static com.jorgetp.notifications.MainActivity.getAppInfo;

import android.app.Activity;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.drawable.Drawable;
import android.util.Pair;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.PopupMenu;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.widget.SwitchCompat;
import androidx.recyclerview.widget.RecyclerView;

import com.jorgetp.notifications.MainActivity;
import com.jorgetp.notifications.R;
import com.jorgetp.notifications.SettingsActivity;
import com.jorgetp.notifications.dao.DbProvider;
import com.jorgetp.notifications.dao.IconDao;
import com.jorgetp.notifications.dao.StoredIcon;

import java.util.TreeSet;
import java.util.concurrent.Executors;

public class SettingsAdapter extends NotificationsAdapter {
    public static final String CHANNEL_ID = "com.jorgetp.notifications";

    private final TreeSet<String> editedItems = new TreeSet<>();

    private final ActivityResultLauncher<Intent> nslSettingsLauncher;

    public SettingsAdapter(Activity activity) {
        super(activity);

        nslSettingsLauncher = ((SettingsActivity) activity).registerForActivityResult(
                new ActivityResultContracts.StartActivityForResult(), result -> {
                    boolean enabled = ((SettingsActivity) activity).isNotificationServiceEnabled();
                    if (enabled && !((Switch) items.get(1)).checked) {
                        NotificationManager notificationManager = (NotificationManager) activity.getSystemService(
                                Context.NOTIFICATION_SERVICE);
                        NotificationChannel channel = new NotificationChannel(
                                CHANNEL_ID,
                                activity.getString(R.string.app_name),
                                NotificationManager.IMPORTANCE_HIGH);
                        notificationManager.createNotificationChannel(channel);

                        Toast.makeText(activity, R.string.nsl, Toast.LENGTH_SHORT).show();
                    }
                    items.set(1, new Switch(activity.getString(R.string.nsl), enabled));
                    notifyItemChanged(1);

                });
    }

    // Helper method to convert byte array back to Bitmap
    private Bitmap byteArrayToBitmap(byte[] bytes) {
        if (bytes == null || bytes.length == 0) return null;
        return BitmapFactory.decodeByteArray(bytes, 0, bytes.length);
    }

    public TreeSet<String> getEditedItems() {
        return editedItems;
    }

    @NonNull
    @Override
    public RecyclerView.ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        if (viewType == 0) {
            View view = LayoutInflater.from(activity).inflate(R.layout.header, parent, false);
            return new NotificationsAdapter.HeaderViewHolder(view);
        } else if (viewType == 1) {
            View view = LayoutInflater.from(activity).inflate(R.layout.item, parent, false);
            return new SilencedAppViewHolder(view);
        } else if (viewType == 2) {
            View view = LayoutInflater.from(activity).inflate(R.layout.item, parent, false);
            return new ImportantSenderViewHolder(view);
        } else /* if (viewType == 3) */ {
            View view = LayoutInflater.from(activity).inflate(R.layout.item_switch, parent, false);
            return new SwitchViewHolder(view);
        }
    }

    @Override
    public int getItemViewType(int position) {
        Object o = getItem(position);
        if (o instanceof String)
            return 0;
        if (o instanceof SilencedApp)
            return 1;
        if (o instanceof ImportantSender)
            return 2;
        if (o instanceof Switch)
            return 3;
        return -1;
    }

    @Override
    public void onBindViewHolder(@NonNull RecyclerView.ViewHolder holderGeneric, int i) {
        if (holderGeneric instanceof NotificationsAdapter.HeaderViewHolder) {
            NotificationsAdapter.HeaderViewHolder holder = (NotificationsAdapter.HeaderViewHolder) holderGeneric;
            String header = (String) getItem(i);
            holder.tvHeader.setText(header);

        } else if (holderGeneric instanceof SwitchViewHolder) {
            SwitchViewHolder holder = (SwitchViewHolder) holderGeneric;
            holder.switch1.setText(((Switch) getItem(i)).title);
            holder.switch1.setChecked(((Switch) getItem(i)).checked);
            holder.ivIcon.setImageDrawable(getAppInfo(activity, activity.getPackageName()).second);

            holder.itemView.setBackgroundResource(getBackground(i));

            holder.switch1.setOnCheckedChangeListener((buttonView, isChecked) -> {
                if (buttonView.isPressed()) { // to avoid infinite loop when updating the switch state programmatically
                    if (i == 1) { // NSL enabled/disabled
                        Intent intent = new Intent(android.provider.Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS);
                        nslSettingsLauncher.launch(intent);
                    }
                }
            });

        } else if (holderGeneric instanceof SilencedAppViewHolder) {
            SilencedAppViewHolder holder = (SilencedAppViewHolder) holderGeneric;

            SilencedApp app = (SilencedApp) getItem(i);
            holder.itemView.setBackgroundResource(getBackground(i));

            // load app name and icon
            Pair<CharSequence, Drawable> appInfo = MainActivity.getAppInfo(activity, app.packageName);
            holder.tvApp.setText(appInfo.first);
            if (appInfo.second != null)
                holder.ivIcon.setImageDrawable(appInfo.second);
            else
                holder.ivIcon.setImageResource(android.R.drawable.sym_def_app_icon);

            holder.tvSilencedWhen.setText(app.silencedWhen == ALWAYS ? activity.getString(R.string.silenced_always)
                    : activity.getString(R.string.silenced_non_business));

            holder.itemView.setOnClickListener(v -> {
                PopupMenu popup = new PopupMenu(activity, v);
                popup.getMenuInflater().inflate(R.menu.menu_app_popup, popup.getMenu());
                SharedPreferences prefs = MainActivity.getPrefs(activity, SILENCED_APPS_PREFS);

                popup.setOnMenuItemClickListener(item -> {
                    int itemId = item.getItemId();
                    int position = holder.getBindingAdapterPosition();

                    if (itemId == R.id.silenced_always) {
                        editedItems.add(app.packageName);
                        prefs.edit().putInt(app.packageName, ALWAYS).apply();
                        items.set(position, new SilencedApp(app.packageName, ALWAYS));
                        notifyItemChanged(position);
                        return true;

                    } else if (itemId == R.id.silenced_non_business) {
                        editedItems.add(app.packageName);
                        prefs.edit().putInt(app.packageName, NON_BUSINESS).apply();
                        items.set(position, new SilencedApp(app.packageName, NON_BUSINESS));
                        notifyItemChanged(position);
                        return true;

                    } else if (itemId == R.id.not_silenced) {
                        editedItems.add(app.packageName);
                        prefs.edit().remove(app.packageName).apply();
                        items.remove(position);
                        // notifyDataSetChanged();
                        if (position > 0)
                            notifyItemChanged(position - 1);
                        notifyItemRemoved(position);
                        notifyItemRangeChanged(position, getItemCount() - position);
                        return true;
                    }
                    return false;
                });

                popup.show();
            });

        } else {
            ImportantSenderViewHolder holder = (ImportantSenderViewHolder) holderGeneric;

            ImportantSender sender = (ImportantSender) getItem(i);
            holder.itemView.setBackgroundResource(getBackground(i));

            // load app name and icon
            Pair<CharSequence, Drawable> appInfo = MainActivity.getAppInfo(activity, sender.packageName);
            holder.tvSender.setText(!sender.sender.isEmpty() ? sender.sender : activity.getString(R.string.no_title));

            // app icon
            if (appInfo.second != null)
                holder.ivAppIcon.setImageDrawable(appInfo.second);
            else
                holder.ivAppIcon.setImageResource(android.R.drawable.sym_def_app_icon);

            // sender icon - load from notification_icons table
            holder.ivSenderIcon.setVisibility(View.GONE);

            // Load icon from notification_icons table
            holder.ivSenderIcon.setVisibility(View.GONE);
            Executors.newSingleThreadExecutor().execute(() -> {
                try {
                    IconDao iconDao = DbProvider.get(activity).iconDao();
                    StoredIcon icon = iconDao.get(sender.packageName, sender.sender);
                    if (icon != null && icon.iconData != null && icon.iconData.length > 0) {
                        Bitmap iconBitmap = byteArrayToBitmap(icon.iconData);
                        if (iconBitmap != null) {
                            // Update UI on main thread
                            activity.runOnUiThread(() -> {
                                holder.ivSenderIcon.setImageBitmap(iconBitmap);
                                holder.ivSenderIcon.setVisibility(View.VISIBLE);
                            });
                        }
                    } else {
                        // Fallback: build icon from sender icon
                        Bitmap bm = createIconBitmap(sender.packageName, sender.sender);
                        if (bm != null) {
                            activity.runOnUiThread(() -> {
                                holder.ivSenderIcon.setImageBitmap(bm);
                                holder.ivSenderIcon.setVisibility(View.VISIBLE);
                            });
                        }
                    }
                } catch (Exception e) {
                    // Log.e("SettingsAdapter", "Error loading icon from database", e);
                }
            });

            holder.itemView.setOnClickListener(v -> new AlertDialog.Builder(activity)
                    .setMessage(R.string.unset_as_important_confirmation)
                    .setPositiveButton(android.R.string.yes, (dialog, id) -> {
                        int position = holder.getBindingAdapterPosition();
                        editedItems.add(sender.packageName + "/" + sender.sender);
                        MainActivity.getPrefs(activity, IMPORTANT_SENDERS_PREFS)
                                .edit()
                                .remove(sender.packageName + "/" + sender.sender)
                                .apply();
                        items.remove(position);
                        // notifyDataSetChanged();
                        if (position > 0)
                            notifyItemChanged(position - 1);
                        notifyItemRemoved(position);
                        notifyItemRangeChanged(position, getItemCount() - position);
                    })
                    .setNegativeButton(android.R.string.cancel, null)
                    .create()
                    .show());
        }
    }

    public static class ImportantSenderViewHolder extends RecyclerView.ViewHolder {
        ImageView ivAppIcon;
        ImageView ivSenderIcon;
        TextView tvSender;

        public ImportantSenderViewHolder(@NonNull View itemView) {
            super(itemView);
            ivAppIcon = itemView.findViewById(R.id.ivAppIcon);
            ivSenderIcon = itemView.findViewById(R.id.ivSenderIcon);
            tvSender = itemView.findViewById(R.id.tvTitle);

            itemView.findViewById(R.id.tvTime).setVisibility(View.GONE);
            ((TextView) itemView.findViewById(R.id.tvText)).setText(R.string.tap_to_unset_as_important);
        }
    }

    public static class ImportantSender {
        public final String packageName;
        public final String sender;

        public ImportantSender(String packageName, String sender) {
            this.packageName = packageName;
            this.sender = sender;
        }
    }

    public static class SilencedAppViewHolder extends RecyclerView.ViewHolder {
        ImageView ivIcon;
        TextView tvApp;
        TextView tvSilencedWhen;

        public SilencedAppViewHolder(@NonNull View itemView) {
            super(itemView);
            ivIcon = itemView.findViewById(R.id.ivAppIcon);
            tvApp = itemView.findViewById(R.id.tvTitle);
            tvSilencedWhen = itemView.findViewById(R.id.tvText);

            itemView.findViewById(R.id.tvTime).setVisibility(View.GONE);
            itemView.findViewById(R.id.ivSenderIcon).setVisibility(View.GONE);
        }
    }

    public static class SilencedApp {
        public final String packageName;
        public final int silencedWhen;

        public SilencedApp(String packageName, int silencedWhen) {
            this.packageName = packageName;
            this.silencedWhen = silencedWhen;
        }
    }

    public static class Switch {
        private final String title;
        private final boolean checked;

        public Switch(String title, boolean checked) {
            this.title = title;
            this.checked = checked;
        }
    }

    public static class SwitchViewHolder extends RecyclerView.ViewHolder {
        SwitchCompat switch1;
        ImageView ivIcon;

        public SwitchViewHolder(@NonNull View itemView) {
            super(itemView);
            switch1 = itemView.findViewById(R.id.switch1);
            ivIcon = itemView.findViewById(R.id.ivIcon);
        }
    }
}