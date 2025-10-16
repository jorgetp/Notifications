package com.jorgetp.notifications.adapter;

import static com.jorgetp.notifications.MainActivity.ALWAYS;
import static com.jorgetp.notifications.MainActivity.IMPORTANT_SENDERS_PREFS;
import static com.jorgetp.notifications.MainActivity.NON_BUSINESS;
import static com.jorgetp.notifications.MainActivity.SILENCED_APPS_PREFS;

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

import java.io.FileInputStream;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Map;
import java.util.TreeSet;

public class SettingsAdapter extends NotificationsAdapter {
    public static final String CHANNEL_ID = "com.jorgetp.notifications";

    private final TreeSet<String> editedItems = new TreeSet<>();

    private final ActivityResultLauncher<Intent> nslSettingsLauncher;

    public SettingsAdapter(Context context) {
        super(context);

        nslSettingsLauncher = ((SettingsActivity) context).registerForActivityResult(
                new ActivityResultContracts.StartActivityForResult(), result -> {
                    boolean enabled = ((SettingsActivity) context).isNotificationServiceEnabled();
                    if (enabled && !((Switch) items.get(1)).checked) {
                        NotificationManager notificationManager = (NotificationManager) context.getSystemService(
                                Context.NOTIFICATION_SERVICE);
                        NotificationChannel channel = new NotificationChannel(
                                CHANNEL_ID,
                                context.getString(R.string.app_name),
                                NotificationManager.IMPORTANCE_HIGH);
                        notificationManager.createNotificationChannel(channel);

                        Toast.makeText(context, R.string.nsl_enabled, Toast.LENGTH_SHORT).show();
                    }
                    items.set(1, new Switch(context.getString(R.string.nsl_enabled), enabled));
                    notifyItemChanged(1);

                });

        items = new ArrayList<>(10);

        // General settings
        items.add(context.getString(R.string.general));
        items.add(new Switch(context.getString(R.string.nsl_enabled), ((SettingsActivity) context).isNotificationServiceEnabled()));

        // important senders
        ArrayList<ImportantSender> senders = new ArrayList<>(10);
        SharedPreferences prefs2 = MainActivity.getPrefs(context, IMPORTANT_SENDERS_PREFS);
        for (Map.Entry<String, ?> entry : prefs2.getAll().entrySet()) {
            String key = entry.getKey();
            String value = entry.getValue().toString();
            String[] parts = key.split("/", 2);
            senders.add(new ImportantSender(parts[0], parts[1], value));
        }
        senders.sort(Comparator.comparing(sender -> sender.sender.toLowerCase()));

        items.add(context.getString(R.string.important_senders));
        items.addAll(senders);

        // silenced apps
        ArrayList<SilencedApp> apps = new ArrayList<>(10);
        SharedPreferences prefs1 = MainActivity.getPrefs(context, SILENCED_APPS_PREFS);
        for (Map.Entry<String, ?> entry : prefs1.getAll().entrySet()) {
            String packageName = entry.getKey();
            Integer silencedWhen = (Integer) entry.getValue();
            apps.add(new SilencedApp(packageName, silencedWhen));
        }
        apps.sort(Comparator
                .comparing(app -> MainActivity.getAppInfo(context, app.packageName).first.toString().toLowerCase()));

        items.add(context.getString(R.string.silenced_apps));
        items.addAll(apps);
    }

    public TreeSet<String> getEditedItems() {
        return editedItems;
    }

    @NonNull
    @Override
    public RecyclerView.ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        if (viewType == 0) {
            View view = LayoutInflater.from(context).inflate(R.layout.header, parent, false);
            return new NotificationsAdapter.HeaderViewHolder(view);
        } else if (viewType == 1) {
            View view = LayoutInflater.from(context).inflate(R.layout.item, parent, false);
            return new SilencedAppViewHolder(view);
        } else if (viewType == 2) {
            View view = LayoutInflater.from(context).inflate(R.layout.item, parent, false);
            return new ImportantSenderViewHolder(view);
        } else /* if (viewType == 3) */ {
            View view = LayoutInflater.from(context).inflate(R.layout.item_switch, parent, false);
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

            holder.itemView.setBackgroundResource(getBackground(i));
            holder.divider.setVisibility(isDividerVisible(i) ? View.VISIBLE : View.GONE);

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
            holder.divider.setVisibility(isDividerVisible(i) ? View.VISIBLE : View.GONE);

            // load app name and icon
            Pair<CharSequence, Drawable> appInfo = MainActivity.getAppInfo(context, app.packageName);
            holder.tvApp.setText(appInfo.first);
            if (appInfo.second != null)
                holder.ivIcon.setImageDrawable(appInfo.second);
            else
                holder.ivIcon.setImageResource(android.R.drawable.sym_def_app_icon);

            holder.tvSilencedWhen.setText(app.silencedWhen == ALWAYS ? context.getString(R.string.silenced_always)
                    : context.getString(R.string.silenced_non_business));

            holder.itemView.setOnClickListener(v -> {
                PopupMenu popup = new PopupMenu(context, v);
                popup.getMenuInflater().inflate(R.menu.menu_app_popup, popup.getMenu());
                SharedPreferences prefs = MainActivity.getPrefs(context, SILENCED_APPS_PREFS);

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
            holder.divider.setVisibility(isDividerVisible(i) ? View.VISIBLE : View.GONE);

            // load app name and icon
            Pair<CharSequence, Drawable> appInfo = MainActivity.getAppInfo(context, sender.packageName);
            holder.tvSender.setText(!sender.sender.isEmpty() ? sender.sender : context.getString(R.string.no_title));

            // app icon
            if (appInfo.second != null)
                holder.ivAppIcon.setImageDrawable(appInfo.second);
            else
                holder.ivAppIcon.setImageResource(android.R.drawable.sym_def_app_icon);

            // sender icon
            holder.ivSenderIcon.setVisibility(View.GONE);
            try (FileInputStream fis = context
                    .openFileInput("notification_icon_" + sender.uuid + ".png")) {
                Bitmap iconBitmap = BitmapFactory.decodeStream(fis);
                holder.ivSenderIcon.setImageBitmap(iconBitmap);
                holder.ivSenderIcon.setVisibility(View.VISIBLE);
            } catch (Exception e) {
                // Log.e("NotificationsAdapter", "Icon not found", e);
            }

            holder.itemView.setOnClickListener(v -> new AlertDialog.Builder(context)
                    .setMessage(R.string.unset_as_important_confirmation)
                    .setPositiveButton(android.R.string.yes, (dialog, id) -> {
                        int position = holder.getBindingAdapterPosition();
                        editedItems.add(sender.packageName + "/" + sender.sender);
                        MainActivity.getPrefs(context, IMPORTANT_SENDERS_PREFS)
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
        View divider;

        public ImportantSenderViewHolder(@NonNull View itemView) {
            super(itemView);
            ivAppIcon = itemView.findViewById(R.id.ivAppIcon);
            ivSenderIcon = itemView.findViewById(R.id.ivSenderIcon);
            tvSender = itemView.findViewById(R.id.tvTitle);
            divider = itemView.findViewById(R.id.divider);

            itemView.findViewById(R.id.tvTime).setVisibility(View.GONE);
            ((TextView) itemView.findViewById(R.id.tvText)).setText(R.string.tap_to_unset_as_important);
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

    public static class SilencedAppViewHolder extends RecyclerView.ViewHolder {
        ImageView ivIcon;
        TextView tvApp;
        TextView tvSilencedWhen;
        View divider;

        public SilencedAppViewHolder(@NonNull View itemView) {
            super(itemView);
            ivIcon = itemView.findViewById(R.id.ivAppIcon);
            tvApp = itemView.findViewById(R.id.tvTitle);
            tvSilencedWhen = itemView.findViewById(R.id.tvText);
            divider = itemView.findViewById(R.id.divider);

            itemView.findViewById(R.id.tvTime).setVisibility(View.GONE);
            itemView.findViewById(R.id.ivSenderIcon).setVisibility(View.GONE);
        }
    }

    public static class SilencedApp {
        private final String packageName;
        private final int silencedWhen;

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
        View divider;

        public SwitchViewHolder(@NonNull View itemView) {
            super(itemView);
            switch1 = itemView.findViewById(R.id.switch1);
            ivIcon = itemView.findViewById(R.id.ivIcon);
            divider = itemView.findViewById(R.id.divider);
        }
    }
}
