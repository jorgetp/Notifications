package com.jorgetp.notifications.adapter;

import static com.jorgetp.notifications.MainActivity.ALWAYS;
import static com.jorgetp.notifications.MainActivity.NON_BUSINESS;
import static com.jorgetp.notifications.MainActivity.SILENCED_APPS_PREFS;

import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.drawable.Drawable;
import android.util.Pair;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.PopupMenu;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.jorgetp.notifications.MainActivity;
import com.jorgetp.notifications.R;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.Map;
import java.util.TreeSet;

public class SilencedAppsAdapter extends RecyclerView.Adapter<SilencedAppsAdapter.ViewHolder> {
    private final Context context;
    private final ArrayList<SilencedApp> apps;
    private final TreeSet<String> editedItems = new TreeSet<>();

    public SilencedAppsAdapter(Context context) {
        this.context = context;
        apps = new ArrayList<>(10);

        SharedPreferences prefs = MainActivity.getPrefs(context, SILENCED_APPS_PREFS);
        for (Map.Entry<String, ?> entry : prefs.getAll().entrySet()) {
            String packageName = entry.getKey();
            Integer silencedWhen = (Integer) entry.getValue();
            apps.add(new SilencedApp(packageName, silencedWhen));
        }

        // sort apps by app name
        apps.sort(Comparator.comparing(app ->
                MainActivity.getAppInfo(context, app.packageName).first.toString().toLowerCase()));
    }

    public TreeSet<String> getEditedItems() {
        return editedItems;
    }

    @Override
    public int getItemCount() {
        return apps.size();
    }


    @Override
    public long getItemId(int position) {
        return position;
    }

    @NonNull
    @Override
    public SilencedAppsAdapter.ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(context).inflate(R.layout.item_silenced_app, parent, false);
        return new SilencedAppsAdapter.ViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int i) {
        SilencedApp app = apps.get(i);

        // load app name and icon
        Pair<CharSequence, Drawable> appInfo = MainActivity.getAppInfo(context, app.packageName);
        holder.tvApp.setText(appInfo.first);
        if (appInfo.second != null)
            holder.ivIcon.setImageDrawable(appInfo.second);
        else
            holder.ivIcon.setImageResource(android.R.drawable.sym_def_app_icon);

        holder.tvSilencedWhen.setText(app.silencedWhen == ALWAYS ?
                context.getString(R.string.silenced_always) :
                context.getString(R.string.silenced_non_business));

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
                    apps.set(position, new SilencedApp(app.packageName, ALWAYS));
                    notifyItemChanged(position);
                    return true;

                } else if (itemId == R.id.silenced_non_business) {
                    editedItems.add(app.packageName);
                    prefs.edit().putInt(app.packageName, NON_BUSINESS).apply();
                    apps.set(position, new SilencedApp(app.packageName, NON_BUSINESS));
                    notifyItemChanged(position);
                    return true;

                } else if (itemId == R.id.not_silenced) {
                    editedItems.add(app.packageName);
                    prefs.edit().remove(app.packageName).apply();
                    apps.remove(position);
                    notifyItemRemoved(position);
                    return true;
                }
                return false;
            });

            popup.show();
        });
    }

    public static class ViewHolder extends RecyclerView.ViewHolder {
        ImageView ivIcon;
        TextView tvApp;
        TextView tvSilencedWhen;

        public ViewHolder(@NonNull View itemView) {
            super(itemView);
            ivIcon = itemView.findViewById(R.id.ivAppIcon);
            tvApp = itemView.findViewById(R.id.tvTitle);
            tvSilencedWhen = itemView.findViewById(R.id.tvText);
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
}
