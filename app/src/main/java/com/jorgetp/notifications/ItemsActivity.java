package com.jorgetp.notifications;

import android.os.Bundle;

import androidx.activity.EdgeToEdge;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.jorgetp.notifications.adapter.ImportantSendersAdapter;
import com.jorgetp.notifications.adapter.SilencedAppsAdapter;

public class ItemsActivity extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_items);
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main), (v, insets) -> {
            Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom);
            return insets;
        });

        String adapter = getIntent().getStringExtra("adapter");
        RecyclerView rvItems = findViewById(R.id.rvItems);
        if (rvItems != null && adapter != null) {
            rvItems.setLayoutManager(new LinearLayoutManager(this, LinearLayoutManager.VERTICAL, false));
            if (adapter.equals(SilencedAppsAdapter.class.getSimpleName())) {
                setTitle(R.string.silenced_apps);
                rvItems.setAdapter(new SilencedAppsAdapter(this));

            } else if (adapter.equals(ImportantSendersAdapter.class.getSimpleName())) {
                setTitle(R.string.important_senders);
                rvItems.setAdapter(new ImportantSendersAdapter(this));
            }
        }
    }
}