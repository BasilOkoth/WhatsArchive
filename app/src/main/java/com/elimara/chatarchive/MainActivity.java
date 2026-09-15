package com.elimara.chatarchive;

import android.Manifest;
import android.app.AlertDialog;
import android.app.NotificationManager;
import android.content.BroadcastReceiver;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.provider.Settings;
import android.text.Editable;
import android.text.InputType;
import android.text.TextWatcher;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import androidx.fragment.app.FragmentActivity;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public class MainActivity extends FragmentActivity {
    private static final int REQ_UNLOCK = 1000;
    private static final int REQ_EXPORT_JSON = 1001;
    private static final int REQ_EXPORT_PNG = 1002;
    private static final int REQ_EXPORT_BACKUP = 1003;
    private static final int REQ_RESTORE_BACKUP = 1004;
    private static final int REQ_GALLERY_PERMISSION = 1005;

    private ArchiveDbHelper db;
    private AppSecurity security;
    private View premiumRoot;
    private TextView accessStatus;
    private TextView archiveCount;
    private TextView emptyState;
    private EditText searchBox;
    private Spinner statusSpinner;
    private Button lockButton;
    private Button proButton;
    private ListView contactList;
    private ContactSummaryAdapter contactAdapter;
    private final List<ContactSummary> visibleContacts = new ArrayList<>();
    private boolean unlocked;

    private String pendingJson;
    private byte[] pendingEncryptedBackup;
    private File pendingSnapshot;
    private ArchiveMessage pendingGalleryMessage;

    private final BroadcastReceiver archiveReceiver = new BroadcastReceiver() {
        @Override public void onReceive(Context context, Intent intent) {
            if (unlocked) refreshMessages();
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        db = new ArchiveDbHelper(this);
        security = new AppSecurity(this);
        bindViews();
        configureUi();

        if (security.isLockEnabled()) {
            premiumRoot.setVisibility(View.INVISIBLE);
            requestUnlock();
        } else {
            unlocked = true;
            premiumRoot.setVisibility(View.VISIBLE);
        }
    }

    private void bindViews() {
        premiumRoot = findViewById(R.id.premiumRoot);
        accessStatus = findViewById(R.id.accessStatus);
        archiveCount = findViewById(R.id.archiveCount);
        emptyState = findViewById(R.id.emptyState);
        searchBox = findViewById(R.id.searchBox);
        statusSpinner = findViewById(R.id.statusSpinner);
        lockButton = findViewById(R.id.lockButton);
        proButton = findViewById(R.id.proButton);
        contactList = findViewById(R.id.contactList);
    }

    private void configureUi() {
        contactAdapter = new ContactSummaryAdapter(this);
        contactList.setAdapter(contactAdapter);
        contactList.setEmptyView(emptyState);

        Button openAccessButton = findViewById(R.id.openAccessButton);
        Button backupButton = findViewById(R.id.backupButton);
        Button restoreButton = findViewById(R.id.restoreButton);
        Button exportButton = findViewById(R.id.exportButton);
        Button deleteButton = findViewById(R.id.deleteButton);

        openAccessButton.setOnClickListener(v -> showNotificationAccessDisclosure());
        lockButton.setOnClickListener(v -> showSecurityMenu());
        proButton.setOnClickListener(v -> openUpgrade());
        backupButton.setOnClickListener(v -> runProOrUpsell(this::createEncryptedBackup));
        restoreButton.setOnClickListener(v -> runProOrUpsell(this::chooseBackupToRestore));
        exportButton.setOnClickListener(v -> runProOrUpsell(this::exportJson));
        deleteButton.setOnClickListener(v -> confirmClear());

        statusSpinner.setAdapter(new ArrayAdapter<>(
                this,
                android.R.layout.simple_spinner_dropdown_item,
                Arrays.asList("All records", "Possible deletion", "Notification still present", "WhatsApp Business")));
        statusSpinner.setOnItemSelectedListener(new SimpleItemSelectedListener(this::refreshMessages));

        searchBox.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            @Override public void onTextChanged(CharSequence s, int start, int before, int count) { refreshMessages(); }
            @Override public void afterTextChanged(Editable s) {}
        });

        contactList.setOnItemClickListener((parent, view, position, id) -> {
            if (position >= 0 && position < visibleContacts.size()) {
                ContactSummary item = visibleContacts.get(position);
                showContactThread(item.conversationId, item.sender);
            }
        });
    }

    @Override protected void onStart() {
        super.onStart();
        IntentFilter filter = new IntentFilter(WhatsAppNotificationListener.ACTION_ARCHIVE_UPDATED);
        if (Build.VERSION.SDK_INT >= 33) registerReceiver(archiveReceiver, filter, Context.RECEIVER_NOT_EXPORTED);
        else registerReceiver(archiveReceiver, filter);
    }

    @Override protected void onStop() {
        super.onStop();
        try { unregisterReceiver(archiveReceiver); } catch (Exception ignored) {}
    }

    @Override protected void onResume() {
        super.onResume();
        if (unlocked) {
            updateAccessStatus();
            updateLockButton();
            updateProButton();
            refreshMessages();
        }
    }

    private void showNotificationAccessDisclosure() {
        new AlertDialog.Builder(this)
                .setTitle("Enable notification access?")
                .setMessage("ChatArchive needs Android Notification Access to create your private archive. If you continue, ChatArchive can read supported WhatsApp and WhatsApp Business notification content that Android exposes, including the chat or sender name, message text, notification time, and attachment information or files when available. Archived message content is stored locally on this device and is not used to access your WhatsApp account or chat database. You can decline now or disable Notification Access at any time in Android Settings.")
                .setNegativeButton("Not now", null)
                .setPositiveButton("Continue", (d, w) ->
                        startActivity(new Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS)))
                .show();
    }

    private void updateAccessStatus() {
        NotificationManager manager = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
        ComponentName component = new ComponentName(this, WhatsAppNotificationListener.class);
        boolean enabled;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
            enabled = manager != null && manager.isNotificationListenerAccessGranted(component);
        } else {
            String listeners = Settings.Secure.getString(getContentResolver(), "enabled_notification_listeners");
            enabled = listeners != null && listeners.contains(getPackageName());
        }
        accessStatus.setText(enabled
                ? "● Capture active — incoming supported WhatsApp notifications are archived locally"
                : "● Capture inactive — enable notification access to archive messages");
        accessStatus.setBackgroundResource(enabled ? R.drawable.bg_status_on : R.drawable.bg_status_off);
    }

    private void updateLockButton() {
        lockButton.setText(security.isLockEnabled() ? "Security ✓" : "Set app lock");
    }

    private void updateProButton() {
        proButton.setText(ProAccess.isPro(this) ? "Lifetime Pro ✓" : "Upgrade to Pro");
    }

    private void openUpgrade() {
        startActivity(new Intent(this, UpgradeActivity.class));
    }

    private void runProOrUpsell(Runnable action) {
        if (ProAccess.isPro(this)) action.run();
        else {
            Toast.makeText(this, "This is a Lifetime Pro feature.", Toast.LENGTH_SHORT).show();
            openUpgrade();
        }
    }

    private void refreshMessages() {
        if (db == null || contactAdapter == null || statusSpinner == null) return;
        List<ArchiveMessage> all = db.getAll();
        String status = statusSpinner.getSelectedItem() == null ? "All records" : statusSpinner.getSelectedItem().toString();
        String query = searchBox == null ? "" : searchBox.getText().toString().trim().toLowerCase(Locale.ROOT);

        Map<String, List<ArchiveMessage>> grouped = new LinkedHashMap<>();
        int possibleCount = 0;
        for (ArchiveMessage m : all) {
            if (m.isPossiblyDeleted()) possibleCount++;
            if (!matchesStatus(m, status)) continue;
            grouped.computeIfAbsent(conversationKey(m), k -> new ArrayList<>()).add(m);
        }

        visibleContacts.clear();
        for (Map.Entry<String, List<ArchiveMessage>> entry : grouped.entrySet()) {
            List<ArchiveMessage> messages = entry.getValue();
            if (messages.isEmpty()) continue;
            ArchiveMessage latest = messages.get(0);
            String conversation = latest.getConversationLabel();

            boolean match = query.isEmpty() || conversation.toLowerCase(Locale.ROOT).contains(query);
            if (!match) {
                for (ArchiveMessage m : messages) {
                    String haystack = (safe(m.body) + " " + safe(m.participantName) + " " + safe(m.sender)).toLowerCase(Locale.ROOT);
                    if (haystack.contains(query)) { match = true; break; }
                    for (ArchiveAttachment a : m.attachments) {
                        if (safe(a.fileName).toLowerCase(Locale.ROOT).contains(query)) { match = true; break; }
                    }
                    if (match) break;
                }
            }
            if (!match) continue;

            ContactSummary summary = new ContactSummary();
            summary.conversationId = entry.getKey();
            summary.sender = conversation;
            summary.latestBody = safe(latest.body);
            summary.latestAt = latest.postedAt;
            summary.messageCount = messages.size();
            summary.packageName = latest.packageName;
            for (ArchiveMessage m : messages) if (m.isPossiblyDeleted()) summary.possibleDeletionCount++;
            visibleContacts.add(summary);
        }

        contactAdapter.setItems(visibleContacts);
        archiveCount.setText(all.size() + " messages  •  " + visibleContacts.size() +
                " conversations shown  •  " + possibleCount + " possible deletions");
    }

    private boolean matchesStatus(ArchiveMessage m, String status) {
        if ("Possible deletion".equals(status)) return m.isPossiblyDeleted();
        if ("Notification still present".equals(status)) return m.removedAt == null;
        if ("WhatsApp Business".equals(status)) return "com.whatsapp.w4b".equals(m.packageName);
        return true;
    }

    private String conversationKey(ArchiveMessage m) {
        if (m.conversationId != null && !m.conversationId.trim().isEmpty()) return m.conversationId.trim();
        return sha256(safe(m.packageName) + "|" + m.getConversationLabel().toLowerCase(Locale.ROOT));
    }

    private void showContactThread(String conversationId, String conversationName) {
        List<ArchiveMessage> messages = new ArrayList<>();
        for (ArchiveMessage m : db.getAll()) if (conversationKey(m).equals(conversationId)) messages.add(m);
        if (messages.isEmpty()) return;

        LinearLayout container = new LinearLayout(this);
        container.setOrientation(LinearLayout.VERTICAL);
        container.setPadding(dp(10), dp(6), dp(10), 0);

        TextView summary = new TextView(this);
        summary.setText(messages.size() + (messages.size() == 1 ? " archived message" : " archived messages"));
        summary.setTextColor(getColor(R.color.wa_text_muted));
        summary.setPadding(dp(8), dp(2), dp(8), dp(8));
        container.addView(summary);

        ListView list = new ListView(this);
        list.setDivider(null);
        list.setDividerHeight(dp(8));
        ArchiveMessageAdapter adapter = new ArchiveMessageAdapter(this, new ArchiveMessageAdapter.ActionListener() {
            @Override public void onOpen(ArchiveMessage message) { showMessage(message); }
            @Override public void onShare(ArchiveMessage message) { shareMessage(message); }
            @Override public void onSnapshot(ArchiveMessage message) { showSnapshotActions(message); }
        });
        adapter.setItems(messages);
        list.setAdapter(adapter);
        container.addView(list, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
                (int)(getResources().getDisplayMetrics().heightPixels * .62f)));

        new AlertDialog.Builder(this)
                .setTitle(conversationName == null ? messages.get(0).getConversationLabel() : conversationName)
                .setView(container)
                .setNegativeButton("Close", null)
                .show();
    }

    private void showMessage(ArchiveMessage message) {
        SimpleDateFormat f = new SimpleDateFormat("dd MMMM yyyy, HH:mm:ss", Locale.getDefault());
        StringBuilder text = new StringBuilder(safe(message.body));
        text.append("\n\nReceived: ").append(f.format(new Date(message.postedAt)));
        if (message.removedAt != null) {
            text.append("\nNotification removed: ").append(f.format(new Date(message.removedAt)));
            text.append("\n\nRemoval can mean sender deletion, dismissal, opening, or replacement; it is not proof of sender deletion.");
        }
        if (!message.attachments.isEmpty()) text.append("\n\nAttachments: ").append(message.attachments.size());

        new AlertDialog.Builder(this)
                .setTitle(message.getMessageSenderLabel())
                .setMessage(text.toString())
                .setNeutralButton("Copy", (d,w) -> copyMessage(message))
                .setPositiveButton("Share", (d,w) -> shareMessage(message))
                .setNegativeButton("Close", null)
                .show();
    }

    private void copyMessage(ArchiveMessage message) {
        ClipboardManager clipboard = (ClipboardManager) getSystemService(CLIPBOARD_SERVICE);
        if (clipboard != null) {
            clipboard.setPrimaryClip(ClipData.newPlainText("ChatArchive message", safe(message.body)));
            Toast.makeText(this, "Message copied", Toast.LENGTH_SHORT).show();
        }
    }

    private void shareMessage(ArchiveMessage message) {
        String text = message.getConversationLabel() + "\n" + safe(message.body) +
                "\n\nArchived locally with ChatArchive";
        Intent share = new Intent(Intent.ACTION_SEND);
        share.setType("text/plain");
        share.putExtra(Intent.EXTRA_TEXT, text);
        startActivity(Intent.createChooser(share, "Share archived message"));
    }

    private JSONObject buildArchiveJson() throws Exception {
        JSONArray array = new JSONArray();
        for (ArchiveMessage m : db.getAll()) {
            JSONObject obj = new JSONObject();
            obj.put("id", m.id);
            obj.put("packageName", m.packageName);
            obj.put("conversationId", conversationKey(m));
            obj.put("conversationName", m.getConversationLabel());
            obj.put("participantName", safe(m.participantName));
            obj.put("sender", safe(m.sender));
            obj.put("message", safe(m.body));
            obj.put("receivedAtEpochMs", m.postedAt);
            obj.put("notificationRemovedAtEpochMs", m.removedAt == null ? JSONObject.NULL : m.removedAt);
            obj.put("possibleDeletion", m.isPossiblyDeleted());
            obj.put("fingerprint", safe(m.fingerprint));
            JSONArray attachments = new JSONArray();
            for (ArchiveAttachment a : m.attachments) {
                JSONObject ao = new JSONObject();
                ao.put("fileName", safe(a.fileName));
                ao.put("mimeType", safe(a.mimeType));
                ao.put("byteSize", a.byteSize);
                ao.put("availableLocally", a.isAvailable());
                ao.put("captureStatus", safe(a.captureStatus));
                attachments.put(ao);
            }
            obj.put("attachments", attachments);
            array.put(obj);
        }
        JSONObject root = new JSONObject();
        root.put("format", "chatarchive-export");
        root.put("version", 5);
        root.put("productCompany", "Elimara Technologies Limited");
        root.put("creator", "Basil Okoth");
        root.put("exportedAtEpochMs", System.currentTimeMillis());
        root.put("messages", array);
        return root;
    }

    private void exportJson() {
        try {
            pendingJson = buildArchiveJson().toString(2);
            Intent intent = new Intent(Intent.ACTION_CREATE_DOCUMENT);
            intent.addCategory(Intent.CATEGORY_OPENABLE);
            intent.setType("application/json");
            intent.putExtra(Intent.EXTRA_TITLE, "chatarchive-" + stamp() + ".json");
            startActivityForResult(intent, REQ_EXPORT_JSON);
        } catch (Exception e) {
            Toast.makeText(this, "Unable to prepare export", Toast.LENGTH_LONG).show();
        }
    }

    private void createEncryptedBackup() {
        showPasswordDialog("Create encrypted backup", true, password -> {
            try {
                pendingEncryptedBackup = BackupCrypto.encrypt(
                        buildArchiveJson().toString().getBytes(StandardCharsets.UTF_8), password.toCharArray());
                Intent intent = new Intent(Intent.ACTION_CREATE_DOCUMENT);
                intent.addCategory(Intent.CATEGORY_OPENABLE);
                intent.setType("application/octet-stream");
                intent.putExtra(Intent.EXTRA_TITLE, "chatarchive-secure-" + stamp() + ".cab");
                startActivityForResult(intent, REQ_EXPORT_BACKUP);
            } catch (Exception e) {
                Toast.makeText(this, "Backup encryption failed", Toast.LENGTH_LONG).show();
            }
        });
    }

    private void chooseBackupToRestore() {
        Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        intent.setType("*/*");
        startActivityForResult(intent, REQ_RESTORE_BACKUP);
    }

    private void restoreData(byte[] bytes) {
        if (looksLikeJson(bytes)) {
            try {
                int count = restoreArchiveJson(new String(bytes, StandardCharsets.UTF_8));
                Toast.makeText(this, count + " message(s) imported; duplicates skipped", Toast.LENGTH_LONG).show();
                refreshMessages();
            } catch (Exception e) {
                Toast.makeText(this, "Invalid ChatArchive JSON", Toast.LENGTH_LONG).show();
            }
            return;
        }
        showPasswordDialog("Unlock encrypted backup", false, password -> {
            try {
                byte[] plain = BackupCrypto.decrypt(bytes, password.toCharArray());
                int count = restoreArchiveJson(new String(plain, StandardCharsets.UTF_8));
                Toast.makeText(this, count + " message(s) restored; duplicates skipped", Toast.LENGTH_LONG).show();
                refreshMessages();
            } catch (Exception e) {
                Toast.makeText(this, "Could not decrypt backup. Check the password.", Toast.LENGTH_LONG).show();
            }
        });
    }

    private int restoreArchiveJson(String json) throws Exception {
        JSONObject root = new JSONObject(json);
        JSONArray messages = root.getJSONArray("messages");
        int restored = 0;
        for (int i=0;i<messages.length();i++) {
            JSONObject o = messages.getJSONObject(i);
            String pkg = o.optString("packageName", "com.whatsapp");
            String conversationName = o.optString("conversationName", o.optString("sender", "Unknown chat"));
            String participant = o.optString("participantName", "");
            String conversationId = o.optString("conversationId", "restore:" + sha256(pkg + "|" + conversationName));
            String sender = o.optString("sender", conversationName);
            String body = o.optString("message", "");
            long posted = o.optLong("receivedAtEpochMs", System.currentTimeMillis());
            String fingerprint = o.optString("fingerprint", "");
            if (fingerprint.isEmpty()) fingerprint = sha256(pkg + "|restore|" + conversationId + "|" + body + "|" + posted);
            long id = db.insertMessage(pkg, null, conversationId, conversationName, participant, sender, body, posted, fingerprint);
            if (id <= 0) continue;
            if (!o.isNull("notificationRemovedAtEpochMs")) {
                long removed = o.optLong("notificationRemovedAtEpochMs", 0);
                if (removed > 0) db.setRemovedAt(id, removed);
            }
            String path = SnapshotRenderer.create(this, id, sender, body, posted, pkg);
            if (path != null) db.setSnapshotPath(id, path);
            restored++;
        }
        return restored;
    }

    private void showSnapshotActions(ArchiveMessage message) {
        String[] options = {"Save snapshot to Gallery", "Export snapshot PNG to file"};
        new AlertDialog.Builder(this).setTitle("Snapshot / Export")
                .setItems(options, (d,which) -> {
                    if (which == 0) saveSnapshotToGallery(message); else exportSnapshot(message);
                }).show();
    }

    private File snapshotFile(ArchiveMessage message) {
        if (message == null || message.snapshotPath == null) return null;
        File file = new File(message.snapshotPath);
        return file.isFile() ? file : null;
    }

    private void saveSnapshotToGallery(ArchiveMessage message) {
        File file = snapshotFile(message);
        if (file == null) { Toast.makeText(this, "No snapshot is available", Toast.LENGTH_LONG).show(); return; }
        if (Build.VERSION.SDK_INT <= 28 && checkSelfPermission(Manifest.permission.WRITE_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED) {
            pendingGalleryMessage = message;
            requestPermissions(new String[]{Manifest.permission.WRITE_EXTERNAL_STORAGE}, REQ_GALLERY_PERMISSION);
            return;
        }
        try {
            String name = "ChatArchive_" + new SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(new Date(message.postedAt)) + "_" + message.id + ".png";
            GallerySaver.savePng(this, file, name);
            Toast.makeText(this, "Snapshot saved to Pictures/ChatArchive", Toast.LENGTH_LONG).show();
        } catch (Exception e) {
            Toast.makeText(this, "Could not save snapshot", Toast.LENGTH_LONG).show();
        }
    }

    private void exportSnapshot(ArchiveMessage message) {
        File file = snapshotFile(message);
        if (file == null) { Toast.makeText(this, "No snapshot is available", Toast.LENGTH_LONG).show(); return; }
        pendingSnapshot = file;
        Intent intent = new Intent(Intent.ACTION_CREATE_DOCUMENT);
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        intent.setType("image/png");
        intent.putExtra(Intent.EXTRA_TITLE, "chatarchive-message-" + message.id + ".png");
        startActivityForResult(intent, REQ_EXPORT_PNG);
    }

    private void showSecurityMenu() {
        if (!security.isLockEnabled()) { showPinSetupDialog(); return; }
        String[] options = {"Lock now", "Change PIN", "Disable app lock"};
        new AlertDialog.Builder(this).setTitle("Archive security").setItems(options, (d,which) -> {
            if (which == 0) requestUnlock();
            else if (which == 1) showPinVerification(() -> showPinSetupDialog());
            else showPinVerification(() -> { security.setLockEnabled(false); updateLockButton(); });
        }).show();
    }

    private void showPinSetupDialog() {
        EditText first = pinField("4–8 digit PIN");
        EditText second = pinField("Confirm PIN");
        LinearLayout wrap = wrapFields(first, second);
        AlertDialog dialog = new AlertDialog.Builder(this)
                .setTitle("Create app PIN").setView(wrap)
                .setNegativeButton("Cancel", null).setPositiveButton("Save PIN", null).create();
        dialog.setOnShowListener(x -> dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v -> {
            String a=first.getText().toString(), b=second.getText().toString();
            if (!a.matches("\\d{4,8}")) { first.setError("Use 4–8 digits"); return; }
            if (!a.equals(b)) { second.setError("PINs do not match"); return; }
            if (security.setPin(a)) { dialog.dismiss(); updateLockButton(); }
        }));
        dialog.show();
    }

    private void showPinVerification(Runnable success) {
        EditText input = pinField("Current PIN");
        AlertDialog dialog = new AlertDialog.Builder(this).setTitle("Verify PIN")
                .setView(wrapFields(input)).setNegativeButton("Cancel", null).setPositiveButton("Continue", null).create();
        dialog.setOnShowListener(x -> dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v -> {
            if (security.verifyPin(input.getText().toString())) { dialog.dismiss(); success.run(); }
            else input.setError("Incorrect PIN");
        }));
        dialog.show();
    }

    private void requestUnlock() {
        unlocked = false;
        premiumRoot.setVisibility(View.INVISIBLE);
        startActivityForResult(new Intent(this, LockActivity.class), REQ_UNLOCK);
    }

    private void showPasswordDialog(String title, boolean confirm, PasswordConsumer consumer) {
        EditText first = passwordField("Backup password");
        EditText second = confirm ? passwordField("Confirm password") : null;
        AlertDialog dialog = new AlertDialog.Builder(this).setTitle(title)
                .setView(confirm ? wrapFields(first,second) : wrapFields(first))
                .setNegativeButton("Cancel", null).setPositiveButton(confirm ? "Encrypt" : "Unlock", null).create();
        dialog.setOnShowListener(x -> dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v -> {
            String password = first.getText().toString();
            if (password.length() < 8) { first.setError("Use at least 8 characters"); return; }
            if (confirm && !password.equals(second.getText().toString())) { second.setError("Passwords do not match"); return; }
            dialog.dismiss(); consumer.accept(password);
        }));
        dialog.show();
    }

    private EditText pinField(String hint) {
        EditText e = new EditText(this); e.setHint(hint); e.setSingleLine(true);
        e.setInputType(InputType.TYPE_CLASS_NUMBER | InputType.TYPE_NUMBER_VARIATION_PASSWORD); return e;
    }
    private EditText passwordField(String hint) {
        EditText e = new EditText(this); e.setHint(hint); e.setSingleLine(true);
        e.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PASSWORD); return e;
    }
    private LinearLayout wrapFields(EditText... fields) {
        LinearLayout l = new LinearLayout(this); l.setOrientation(LinearLayout.VERTICAL); l.setPadding(dp(20),dp(8),dp(20),0);
        for (EditText f : fields) l.addView(f); return l;
    }

    private void confirmClear() {
        new AlertDialog.Builder(this).setTitle("Clear local archive?")
                .setMessage("This permanently deletes the local encrypted database, retained attachments and snapshots. Export a backup first if needed.")
                .setNegativeButton("Cancel", null)
                .setPositiveButton("Delete everything", (d,w) -> {
                    db.clearAll(); deleteRecursively(new File(getFilesDir(), "snapshots")); refreshMessages();
                }).show();
    }

    private void deleteRecursively(File file) {
        if (file == null || !file.exists()) return;
        if (file.isDirectory()) { File[] children=file.listFiles(); if (children!=null) for (File c:children) deleteRecursively(c); }
        //noinspection ResultOfMethodCallIgnored
        file.delete();
    }

    @Override public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == REQ_GALLERY_PERMISSION && pendingGalleryMessage != null) {
            ArchiveMessage m = pendingGalleryMessage; pendingGalleryMessage = null;
            if (grantResults.length>0 && grantResults[0]==PackageManager.PERMISSION_GRANTED) saveSnapshotToGallery(m);
        }
    }

    @Override protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == REQ_UNLOCK) {
            if (resultCode == RESULT_OK) {
                unlocked = true; premiumRoot.setVisibility(View.VISIBLE); updateAccessStatus(); updateProButton(); refreshMessages();
            } else finish();
            return;
        }
        if (resultCode != RESULT_OK || data == null || data.getData() == null) return;
        Uri uri = data.getData();
        try {
            if (requestCode == REQ_EXPORT_JSON && pendingJson != null) {
                writeBytes(uri, pendingJson.getBytes(StandardCharsets.UTF_8)); pendingJson = null;
                Toast.makeText(this, "JSON archive exported", Toast.LENGTH_SHORT).show();
            } else if (requestCode == REQ_EXPORT_BACKUP && pendingEncryptedBackup != null) {
                writeBytes(uri, pendingEncryptedBackup); pendingEncryptedBackup = null;
                Toast.makeText(this, "Encrypted backup saved", Toast.LENGTH_SHORT).show();
            } else if (requestCode == REQ_RESTORE_BACKUP) {
                restoreData(readBytes(uri));
            } else if (requestCode == REQ_EXPORT_PNG && pendingSnapshot != null) {
                try (FileInputStream in = new FileInputStream(pendingSnapshot); OutputStream out = getContentResolver().openOutputStream(uri)) {
                    if (out == null) throw new IllegalStateException(); copy(in,out);
                }
                pendingSnapshot = null;
                Toast.makeText(this, "Snapshot PNG exported", Toast.LENGTH_SHORT).show();
            }
        } catch (Exception e) {
            Toast.makeText(this, "File operation failed: " + e.getMessage(), Toast.LENGTH_LONG).show();
        }
    }

    private boolean looksLikeJson(byte[] data) {
        if (data == null) return false; int i=0; while (i<data.length && Character.isWhitespace((char)data[i])) i++;
        return i<data.length && data[i]=='{';
    }
    private void writeBytes(Uri uri, byte[] bytes) throws Exception {
        try (OutputStream out=getContentResolver().openOutputStream(uri)) { if (out==null) throw new IllegalStateException(); out.write(bytes); }
    }
    private byte[] readBytes(Uri uri) throws Exception {
        try (InputStream in=getContentResolver().openInputStream(uri); ByteArrayOutputStream out=new ByteArrayOutputStream()) {
            if (in==null) throw new IllegalStateException(); copy(in,out); return out.toByteArray();
        }
    }
    private void copy(InputStream in, OutputStream out) throws Exception { byte[] b=new byte[8192]; int r; while((r=in.read(b))!=-1) out.write(b,0,r); }
    private String stamp() { return new SimpleDateFormat("yyyyMMdd-HHmmss", Locale.US).format(new Date()); }
    private int dp(int v) { return Math.round(v * getResources().getDisplayMetrics().density); }
    private String safe(String v) { return v == null ? "" : v; }
    private String sha256(String value) {
        try { MessageDigest d=MessageDigest.getInstance("SHA-256"); byte[] hash=d.digest(value.getBytes(StandardCharsets.UTF_8)); StringBuilder s=new StringBuilder(); for(byte b:hash) s.append(String.format(Locale.US,"%02x",b)); return s.toString(); }
        catch(Exception e){ return Integer.toHexString(value.hashCode()); }
    }
    private interface PasswordConsumer { void accept(String password); }
}
