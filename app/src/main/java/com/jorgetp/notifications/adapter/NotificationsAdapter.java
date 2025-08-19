package com.jorgetp.notifications.adapter;

import static com.jorgetp.notifications.MainActivity.ALWAYS;
import static com.jorgetp.notifications.MainActivity.IMPORTANT_SENDERS_PREFS;
import static com.jorgetp.notifications.MainActivity.IsToday;
import static com.jorgetp.notifications.MainActivity.IsYesterday;
import static com.jorgetp.notifications.MainActivity.SILENCED_APPS_PREFS;
import static com.jorgetp.notifications.MainActivity.ToDate;

import android.app.Dialog;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.drawable.Drawable;
import android.net.Uri;
import android.provider.Settings;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.widget.ImageView;
import android.widget.PopupMenu;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.core.view.MenuCompat;
import androidx.recyclerview.widget.RecyclerView;

import com.jorgetp.notifications.R;

import org.json.JSONObject;

import java.io.FileInputStream;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;

public class NotificationsAdapter extends RecyclerView.Adapter<NotificationsAdapter.ViewHolder> {
    private final Context context;
    private final SharedPreferences silencedAppsPrefs;
    private final SharedPreferences importantSendersPrefs;
    private List<JSONObject> notifications = new ArrayList<>();

    // The constructor now just sets up an empty list
    public NotificationsAdapter(Context context) {
        this.context = context;
        silencedAppsPrefs = context.getSharedPreferences(SILENCED_APPS_PREFS, Context.MODE_PRIVATE);
        importantSendersPrefs = context.getSharedPreferences(IMPORTANT_SENDERS_PREFS, Context.MODE_PRIVATE);
    }

    // Add this new method to update the data
    public void updateData(List<JSONObject> newNotifications) {
        this.notifications = newNotifications;
        notifyDataSetChanged(); // Tell the RecyclerView to refresh
    }

    @Override
    public int getItemCount() {
        return notifications.size();
    }

    @Override
    public long getItemId(int position) {
        return position;
    }

    @NonNull
    @Override
    public NotificationsAdapter.ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(context).inflate(R.layout.item_notification, parent, false);
        return new NotificationsAdapter.ViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull NotificationsAdapter.ViewHolder holder, int i) {
        JSONObject notification = notifications.get(i);

        String packageName = notification.optString("package");
        String title = notification.optString("title");
        String text = notification.optString("text");
        // String bigText = notification.optString("bigText");
        // boolean hasBigText = !bigText.isEmpty() && !text.equals(bigText);
        boolean isSilenced = silencedAppsPrefs.contains(packageName);
        boolean isImportant = importantSendersPrefs.contains(packageName + "/" + title);

        long postTime = notification.optLong("postTime");
        Date date = ToDate(postTime);
        if (date != null) {
            if (IsToday(date)) {
                SimpleDateFormat sdf = new SimpleDateFormat("h:mm a", Locale.getDefault());
                holder.tvTime.setText(sdf.format(date));
            } else if (IsYesterday(date)) {
                SimpleDateFormat sdf = new SimpleDateFormat("h:mm a", Locale.getDefault());
                holder.tvTime.setText(String.format("Yesterday %s", sdf.format(date)));
            } else {
                SimpleDateFormat sdf = new SimpleDateFormat("dd/MMMM/yyyy h:mm a", Locale.getDefault());
                holder.tvTime.setText(sdf.format(date));
            }
        }

        holder.tvApp.setText(packageName);
        holder.tvTitle.setText(title);
        holder.tvText.setText(text);

        // Load app name and icon
        try {
            ApplicationInfo appInfo = context.getPackageManager().getApplicationInfo(packageName, 0);
            CharSequence appName = context.getPackageManager().getApplicationLabel(appInfo);
            holder.tvApp.setText(appName);

            Drawable appIcon = context.getPackageManager().getApplicationIcon(appInfo);
            holder.ivIcon.setImageDrawable(appIcon);
            holder.ivIconSecondary.setVisibility(View.GONE);

            // load icon from internal storage if it exists
            try (FileInputStream fis = context
                    .openFileInput("notification_icon_" + notification.optString("uuid") + ".png")) {
                Bitmap iconBitmap = BitmapFactory.decodeStream(fis);

                holder.ivIcon.setImageBitmap(iconBitmap);
                holder.ivIconSecondary.setVisibility(View.VISIBLE);
                holder.ivIconSecondary.setImageDrawable(appIcon);

            } catch (Exception e) {
                Log.e("NotificationsAdapter", "Icon not found", e);
            }

        } catch (PackageManager.NameNotFoundException e) {
            Log.e("NotificationsAdapter", "App not found", e);
        }

        // when item is clicked, show a menu with several options
        holder.itemView.setOnClickListener(v -> {
            PopupMenu popup = new PopupMenu(context, v);
            popup.getMenuInflater().inflate(R.menu.notification_popup_menu, popup.getMenu());
            MenuCompat.setGroupDividerEnabled(popup.getMenu(), true);
            popup.getMenu().findItem(R.id.silence_app).setVisible(!isSilenced);
            popup.getMenu().findItem(R.id.set_as_important).setVisible(!isImportant);

            popup.setOnMenuItemClickListener(item -> {
                int itemId = item.getItemId();
                if (itemId == R.id.copy_title) {
                    ClipboardManager clipboard = (ClipboardManager) context.getSystemService(Context.CLIPBOARD_SERVICE);
                    ClipData clip = ClipData.newPlainText("Notification title", title);
                    clipboard.setPrimaryClip(clip);
                    Toast.makeText(context, R.string.copied_title, Toast.LENGTH_SHORT).show();
                    return true;

                } else if (itemId == R.id.copy_text) {
                    ClipboardManager clipboard = (ClipboardManager) context.getSystemService(Context.CLIPBOARD_SERVICE);
                    ClipData clip = ClipData.newPlainText("Notification text", text);
                    clipboard.setPrimaryClip(clip);
                    Toast.makeText(context, R.string.copied_text, Toast.LENGTH_SHORT).show();
                    return true;

                } else if (itemId == R.id.pop_out) {
                    try {
                        Dialog dialog = new Dialog(context);
                        dialog.setContentView(R.layout.item_notification);

                        ((ImageView) dialog.findViewById(R.id.ivIcon)).setImageDrawable(holder.ivIcon.getDrawable());
                        ((ImageView) dialog.findViewById(R.id.ivIconSecondary)).setImageDrawable(holder.ivIconSecondary.getDrawable());
                        ((ImageView) dialog.findViewById(R.id.ivIconSecondary)).setVisibility(holder.ivIconSecondary.getVisibility());
                        ((TextView) dialog.findViewById(R.id.tvApp)).setText(holder.tvApp.getText());
                        ((TextView) dialog.findViewById(R.id.tvTime)).setText(holder.tvTime.getText());
                        ((TextView) dialog.findViewById(R.id.tvTitle)).setText(holder.tvTitle.getText());
                        ((TextView) dialog.findViewById(R.id.tvText)).setText(holder.tvText.getText());
                        ((TextView) dialog.findViewById(R.id.tvText)).setMaxLines(Integer.MAX_VALUE);

                        Window window = dialog.getWindow();
                        if (window != null) {
                            window.setLayout(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
                            window.setBackgroundDrawableResource(android.R.color.transparent);
                            window.setDimAmount(0.9f);
                        }
                        dialog.show();

                    } catch (Exception ignored) {
                    }
                    return true;

                } else if (itemId == R.id.go_to_app) {
                    Intent launchIntent = context.getPackageManager().getLaunchIntentForPackage(packageName);
                    if (launchIntent != null) {
                        context.startActivity(launchIntent);
                    } else {
                        Intent settingsIntent = new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS);
                        settingsIntent.setData(Uri.parse("package:" + packageName));
                        context.startActivity(settingsIntent);
                    }
                    return true;

                } else if (itemId == R.id.silence_app) {
                    silencedAppsPrefs.edit().putInt(packageName, ALWAYS).apply();
                    Toast.makeText(context, context.getString(R.string.silenced_always),
                            Toast.LENGTH_SHORT).show();
                    return true;

                } else if (itemId == R.id.set_as_important) {
                    importantSendersPrefs.edit().putString(packageName + "/" + title, notification.optString("uuid")).apply();
                    Toast.makeText(context, context.getString(R.string.set_as_important),
                            Toast.LENGTH_SHORT).show();
                    return true;
                }

                return false;
            });

            popup.show();
        });
    }

    public static class ViewHolder extends RecyclerView.ViewHolder {
        ImageView ivIcon;
        ImageView ivIconSecondary;
        TextView tvApp;
        TextView tvTime;
        TextView tvTitle;
        TextView tvText;

        public ViewHolder(@NonNull View itemView) {
            super(itemView);
            ivIcon = itemView.findViewById(R.id.ivIcon);
            ivIconSecondary = itemView.findViewById(R.id.ivIconSecondary);
            tvApp = itemView.findViewById(R.id.tvApp);
            tvTime = itemView.findViewById(R.id.tvTime);
            tvTitle = itemView.findViewById(R.id.tvTitle);
            tvText = itemView.findViewById(R.id.tvText);
        }
    }
}