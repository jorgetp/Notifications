package com.jorgetp.notifications.adapter;

import static com.jorgetp.notifications.MainActivity.ALWAYS;
import static com.jorgetp.notifications.MainActivity.IMPORTANT_SENDERS_PREFS;
import static com.jorgetp.notifications.MainActivity.SILENCED_APPS_PREFS;

import android.app.Notification;
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
import com.jorgetp.notifications.dao.StoredNotification;

import java.io.FileInputStream;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.Locale;

public class NotificationsAdapter extends RecyclerView.Adapter<RecyclerView.ViewHolder> {
    protected final Context context;
    protected ArrayList<Object> items = new ArrayList<>();

    public NotificationsAdapter(Context context) {
        this.context = context;
    }

    public static Bitmap createIconBitmap(String packageName, String sender) {
        try {
            int lastIndex = sender.lastIndexOf(":");
            String actualSender = (lastIndex == -1 || lastIndex == sender.length() - 1) ?
                    sender : sender.substring(lastIndex + 1).strip();

            // continue only if first char is a letter
            if (Character.isLetter(actualSender.charAt(0))) {
                // create colored circle
                int hash = (packageName + actualSender).hashCode();
                int r = (hash >> 16) & 0xFF;
                int g = (hash >> 8) & 0xFF;
                int b = hash & 0xFF;
                int color = 0xFF000000 | (r << 16) | (g << 8) | b;

                Bitmap bitmap = Bitmap.createBitmap(100, 100, Bitmap.Config.ARGB_8888);
                android.graphics.Canvas canvas = new android.graphics.Canvas(bitmap);
                android.graphics.Paint paint = new android.graphics.Paint();
                paint.setColor(color);
                paint.setStyle(android.graphics.Paint.Style.FILL);
                canvas.drawCircle(50, 50, 50, paint);
                paint.setColor(0xFF000000);
                paint.setTextSize(60);
                paint.setTextAlign(android.graphics.Paint.Align.CENTER);

                // write first letter in circle
                String firstLetter = actualSender.substring(0, 1).toUpperCase();
                canvas.drawText(firstLetter, 50, 70, paint);

                return bitmap;
            }

        } catch (Exception e) {
            Log.e("MainActivity", "Error creating icon bitmap", e);
        }

        return null;
    }

    public int getBackground(int position) {
        boolean afterHeader = isAfterHeader(position);
        boolean beforeHeader = isBeforeHeader(position);
        if (afterHeader && beforeHeader) return R.drawable.rounded_all;
        if (afterHeader) return R.drawable.rounded_top;
        if (beforeHeader) return R.drawable.rounded_bottom;
        return R.drawable.rounded_none;
    }

    public boolean isAfterHeader(int position) {
        return position == 0 || getItem(position - 1) instanceof String;
    }

    public boolean isBeforeHeader(int position) {
        return position == getItemCount() - 1 || getItem(position + 1) instanceof String;
    }

    public boolean isDividerVisible(int position) {
        return !isBeforeHeader(position);
    }

    @Override
    public int getItemCount() {
        return items.size();
    }

    @Override
    public long getItemId(int position) {
        return position;
    }

    public Object getItem(int position) {
        return items.get(position);
    }

    public void updateData(ArrayList<Object> items) {
        this.items = items;
        notifyDataSetChanged();
    }

    @Override
    public int getItemViewType(int position) {
        return getItem(position) instanceof String ? 0 : 1;
    }

    @NonNull
    @Override
    public RecyclerView.ViewHolder onCreateViewHolder(ViewGroup parent, int viewType) {
        if (viewType == 0) { // header
            View view = LayoutInflater.from(parent.getContext())
                    .inflate(R.layout.header, parent, false);
            return new HeaderViewHolder(view);
        } else { // item
            View view = LayoutInflater.from(parent.getContext())
                    .inflate(R.layout.item, parent, false);
            return new ItemViewHolder(view);
        }
    }

    @Override
    public void onBindViewHolder(@NonNull RecyclerView.ViewHolder holderGeneric, int i) {
        if (holderGeneric instanceof HeaderViewHolder) {
            HeaderViewHolder holder = (HeaderViewHolder) holderGeneric;
            holder.tvHeader.setText((String) getItem(i));

        } else {
            StoredNotification notification = (StoredNotification) getItem(i);
            ItemViewHolder holder = (ItemViewHolder) holderGeneric;

            holder.itemView.setBackgroundResource(getBackground(i));
            holder.divider.setVisibility(isDividerVisible(i) ? View.VISIBLE : View.GONE);

            SharedPreferences silencedAppsPrefs = MainActivity.getPrefs(context, SILENCED_APPS_PREFS);
            SharedPreferences importantSendersPrefs = MainActivity.getPrefs(context, IMPORTANT_SENDERS_PREFS);

            String packageName = notification.packageName;
            String title = notification.title;
            String text = notification.text;
            boolean isSilencedApp = silencedAppsPrefs.contains(packageName);
            boolean isImportant = importantSendersPrefs.contains(packageName + "/" + title);

            long postTime = notification.postTime;
            Date date = MainActivity.toDate(postTime);
            if (date != null) {
                SimpleDateFormat sdf = new SimpleDateFormat("h:mm a", Locale.getDefault());
                holder.tvTime.setText(sdf.format(date));
            }

            Pair<CharSequence, Drawable> appInfo = MainActivity.getAppInfo(context, packageName);
            int position = holder.getBindingAdapterPosition();

            holder.tvTitle.setText(!title.isEmpty() ? title : context.getString(R.string.no_title));
            holder.tvText.setText(text);

            // app icon
            if (appInfo.second != null)
                holder.ivAppIcon.setImageDrawable(appInfo.second);
            else
                holder.ivAppIcon.setImageResource(android.R.drawable.sym_def_app_icon);

            // sender icon
            holder.ivSenderIcon.setVisibility(View.GONE);
            String category = notification.category;
            if (Notification.CATEGORY_MESSAGE.equals(category)
                /*|| Notification.CATEGORY_EMAIL.equals(category)
                || Notification.CATEGORY_SOCIAL.equals(category)
                || Notification.CATEGORY_CALL.equals(category)
                || Notification.CATEGORY_MISSED_CALL.equals(category)*/) {
                Bitmap bm = createIconBitmap(packageName, title);
                if (bm != null)
                    holder.ivSenderIcon.setImageBitmap(bm);
                holder.ivSenderIcon.setVisibility(View.VISIBLE);
            }

            try (FileInputStream fis = context
                    .openFileInput("notification_icon_" + notification.uuid + ".png")) {
                Bitmap iconBitmap = BitmapFactory.decodeStream(fis);
                holder.ivSenderIcon.setImageBitmap(iconBitmap);
                holder.ivSenderIcon.setVisibility(View.VISIBLE);
            } catch (Exception e) {
                // Log.e("NotificationsAdapter", "Icon not found", e);
            }

            // when item is clicked, show a menu with several options
            holder.itemView.setOnClickListener(v -> {
                PopupMenu popup = new PopupMenu(context, v);
                popup.getMenuInflater().inflate(R.menu.menu_notification_popup, popup.getMenu());
                MenuCompat.setGroupDividerEnabled(popup.getMenu(), true);
                popup.getMenu().findItem(R.id.silence_app).setEnabled(!isSilencedApp);
                popup.getMenu().findItem(R.id.set_as_important).setEnabled(!isImportant);

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

                    } else if (itemId == R.id.silence_app) {
                        silencedAppsPrefs.edit().putInt(packageName, ALWAYS).apply();
                        notifyItemChanged(position);
                        Toast.makeText(context, context.getString(R.string.silenced_always),
                                Toast.LENGTH_SHORT).show();
                        return true;

                    } else if (itemId == R.id.set_as_important) {
                        importantSendersPrefs.edit().putString(packageName + "/" + title,
                                notification.uuid).apply();
                        notifyItemChanged(position);
                        Toast.makeText(context, context.getString(R.string.set_as_important),
                                Toast.LENGTH_SHORT).show();
                        return true;
                    }

                    return false;
                });

                popup.show();
            });
        }
    }

    public static class ItemViewHolder extends RecyclerView.ViewHolder {
        ImageView ivAppIcon;
        ImageView ivSenderIcon;
        TextView tvTime;
        TextView tvTitle;
        TextView tvText;
        View divider;

        public ItemViewHolder(@NonNull View itemView) {
            super(itemView);
            ivAppIcon = itemView.findViewById(R.id.ivAppIcon);
            ivSenderIcon = itemView.findViewById(R.id.ivSenderIcon);
            tvTime = itemView.findViewById(R.id.tvTime);
            tvTitle = itemView.findViewById(R.id.tvTitle);
            tvText = itemView.findViewById(R.id.tvText);
            divider = itemView.findViewById(R.id.divider);
        }
    }

    public static class HeaderViewHolder extends RecyclerView.ViewHolder {
        TextView tvHeader;

        public HeaderViewHolder(@NonNull View itemView) {
            super(itemView);
            tvHeader = itemView.findViewById(R.id.tvHeader);
        }
    }
}