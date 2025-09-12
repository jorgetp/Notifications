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

import org.json.JSONException;
import org.json.JSONObject;

import java.io.FileInputStream;
import java.text.SimpleDateFormat;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.Locale;

public class NotificationsAdapter extends RecyclerView.Adapter<RecyclerView.ViewHolder> {
    private final Context context;
    private final ArrayList<JSONObject> items = new ArrayList<>();
    private boolean[] isExpanded;

    public NotificationsAdapter(Context context) {
        this.context = context;
    }

    public static boolean isSameDay(Date date1, Date date2) {
        Calendar cal1 = Calendar.getInstance();
        cal1.setTime(date1);
        Calendar cal2 = Calendar.getInstance();
        cal2.setTime(date2);
        return cal1.get(Calendar.YEAR) == cal2.get(Calendar.YEAR) &&
                cal1.get(Calendar.DAY_OF_YEAR) == cal2.get(Calendar.DAY_OF_YEAR);
    }

    public static Date toDate(long timestamp) {
        try {
            return new Date(timestamp);
        } catch (NumberFormatException e) {
            return null;
        }
    }

    public static boolean isToday(Date date) {
        LocalDate givenDate = date.toInstant().atZone(ZoneId.systemDefault()).toLocalDate();
        LocalDate today = LocalDate.now();
        return givenDate.equals(today);
    }

    public static boolean isYesterday(Date date) {
        LocalDate givenDate = date.toInstant().atZone(ZoneId.systemDefault()).toLocalDate();
        LocalDate yesterday = LocalDate.now().minusDays(1);
        return givenDate.equals(yesterday);
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

    public void updateData(ArrayList<JSONObject> newNotifications) {
        items.clear();
        if (!newNotifications.isEmpty()) {
            for (int i = 0; i < newNotifications.size(); i++) {
                boolean addHeader = true;
                Date currentDate = toDate(newNotifications.get(i).optLong("postTime"));
                if (i > 0) {
                    Date previousDate = toDate(newNotifications.get(i - 1).optLong("postTime"));
                    addHeader = !isSameDay(previousDate, currentDate);
                }
                if (addHeader) {
                    try {
                        JSONObject header = new JSONObject();
                        header.put("header", dateToHeader(currentDate));
                        items.add(header);
                    } catch (JSONException e) {
                        Log.e("NotificationsAdapter", "Error creating header", e);
                    }
                }
                items.add(newNotifications.get(i));
            }
        }
        isExpanded = new boolean[items.size()];
        notifyDataSetChanged();
    }

    @Override
    public int getItemCount() {
        return items.size();
    }

    @Override
    public long getItemId(int position) {
        return position;
    }

    @Override
    public int getItemViewType(int position) {
        return items.get(position).optString("header", "").isEmpty() ? 1 : 0;
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

    public String dateToHeader(Date date) {
        if (date != null) {
            if (isToday(date)) {
                return context.getString(R.string.today);
            } else if (isYesterday(date)) {
                return context.getString(R.string.yesterday);
            } else {
                SimpleDateFormat sdf = new SimpleDateFormat("EEE, dd MMM yyyy", Locale.getDefault());
                return sdf.format(date);
            }
        }
        return "";
    }

    @Override
    public void onBindViewHolder(@NonNull RecyclerView.ViewHolder holderGeneric, int i) {
        if (holderGeneric instanceof HeaderViewHolder) {
            HeaderViewHolder holder = (HeaderViewHolder) holderGeneric;
            JSONObject header = items.get(i);
            holder.tvDay.setText(header.optString("header"));

        } else {
            JSONObject notification = items.get(i);
            ItemViewHolder holder = (ItemViewHolder) holderGeneric;

            SharedPreferences silencedAppsPrefs = MainActivity.getPrefs(context, SILENCED_APPS_PREFS);
            SharedPreferences importantSendersPrefs = MainActivity.getPrefs(context, IMPORTANT_SENDERS_PREFS);

            String packageName = notification.optString("package");
            String title = notification.optString("title");
            String text = notification.optString("text");
            boolean isSilencedApp = silencedAppsPrefs.contains(packageName);
            boolean isImportant = importantSendersPrefs.contains(packageName + "/" + title);

            long postTime = notification.optLong("postTime");
            Date date = toDate(postTime);
            if (date != null) {
                SimpleDateFormat sdf = new SimpleDateFormat("h:mm a", Locale.getDefault());
                holder.tvTime.setText(sdf.format(date));
            }

            Pair<CharSequence, Drawable> appInfo = MainActivity.getAppInfo(context, packageName);
            int position = holder.getBindingAdapterPosition();

            holder.tvTitle.setText(!title.isEmpty() ? title : context.getString(R.string.no_title));
            holder.tvText.setText(text);
            holder.tvText.setMaxLines(isExpanded[position] ? Integer.MAX_VALUE : 3);

            // app icon
            if (appInfo.second != null)
                holder.ivAppIcon.setImageDrawable(appInfo.second);
            else
                holder.ivAppIcon.setImageResource(android.R.drawable.sym_def_app_icon);

            // sender icon
            holder.ivSenderIcon.setVisibility(View.GONE);
            String category = notification.optString("category");
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

                    } else if (itemId == R.id.expand) {
                        isExpanded[position] = true;
                        notifyItemChanged(position);
                        return true;

                    } else if (itemId == R.id.silence_app) {
                        silencedAppsPrefs.edit().putInt(packageName, ALWAYS).apply();
                        notifyItemChanged(position);
                        Toast.makeText(context, context.getString(R.string.silenced_always),
                                Toast.LENGTH_SHORT).show();
                        return true;

                    } else if (itemId == R.id.set_as_important) {
                        importantSendersPrefs.edit().putString(packageName + "/" + title,
                                notification.optString("uuid")).apply();
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

        public ItemViewHolder(@NonNull View itemView) {
            super(itemView);
            ivAppIcon = itemView.findViewById(R.id.ivAppIcon);
            ivSenderIcon = itemView.findViewById(R.id.ivSenderIcon);
            tvTime = itemView.findViewById(R.id.tvTime);
            tvTitle = itemView.findViewById(R.id.tvTitle);
            tvText = itemView.findViewById(R.id.tvText);
        }
    }

    public static class HeaderViewHolder extends RecyclerView.ViewHolder {
        TextView tvDay;

        public HeaderViewHolder(@NonNull View itemView) {
            super(itemView);
            tvDay = itemView.findViewById(R.id.tvDay);
        }
    }
}