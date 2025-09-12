package com.jorgetp.notifications;

import android.content.Intent;
import android.os.Bundle;

import androidx.activity.EdgeToEdge;
import androidx.activity.OnBackPressedCallback;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.jorgetp.notifications.adapter.ImportantSendersAdapter;
import com.jorgetp.notifications.adapter.SilencedAppsAdapter;

import java.util.TreeSet;

public class ItemsActivity extends AppCompatActivity {
    private RecyclerView.Adapter<?> adapter;

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

        String adapterType = getIntent().getStringExtra("adapter");
        RecyclerView rvItems = findViewById(R.id.rvItems);
        if (rvItems != null && adapterType != null) {
            rvItems.setLayoutManager(new LinearLayoutManager(this, LinearLayoutManager.VERTICAL, false));
            if (adapterType.equals(SilencedAppsAdapter.class.getSimpleName())) {
                setTitle(R.string.silenced_apps);
                rvItems.setAdapter(adapter = new SilencedAppsAdapter(this));

            } else if (adapterType.equals(ImportantSendersAdapter.class.getSimpleName())) {
                setTitle(R.string.important_senders);
                rvItems.setAdapter(adapter = new ImportantSendersAdapter(this));
            }
        }

        getOnBackPressedDispatcher().addCallback(this, new OnBackPressedCallback(true) {
            @Override
            public void handleOnBackPressed() {
                Intent resultIntent = new Intent();
                TreeSet<String> editedItems = new TreeSet<>();
                if (adapter instanceof SilencedAppsAdapter) {
                    editedItems = ((SilencedAppsAdapter) adapter).getEditedItems();
                } else if (adapter instanceof ImportantSendersAdapter) {
                    editedItems = ((ImportantSendersAdapter) adapter).getEditedItems();
                }
                resultIntent.putExtra("edited_items", editedItems);
                setResult(RESULT_OK, resultIntent);
                finish();
            }
        });
    }
}