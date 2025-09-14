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

import com.jorgetp.notifications.adapter.SettingsAdapter;

import java.util.TreeSet;

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
}