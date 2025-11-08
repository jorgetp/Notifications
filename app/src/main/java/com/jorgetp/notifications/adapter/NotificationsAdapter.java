package com.jorgetp.notifications.adapter;

import static com.jorgetp.notifications.MainActivity.ALWAYS;
import static com.jorgetp.notifications.MainActivity.IMPORTANT_SENDERS_PREFS;
import static com.jorgetp.notifications.MainActivity.SILENCED_APPS_PREFS;

import android.app.Activity;
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
import android.util.TypedValue;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.PopupMenu;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.core.view.MenuCompat;
import androidx.recyclerview.widget.RecyclerView;

import com.jorgetp.notifications.MainActivity;
import com.jorgetp.notifications.R;
import com.jorgetp.notifications.dao.DbProvider;
import com.jorgetp.notifications.dao.IconDao;
import com.jorgetp.notifications.dao.NotificationDao;
import com.jorgetp.notifications.dao.StoredIcon;
import com.jorgetp.notifications.dao.StoredNotification;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.Locale;
import java.util.concurrent.Executors;

public class NotificationsAdapter extends RecyclerView.Adapter<RecyclerView.ViewHolder> {
    private static final String[] CATEGORIES_FOR_SENDER_ICON = {
            Notification.CATEGORY_MESSAGE,
            //Notification.CATEGORY_EMAIL,
    };
    private static final String[] PACKAGES_FOR_SENDER_ICON = {
            // Add package names here if needed
    };
    protected final Activity activity;
    protected ArrayList<Object> items;

    public NotificationsAdapter(Activity activity) {
        this.activity = activity;
    }


    public static Bitmap createIconBitmap(String packageName, String sender) {
        try {
            int lastIndex = sender.lastIndexOf(":");
            String actualSender = (lastIndex == -1 || lastIndex == sender.length() - 1) ? sender
                    : sender.substring(lastIndex + 1).strip();

            // Create more homogeneous light colors
            int hash = (/*packageName +*/ actualSender).hashCode();

            // Use HSV color space for better color distribution
            float hue = (Math.abs(hash) % 360); // 0-359 degrees
            float saturation = 0.3f + (Math.abs(hash >> 8) % 40) / 100.0f; // 0.3-0.7 (soft colors)
            float value = 0.85f + (Math.abs(hash >> 16) % 15) / 100.0f; // 0.85-1.0 (light colors)

            int color = android.graphics.Color.HSVToColor(new float[]{hue, saturation, value});

            Bitmap bitmap = Bitmap.createBitmap(100, 100, Bitmap.Config.ARGB_8888);
            android.graphics.Canvas canvas = new android.graphics.Canvas(bitmap);
            android.graphics.Paint paint = new android.graphics.Paint();
            paint.setAntiAlias(true); // Smooth edges
            paint.setColor(color);
            paint.setStyle(android.graphics.Paint.Style.FILL);
            canvas.drawCircle(50, 50, 50, paint);

            // Text color - use dark color for better contrast on light backgrounds
            paint.setColor(0xFF333333); // Dark gray instead of pure black
            paint.setTextSize(60);
            paint.setTextAlign(android.graphics.Paint.Align.CENTER);
            //paint.setTypeface(android.graphics.Typeface.DEFAULT_BOLD); // Bold for better readability

            // Write first letter in circle
            if (Character.isLetter(actualSender.charAt(0))) {
                String firstLetter = actualSender.substring(0, 1).toUpperCase();
                canvas.drawText(firstLetter, 50, 70, paint);
            }

            return bitmap;

        } catch (Exception e) {
            Log.e("MainActivity", "Error creating icon bitmap", e);
        }

        return null;
    }

    // Helper method to convert byte array back to Bitmap
    private Bitmap byteArrayToBitmap(byte[] bytes) {
        if (bytes == null || bytes.length == 0) return null;
        return BitmapFactory.decodeByteArray(bytes, 0, bytes.length);
    }

    // Helper method to check if array contains a value
    private boolean arrayContains(String[] array, String value) {
        if (array == null || value == null) return false;
        for (String item : array) {
            if (value.equals(item)) {
                return true;
            }
        }
        return false;
    }

    public void setItems(ArrayList<Object> items) {
        this.items = items;
    }

    public int getBackground(int position) {
        boolean afterHeader = isAfterHeader(position);
        boolean beforeHeader = isBeforeHeader(position);
        if (afterHeader && beforeHeader)
            return R.drawable.rounded_all;
        if (afterHeader)
            return R.drawable.rounded_top;
        if (beforeHeader)
            return R.drawable.rounded_bottom;
        return R.drawable.rounded_none;
    }

    public boolean isAfterHeader(int position) {
        return position == 0 || getItem(position - 1) instanceof String;
    }

    public boolean isBeforeHeader(int position) {
        return position == getItemCount() - 1 || getItem(position + 1) instanceof String;
    }

    @Override
    public int getItemCount() {
        return items == null ? 0 : items.size();
    }

    @Override
    public long getItemId(int position) {
        return position;
    }

    public Object getItem(int position) {
        return items.get(position);
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

            // Limit tvTitle width
            int maxWidth = 170;
            if (notification.pinned)
                maxWidth = 150;
            holder.tvTitle.setMaxWidth((int) TypedValue.applyDimension(
                    TypedValue.COMPLEX_UNIT_DIP, maxWidth,
                    holder.itemView.getResources().getDisplayMetrics()));

            SharedPreferences silencedAppsPrefs = MainActivity.getPrefs(activity, SILENCED_APPS_PREFS);
            SharedPreferences importantSendersPrefs = MainActivity.getPrefs(activity, IMPORTANT_SENDERS_PREFS);

            String packageName = notification.packageName;
            String title = notification.title;
            String text = notification.text;
            boolean isSilencedApp = silencedAppsPrefs.contains(packageName);
            boolean isImportant = importantSendersPrefs.contains(packageName + "/" + title);

            long postTime = notification.postTime;
            Date date = MainActivity.toDate(postTime);
            if (date != null) {
                SimpleDateFormat sdf = new SimpleDateFormat("h:mm a", Locale.getDefault());
                if (notification.pinned) {
                    sdf = new SimpleDateFormat("d MMM, h:mm a", Locale.getDefault());
                }
                holder.tvTime.setText(sdf.format(date));
            }

            Pair<CharSequence, Drawable> appInfo = MainActivity.getAppInfo(activity, packageName);
            int position = holder.getBindingAdapterPosition();

            holder.tvTitle.setText(!title.isEmpty() ? title : activity.getString(R.string.no_title));
            holder.tvText.setText(text);

            // App icon
            if (appInfo.second != null)
                holder.ivAppIcon.setImageDrawable(appInfo.second);
            else
                holder.ivAppIcon.setImageResource(android.R.drawable.sym_def_app_icon);

            // Load icon from separate icons table
            holder.ivSenderIcon.setVisibility(View.GONE);
            Executors.newSingleThreadExecutor().execute(() -> {
                try {
                    IconDao iconsTable = DbProvider.get(activity).notificationIconDao();
                    StoredIcon icon = iconsTable.getIcon(packageName, title);

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
                        // Fallback: show sender icon for selected categories or apps
                        String category = notification.category;
                        if (arrayContains(CATEGORIES_FOR_SENDER_ICON, category)
                                || arrayContains(PACKAGES_FOR_SENDER_ICON, packageName)) {
                            Bitmap bm = createIconBitmap(packageName, title);
                            if (bm != null) {
                                activity.runOnUiThread(() -> {
                                    holder.ivSenderIcon.setImageBitmap(bm);
                                    holder.ivSenderIcon.setVisibility(View.VISIBLE);
                                });
                            }
                        }
                    }
                } catch (Exception e) {
                    // Log.e("NotificationsAdapter", "Error loading icon", e);
                }
            });

            // when item is clicked, show a menu with several options
            holder.itemView.setOnClickListener(v -> {
                /*PopupMenu popup = new PopupMenu(activity, v);
                popup.getMenuInflater().inflate(R.menu.menu_notification_popup, popup.getMenu());
                MenuCompat.setGroupDividerEnabled(popup.getMenu(), true);

                if (popup.getMenu() instanceof MenuBuilder) {
                    MenuBuilder m = (MenuBuilder) popup.getMenu();
                    m.setOptionalIconsVisible(true);
                }*/
                PopupMenu popup = createPopupMenu(v);

                popup.getMenu().findItem(R.id.silence_app).setEnabled(!isSilencedApp);
                popup.getMenu().findItem(R.id.set_as_important).setEnabled(!isImportant);
                popup.getMenu().findItem(R.id.pin).setVisible(!notification.pinned);
                popup.getMenu().findItem(R.id.unpin).setVisible(notification.pinned);

                popup.setOnMenuItemClickListener(item -> {
                    int itemId = item.getItemId();
                    if (itemId == R.id.copy_title) {
                        ClipboardManager clipboard = (ClipboardManager) activity
                                .getSystemService(Context.CLIPBOARD_SERVICE);
                        ClipData clip = ClipData.newPlainText("Notification title", title);
                        clipboard.setPrimaryClip(clip);
                        Toast.makeText(activity, R.string.copied_title, Toast.LENGTH_SHORT).show();
                        return true;

                    } else if (itemId == R.id.copy_text) {
                        ClipboardManager clipboard = (ClipboardManager) activity
                                .getSystemService(Context.CLIPBOARD_SERVICE);
                        ClipData clip = ClipData.newPlainText("Notification text", text);
                        clipboard.setPrimaryClip(clip);
                        Toast.makeText(activity, R.string.copied_text, Toast.LENGTH_SHORT).show();
                        return true;

                    } else if (itemId == R.id.delete) {
                        new AlertDialog.Builder(activity)
                                .setMessage(R.string.delete_confirmation)
                                .setPositiveButton(android.R.string.yes, (dialog, id) -> {
                                    Executors.newSingleThreadExecutor().execute(() -> {
                                        NotificationDao notificationDao = DbProvider.get(activity).notificationDao();
                                        notificationDao.delete(notification.uuid);
                                        activity.runOnUiThread(() -> ((MainActivity) activity).getNotificationsAndRefreshUI());
                                    });
                                })
                                .setNegativeButton(android.R.string.cancel, null)
                                .create()
                                .show();
                        return true;

                    } else if (itemId == R.id.pin || itemId == R.id.unpin) {
                        Executors.newSingleThreadExecutor().execute(() -> {
                            NotificationDao notificationDao = DbProvider.get(activity).notificationDao();
                            StoredNotification sn = notificationDao.get(notification.uuid);
                            notificationDao.setPinned(notification.uuid, !sn.pinned);
                            activity.runOnUiThread(() -> ((MainActivity) activity).getNotificationsAndRefreshUI());
                        });
                        return true;

                    } else if (itemId == R.id.silence_app) {
                        silencedAppsPrefs.edit().putInt(packageName, ALWAYS).apply();
                        notifyItemChanged(position);
                        Toast.makeText(activity, activity.getString(R.string.silenced_always),
                                Toast.LENGTH_SHORT).show();
                        return true;

                    } else if (itemId == R.id.set_as_important) {
                        importantSendersPrefs.edit().putString(packageName + "/" + title,
                                notification.uuid).apply();
                        notifyItemChanged(position);
                        Toast.makeText(activity, activity.getString(R.string.set_as_important),
                                Toast.LENGTH_SHORT).show();
                        return true;
                    }

                    return false;
                });

                popup.show();
            });
        }
    }

    protected PopupMenu createPopupMenu(View anchor) {
        PopupMenu popup = new PopupMenu(activity, anchor);
        popup.getMenuInflater().inflate(R.menu.menu_notification_popup, popup.getMenu());

        // Force icons to show using reflection
        try {
            Field mPopup = PopupMenu.class.getDeclaredField("mPopup");
            mPopup.setAccessible(true);
            Object menuPopupHelper = mPopup.get(popup);
            Class<?> classPopupHelper = Class.forName(menuPopupHelper.getClass().getName());
            Method setForceIcons = classPopupHelper.getMethod("setForceShowIcon", boolean.class);
            setForceIcons.invoke(menuPopupHelper, true);
            MenuCompat.setGroupDividerEnabled(popup.getMenu(), true);

        } catch (Exception e) {
            Log.e("NotificationsAdapter", "Error showing popup menu", e);
            // e.printStackTrace();
        }

        return popup;
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
        TextView tvHeader;

        public HeaderViewHolder(@NonNull View itemView) {
            super(itemView);
            tvHeader = itemView.findViewById(R.id.tvHeader);
        }
    }
}