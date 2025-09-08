package com.jorgetp.notifications.adapter;

import static com.jorgetp.notifications.MainActivity.ALWAYS;
import static com.jorgetp.notifications.MainActivity.IMPORTANT_SENDERS_PREFS;
import static com.jorgetp.notifications.MainActivity.SILENCED_APPS_PREFS;
import static com.jorgetp.notifications.MainActivity.isToday;
import static com.jorgetp.notifications.MainActivity.isYesterday;
import static com.jorgetp.notifications.MainActivity.toDate;

import android.content.ClipData;
import android.content.ClipboardManager;
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
import android.widget.PopupMenu;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.core.view.MenuCompat;
import androidx.recyclerview.widget.RecyclerView;

import com.jorgetp.notifications.MainActivity;
import com.jorgetp.notifications.R;

import org.json.JSONObject;

import java.io.FileInputStream;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.Locale;

public class NotificationsAdapter extends RecyclerView.Adapter<NotificationsAdapter.ViewHolder> {
    private final Context context;
    private ArrayList<JSONObject> notifications = new ArrayList<>();
    private boolean[] fullText;

    public NotificationsAdapter(Context context) {
        this.context = context;
    }

    public void updateData(ArrayList<JSONObject> newNotifications) {
        this.notifications = newNotifications;
        fullText = new boolean[newNotifications.size()];
        notifyDataSetChanged();
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
        View view = LayoutInflater.from(context).inflate(R.layout.item, parent, false);
        return new NotificationsAdapter.ViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull NotificationsAdapter.ViewHolder holder, int i) {
        JSONObject notification = notifications.get(i);

        SharedPreferences silencedAppsPrefs = MainActivity.getPrefs(context, SILENCED_APPS_PREFS);
        SharedPreferences importantSendersPrefs = MainActivity.getPrefs(context, IMPORTANT_SENDERS_PREFS);

        String packageName = notification.optString("package");
        String title = notification.optString("title");
        String text = notification.optString("text");
        // String bigText = notification.optString("bigText");
        // boolean hasBigText = !bigText.isEmpty() && !text.equals(bigText);
        boolean isSilencedApp = silencedAppsPrefs.contains(packageName);
        boolean isImportant = importantSendersPrefs.contains(packageName + "/" + title);

        long postTime = notification.optLong("postTime");
        Date date = toDate(postTime);
        if (date != null) {
            if (isToday(date)) {
                SimpleDateFormat sdf = new SimpleDateFormat("h:mm a", Locale.getDefault());
                holder.tvTime.setText(sdf.format(date));
            } else if (isYesterday(date)) {
                SimpleDateFormat sdf = new SimpleDateFormat("h:mm a", Locale.getDefault());
                holder.tvTime.setText(context.getString(R.string.yesterday, sdf.format(date)));
            } else {
                SimpleDateFormat sdf = new SimpleDateFormat("dd/MMMM/yyyy h:mm a", Locale.getDefault());
                holder.tvTime.setText(sdf.format(date));
            }
        }

        Pair<CharSequence, Drawable> appInfo = MainActivity.getAppInfo(context, packageName);

        holder.tvApp.setText(appInfo.first);
        holder.tvTitle.setText(title);
        holder.tvText.setText(text);
        holder.tvText.setMaxLines(fullText[holder.getBindingAdapterPosition()] ? Integer.MAX_VALUE : 2);

        // app icon
        if (appInfo.second != null)
            holder.ivAppIcon.setImageDrawable(appInfo.second);
        else
            holder.ivAppIcon.setImageResource(android.R.drawable.sym_def_app_icon);

        // sender icon
        // holder.ivSenderIcon.setImageBitmap(MainActivity.createIconBitmap(packageName, title));
        holder.ivSenderIcon.setVisibility(View.GONE);
        try (FileInputStream fis = context
                .openFileInput("notification_icon_" + notification.optString("uuid") + ".png")) {
            Bitmap iconBitmap = BitmapFactory.decodeStream(fis);
            holder.ivSenderIcon.setImageBitmap(iconBitmap);
            holder.ivSenderIcon.setVisibility(View.VISIBLE);
        } catch (Exception e) {
            Log.e("NotificationsAdapter", "Icon not found", e);
        }

        // when item is clicked, show a menu with several options
        holder.itemView.setOnClickListener(v -> {
            PopupMenu popup = new PopupMenu(context, v);
            popup.getMenuInflater().inflate(R.menu.menu_notification_popup, popup.getMenu());
            MenuCompat.setGroupDividerEnabled(popup.getMenu(), true);
            popup.getMenu().findItem(R.id.silence_app).setVisible(!isSilencedApp);
            popup.getMenu().findItem(R.id.set_as_important).setVisible(!isImportant);

            popup.setOnMenuItemClickListener(item -> {
                int itemId = item.getItemId();
                int position = holder.getBindingAdapterPosition();

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

                } else if (itemId == R.id.view_full_text) {
                    fullText[position] = true;
                    notifyItemChanged(position);
                    return true;

                } else if (itemId == R.id.silence_app) {
                    silencedAppsPrefs.edit().putInt(packageName, ALWAYS).apply();
                    Toast.makeText(context, context.getString(R.string.silenced_always),
                            Toast.LENGTH_SHORT).show();
                    return true;

                } else if (itemId == R.id.set_as_important) {
                    importantSendersPrefs.edit().putString(packageName + "/" + title,
                            notification.optString("uuid")).apply();
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
        ImageView ivAppIcon;
        ImageView ivSenderIcon;
        TextView tvApp;
        TextView tvTime;
        TextView tvTitle;
        TextView tvText;

        public ViewHolder(@NonNull View itemView) {
            super(itemView);
            ivAppIcon = itemView.findViewById(R.id.ivAppIcon);
            ivSenderIcon = itemView.findViewById(R.id.ivSenderIcon);
            tvApp = itemView.findViewById(R.id.tvApp);
            tvTime = itemView.findViewById(R.id.tvTime);
            tvTitle = itemView.findViewById(R.id.tvTitle);
            tvText = itemView.findViewById(R.id.tvText);
        }
    }
}