package com.basil.whatsarchive;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.NotificationManager;
import android.content.ComponentName;
import android.content.Intent;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.provider.Settings;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;

public class MainActivity extends Activity {
    private static final int REQ_EXPORT_JSON = 1001;
    private static final int REQ_EXPORT_PNG = 1002;

    private ArchiveDbHelper db;
    private TextView accessStatus;
    private TextView archiveCount;
    private EditText searchBox;
    private ListView listView;
    private ArrayAdapter<String> adapter;
    private final List<ArchiveMessage> visibleMessages = new ArrayList<>();
    private String pendingJson;
    private File pendingSnapshot;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        db = new ArchiveDbHelper(this);
        accessStatus = findViewById(R.id.accessStatus);
        archiveCount = findViewById(R.id.archiveCount);
        searchBox = findViewById(R.id.searchBox);
        listView = findViewById(R.id.messageList);
        Button openAccessButton = findViewById(R.id.openAccessButton);
        Button exportButton = findViewById(R.id.exportButton);
        Button deleteButton = findViewById(R.id.deleteButton);

        adapter = new ArrayAdapter<>(this, android.R.layout.simple_list_item_1, new ArrayList<>());
        listView.setAdapter(adapter);

        openAccessButton.setOnClickListener(v -> {
            Intent intent = new Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS);
            startActivity(intent);
        });

        exportButton.setOnClickListener(v -> exportJson());
        deleteButton.setOnClickListener(v -> confirmClear());

        searchBox.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            @Override public void onTextChanged(CharSequence s, int start, int before, int count) { refreshMessages(); }
            @Override public void afterTextChanged(Editable s) {}
        });

        listView.setOnItemClickListener((parent, view, position, id) -> {
            if (position >= 0 && position < visibleMessages.size()) showMessage(visibleMessages.get(position));
        });
    }

    @Override
    protected void onResume() {
        super.onResume();
        updateAccessStatus();
        refreshMessages();
    }

    private void updateAccessStatus() {
        NotificationManager manager = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
        ComponentName componentName = new ComponentName(this, WhatsAppNotificationListener.class);
        boolean enabled;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
            enabled = manager != null && manager.isNotificationListenerAccessGranted(componentName);
        } else {
            String listeners = Settings.Secure.getString(getContentResolver(), "enabled_notification_listeners");
            enabled = listeners != null && listeners.contains(getPackageName());
        }
        accessStatus.setText(enabled
                ? "Notification access: ON — WhatsApp notifications can be archived"
                : "Notification access: OFF — enable it for WhatsArchive to work");
    }

    private void refreshMessages() {
        List<ArchiveMessage> all = db.getAll();
        String query = searchBox == null ? "" : searchBox.getText().toString().trim().toLowerCase(Locale.ROOT);
        visibleMessages.clear();
        List<String> rows = new ArrayList<>();
        SimpleDateFormat format = new SimpleDateFormat("dd MMM, HH:mm", Locale.getDefault());

        for (ArchiveMessage message : all) {
            String haystack = (message.sender + "\n" + message.body).toLowerCase(Locale.ROOT);
            if (!query.isEmpty() && !haystack.contains(query)) continue;
            visibleMessages.add(message);
            String preview = message.body.replace('\n', ' ');
            if (preview.length() > 90) preview = preview.substring(0, 87) + "...";
            String app = "com.whatsapp.w4b".equals(message.packageName) ? "WA Business" : "WhatsApp";
            rows.add(message.sender + "\n" + preview + "\n" + format.format(new Date(message.postedAt)) + " • " + app);
        }

        adapter.clear();
        adapter.addAll(rows);
        adapter.notifyDataSetChanged();
        archiveCount.setText("Archived: " + all.size() + " notification records");
    }

    private void showMessage(ArchiveMessage message) {
        SimpleDateFormat format = new SimpleDateFormat("dd MMMM yyyy, HH:mm:ss", Locale.getDefault());
        String removed = message.removedAt == null
                ? "Notification removal: not observed"
                : "Notification removed from status bar: " + format.format(new Date(message.removedAt)) +
                  "\n(This does NOT prove the sender deleted the message.)";

        String text = message.body + "\n\nReceived: " + format.format(new Date(message.postedAt)) +
                "\n" + removed;

        AlertDialog dialog = new AlertDialog.Builder(this)
                .setTitle(message.sender)
                .setMessage(text)
                .setNegativeButton("Close", null)
                .setPositiveButton("Export snapshot", null)
                .create();

        dialog.setOnShowListener(d -> dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v -> {
            exportSnapshot(message);
            dialog.dismiss();
        }));
        dialog.show();
    }

    private void exportJson() {
        try {
            List<ArchiveMessage> all = db.getAll();
            JSONArray array = new JSONArray();
            for (ArchiveMessage message : all) {
                JSONObject obj = new JSONObject();
                obj.put("id", message.id);
                obj.put("source", "com.whatsapp.w4b".equals(message.packageName) ? "WhatsApp Business" : "WhatsApp");
                obj.put("sender", message.sender);
                obj.put("message", message.body);
                obj.put("receivedAtEpochMs", message.postedAt);
                obj.put("notificationRemovedAtEpochMs", message.removedAt == null ? JSONObject.NULL : message.removedAt);
                obj.put("notificationRemovalIsProofOfSenderDeletion", false);
                array.put(obj);
            }
            JSONObject root = new JSONObject();
            root.put("format", "whatsarchive-export");
            root.put("version", 1);
            root.put("exportedAtEpochMs", System.currentTimeMillis());
            root.put("messages", array);
            pendingJson = root.toString(2);

            Intent intent = new Intent(Intent.ACTION_CREATE_DOCUMENT);
            intent.addCategory(Intent.CATEGORY_OPENABLE);
            intent.setType("application/json");
            intent.putExtra(Intent.EXTRA_TITLE, "whatsarchive-" + new SimpleDateFormat("yyyyMMdd-HHmmss", Locale.US).format(new Date()) + ".json");
            startActivityForResult(intent, REQ_EXPORT_JSON);
        } catch (Exception e) {
            Toast.makeText(this, "Unable to prepare export", Toast.LENGTH_LONG).show();
        }
    }

    private void exportSnapshot(ArchiveMessage message) {
        if (message.snapshotPath == null) {
            Toast.makeText(this, "No snapshot is available for this record", Toast.LENGTH_LONG).show();
            return;
        }
        File file = new File(message.snapshotPath);
        if (!file.exists()) {
            Toast.makeText(this, "Snapshot file is missing", Toast.LENGTH_LONG).show();
            return;
        }
        pendingSnapshot = file;
        Intent intent = new Intent(Intent.ACTION_CREATE_DOCUMENT);
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        intent.setType("image/png");
        intent.putExtra(Intent.EXTRA_TITLE, "whatsarchive-message-" + message.id + ".png");
        startActivityForResult(intent, REQ_EXPORT_PNG);
    }

    private void confirmClear() {
        new AlertDialog.Builder(this)
                .setTitle("Clear archive?")
                .setMessage("This permanently deletes the local message database and generated snapshots from this app.")
                .setNegativeButton("Cancel", null)
                .setPositiveButton("Delete", (dialog, which) -> {
                    db.clearAll();
                    deleteRecursively(new File(getFilesDir(), "snapshots"));
                    refreshMessages();
                })
                .show();
    }

    private void deleteRecursively(File file) {
        if (file == null || !file.exists()) return;
        if (file.isDirectory()) {
            File[] children = file.listFiles();
            if (children != null) for (File child : children) deleteRecursively(child);
        }
        //noinspection ResultOfMethodCallIgnored
        file.delete();
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (resultCode != RESULT_OK || data == null || data.getData() == null) return;
        Uri uri = data.getData();

        try {
            if (requestCode == REQ_EXPORT_JSON && pendingJson != null) {
                try (OutputStream out = getContentResolver().openOutputStream(uri)) {
                    if (out == null) throw new IllegalStateException("No output stream");
                    out.write(pendingJson.getBytes(StandardCharsets.UTF_8));
                }
                pendingJson = null;
                Toast.makeText(this, "JSON archive exported", Toast.LENGTH_SHORT).show();
            } else if (requestCode == REQ_EXPORT_PNG && pendingSnapshot != null) {
                try (FileInputStream in = new FileInputStream(pendingSnapshot);
                     OutputStream out = getContentResolver().openOutputStream(uri)) {
                    if (out == null) throw new IllegalStateException("No output stream");
                    byte[] buffer = new byte[8192];
                    int read;
                    while ((read = in.read(buffer)) != -1) out.write(buffer, 0, read);
                }
                pendingSnapshot = null;
                Toast.makeText(this, "Snapshot exported", Toast.LENGTH_SHORT).show();
            }
        } catch (Exception e) {
            Toast.makeText(this, "Export failed: " + e.getMessage(), Toast.LENGTH_LONG).show();
        }
    }
}
