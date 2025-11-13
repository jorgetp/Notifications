package com.jorgetp.notifications;

import static com.jorgetp.notifications.MainActivity.IMPORTANT_SENDERS_PREFS;
import static com.jorgetp.notifications.MainActivity.SILENCED_APPS_PREFS;

import android.content.ComponentName;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.provider.Settings;
import android.text.TextUtils;

import androidx.activity.EdgeToEdge;
import androidx.activity.OnBackPressedCallback;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.jorgetp.notifications.adapter.SettingsAdapter;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.Map;
import java.util.TreeSet;
import java.util.concurrent.Executors;

public class SettingsActivity extends AppCompatActivity {
    private SettingsAdapter adapter;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_settings);
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main), (v, insets) -> {
            Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom);
            return insets;
        });

        RecyclerView rvItems = findViewById(R.id.rvItems);
        LinearLayoutManager lm = new LinearLayoutManager(this);
        rvItems.setLayoutManager(lm);
        rvItems.setAdapter(adapter = new SettingsAdapter(this));
        getItemsAndRefreshUI();

        getOnBackPressedDispatcher().addCallback(this, new OnBackPressedCallback(true) {
            @Override
            public void handleOnBackPressed() {
                Intent resultIntent = new Intent();
                TreeSet<String> editedItems = adapter.getEditedItems();
                resultIntent.putExtra("edited_items", editedItems);
                setResult(RESULT_OK, resultIntent);
                finish();
            }
        });
    }

    public void getItemsAndRefreshUI() {
        Executors.newSingleThreadExecutor().execute(() -> {
            ArrayList<Object> items = new ArrayList<>(10);

            // General settings
            items.add(getString(R.string.general));
            items.add(new SettingsAdapter.Switch(getString(R.string.nsl), isNotificationServiceEnabled()));
            /*items.add(new SettingsAdapter.Switch(getString(R.string.pin_important_senders),
                    MainActivity.getPrefs(this, SETTINGS_PREFS).getBoolean("pin_important_senders", false)));*/

            // Important senders
            ArrayList<SettingsAdapter.ImportantSender> senders = new ArrayList<SettingsAdapter.ImportantSender>(10);
            SharedPreferences prefs2 = MainActivity.getPrefs(this, IMPORTANT_SENDERS_PREFS);
            for (Map.Entry<String, ?> entry : prefs2.getAll().entrySet()) {
                String key = entry.getKey();
                // String value = entry.getValue().toString();
                String[] parts = key.split("/", 2);
                senders.add(new SettingsAdapter.ImportantSender(parts[0], parts[1]));
            }
            senders.sort(Comparator.comparing(sender -> sender.sender.toLowerCase()));

            items.add(getString(R.string.important_senders));
            items.addAll(senders);

            // Silenced apps
            ArrayList<SettingsAdapter.SilencedApp> apps = new ArrayList<>(10);
            SharedPreferences prefs1 = MainActivity.getPrefs(this, SILENCED_APPS_PREFS);
            for (Map.Entry<String, ?> entry : prefs1.getAll().entrySet()) {
                String packageName = entry.getKey();
                Integer silencedWhen = (Integer) entry.getValue();
                apps.add(new SettingsAdapter.SilencedApp(packageName, silencedWhen));
            }
            apps.sort(Comparator
                    .comparing(app -> MainActivity.getAppInfo(this, app.packageName).first.toString().toLowerCase()));

            items.add(getString(R.string.silenced_apps));
            items.addAll(apps);

            adapter.setItems(items);
            runOnUiThread(() -> adapter.notifyDataSetChanged());
        });
    }

    public boolean isNotificationServiceEnabled() {
        String pkgName = getPackageName();
        String enabledListeners = Settings.Secure.getString(getContentResolver(),
                "enabled_notification_listeners");
        if (!TextUtils.isEmpty(enabledListeners)) {
            String[] listeners = enabledListeners.split(":");
            for (String listener : listeners) {
                ComponentName cn = ComponentName.unflattenFromString(listener);
                if (cn != null && TextUtils.equals(pkgName, cn.getPackageName())) {
                    return true;
                }
            }
        }
        return false;
    }
}