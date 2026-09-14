package com.basil.whatsarchive;

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
import android.text.SpannableString;
import android.text.Spanned;
import android.text.TextWatcher;
import android.text.style.ForegroundColorSpan;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import androidx.biometric.BiometricManager;
import androidx.biometric.BiometricPrompt;
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
import java.util.Collections;
import java.util.Date;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.concurrent.Executor;

public class MainActivity extends FragmentActivity {
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
    private Spinner contactSpinner;
    private Spinner statusSpinner;
    private Button lockButton;
    private ListView listView;
    private ArchiveMessageAdapter adapter;
    private final List<ArchiveMessage> visibleMessages = new ArrayList<>();
    private boolean updatingFilters = false;
    private boolean unlocked = false;

    private String pendingJson;
    private byte[] pendingEncryptedBackup;
    private byte[] pendingRestoreData;
    private File pendingSnapshot;
    private ArchiveMessage pendingGalleryMessage;

    private final BroadcastReceiver archiveReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
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
        contactSpinner = findViewById(R.id.contactSpinner);
        statusSpinner = findViewById(R.id.statusSpinner);
        lockButton = findViewById(R.id.lockButton);
        listView = findViewById(R.id.messageList);
    }

    private void configureUi() {
        adapter = new ArchiveMessageAdapter(this);
        listView.setAdapter(adapter);
        listView.setEmptyView(emptyState);

        Button openAccessButton = findViewById(R.id.openAccessButton);
        Button backupButton = findViewById(R.id.backupButton);
        Button restoreButton = findViewById(R.id.restoreButton);
        Button exportButton = findViewById(R.id.exportButton);
        Button deleteButton = findViewById(R.id.deleteButton);

        openAccessButton.setOnClickListener(v -> startActivity(new Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS)));
        lockButton.setOnClickListener(v -> showSecurityMenu());
        backupButton.setOnClickListener(v -> createEncryptedBackup());
        restoreButton.setOnClickListener(v -> chooseBackupToRestore());
        exportButton.setOnClickListener(v -> exportJson());
        deleteButton.setOnClickListener(v -> confirmClear());

        statusSpinner.setAdapter(new ArrayAdapter<>(this, android.R.layout.simple_spinner_dropdown_item,
                Arrays.asList("All records", "Possible deletion", "Notification still present", "WhatsApp Business")));

        searchBox.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            @Override public void onTextChanged(CharSequence s, int start, int before, int count) { if (!updatingFilters) refreshMessages(); }
            @Override public void afterTextChanged(Editable s) {}
        });

        contactSpinner.setOnItemSelectedListener(new SimpleItemSelectedListener(() -> {
            if (!updatingFilters) refreshMessages();
        }));
        statusSpinner.setOnItemSelectedListener(new SimpleItemSelectedListener(() -> {
            if (!updatingFilters) refreshMessages();
        }));

        listView.setOnItemClickListener((parent, view, position, id) -> {
            if (position >= 0 && position < visibleMessages.size()) showMessage(visibleMessages.get(position));
        });
    }

    @Override
    protected void onStart() {
        super.onStart();
        IntentFilter filter = new IntentFilter(WhatsAppNotificationListener.ACTION_ARCHIVE_UPDATED);
        if (Build.VERSION.SDK_INT >= 33) registerReceiver(archiveReceiver, filter, Context.RECEIVER_NOT_EXPORTED);
        else registerReceiver(archiveReceiver, filter);
    }

    @Override
    protected void onStop() {
        super.onStop();
        try { unregisterReceiver(archiveReceiver); } catch (Exception ignored) {}
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (unlocked) {
            updateAccessStatus();
            updateLockButton();
            refreshMessages();
        }
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
                ? "● Capture active — incoming WhatsApp notifications are archived locally"
                : "● Capture inactive — enable notification access to archive messages");
        accessStatus.setBackgroundResource(enabled ? R.drawable.bg_status_on : R.drawable.bg_status_off);
    }

    private void updateLockButton() {
        lockButton.setText(security.isLockEnabled() ? "Security ✓" : "Set app lock");
    }

    private void refreshMessages() {
        List<ArchiveMessage> all = db.getAll();
        String currentContact = selected(contactSpinner, "All contacts");
        String currentStatus = selected(statusSpinner, "All records");
        updateContactChoices(all, currentContact);

        String query = searchBox == null ? "" : searchBox.getText().toString().trim().toLowerCase(Locale.ROOT);
        visibleMessages.clear();
        int possibleCount = 0;
        SimpleDateFormat searchDate = new SimpleDateFormat("dd MMM yyyy HH:mm", Locale.getDefault());

        for (ArchiveMessage message : all) {
            if (message.isPossiblyDeleted()) possibleCount++;

            String selectedContact = selected(contactSpinner, "All contacts");
            if (!"All contacts".equals(selectedContact) && !selectedContact.equals(message.sender)) continue;

            String selectedStatus = selected(statusSpinner, "All records");
            if ("Possible deletion".equals(selectedStatus) && !message.isPossiblyDeleted()) continue;
            if ("Notification still present".equals(selectedStatus) && message.removedAt != null) continue;
            if ("WhatsApp Business".equals(selectedStatus) && !"com.whatsapp.w4b".equals(message.packageName)) continue;

            String source = "com.whatsapp.w4b".equals(message.packageName) ? "WhatsApp Business" : "WhatsApp";
            String haystack = (message.sender + "\n" + message.body + "\n" + source + "\n" +
                    searchDate.format(new Date(message.postedAt))).toLowerCase(Locale.ROOT);
            if (!query.isEmpty() && !haystack.contains(query)) continue;

            visibleMessages.add(message);
        }

        adapter.setItems(visibleMessages);
        archiveCount.setText(all.size() + " archived  •  " + possibleCount + " possible deletions  •  " + visibleMessages.size() + " shown");
    }

    private void updateContactChoices(List<ArchiveMessage> all, String preferred) {
        Set<String> set = new LinkedHashSet<>();
        for (ArchiveMessage message : all) {
            if (message.sender != null && !message.sender.trim().isEmpty()) set.add(message.sender.trim());
        }
        List<String> contacts = new ArrayList<>(set);
        Collections.sort(contacts, String.CASE_INSENSITIVE_ORDER);
        contacts.add(0, "All contacts");

        updatingFilters = true;
        ArrayAdapter<String> contactAdapter = new ArrayAdapter<>(this, android.R.layout.simple_spinner_dropdown_item, contacts);
        contactSpinner.setAdapter(contactAdapter);
        int index = contacts.indexOf(preferred);
        contactSpinner.setSelection(index >= 0 ? index : 0, false);
        updatingFilters = false;
    }

    private String selected(Spinner spinner, String fallback) {
        if (spinner == null || spinner.getSelectedItem() == null) return fallback;
        return spinner.getSelectedItem().toString();
    }

    private void showMessage(ArchiveMessage message) {
        SimpleDateFormat format = new SimpleDateFormat("dd MMMM yyyy, HH:mm:ss", Locale.getDefault());
        StringBuilder timeline = new StringBuilder();
        timeline.append("TIMELINE\n")
                .append("✓ Received: ").append(format.format(new Date(message.postedAt))).append("\n")
                .append("✓ Archived locally: immediately after notification capture\n");
        if (message.removedAt != null) {
            timeline.append("! Notification removed: ").append(format.format(new Date(message.removedAt))).append("\n")
                    .append("  Possible deletion — this can also happen when a notification is opened, dismissed or replaced.\n");
        } else {
            timeline.append("○ Notification removal: not observed\n");
        }

        String text = message.body + "\n\n" + timeline;
        SpannableString senderTitle = new SpannableString(message.sender == null ? "Unknown chat" : message.sender);
        senderTitle.setSpan(new ForegroundColorSpan(getColor(R.color.wa_sender_name)), 0, senderTitle.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        AlertDialog dialog = new AlertDialog.Builder(this)
                .setTitle(senderTitle)
                .setMessage(text)
                .setNegativeButton("Close", null)
                .setNeutralButton("Copy", (d, which) -> copyMessage(message))
                .setPositiveButton("Snapshot", null)
                .create();

        dialog.setOnShowListener(d -> dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v ->
                showSnapshotActions(message)));
        dialog.show();
    }

    private void copyMessage(ArchiveMessage message) {
        ClipboardManager clipboard = (ClipboardManager) getSystemService(CLIPBOARD_SERVICE);
        if (clipboard != null) {
            clipboard.setPrimaryClip(ClipData.newPlainText("WhatsArchive message", message.body));
            Toast.makeText(this, "Message copied", Toast.LENGTH_SHORT).show();
        }
    }

    private JSONObject buildArchiveJson() throws Exception {
        List<ArchiveMessage> all = db.getAll();
        JSONArray array = new JSONArray();
        for (ArchiveMessage message : all) {
            JSONObject obj = new JSONObject();
            obj.put("id", message.id);
            obj.put("packageName", message.packageName);
            obj.put("source", "com.whatsapp.w4b".equals(message.packageName) ? "WhatsApp Business" : "WhatsApp");
            obj.put("sender", message.sender);
            obj.put("message", message.body);
            obj.put("receivedAtEpochMs", message.postedAt);
            obj.put("notificationRemovedAtEpochMs", message.removedAt == null ? JSONObject.NULL : message.removedAt);
            obj.put("possibleDeletion", message.isPossiblyDeleted());
            obj.put("fingerprint", message.fingerprint == null ? "" : message.fingerprint);
            obj.put("notificationRemovalIsProofOfSenderDeletion", false);
            array.put(obj);
        }
        JSONObject root = new JSONObject();
        root.put("format", "whatsarchive-export");
        root.put("version", 2);
        root.put("developer", "Basil Okoth");
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
            intent.putExtra(Intent.EXTRA_TITLE, "whatsarchive-" + stamp() + ".json");
            startActivityForResult(intent, REQ_EXPORT_JSON);
        } catch (Exception e) {
            Toast.makeText(this, "Unable to prepare export", Toast.LENGTH_LONG).show();
        }
    }

    private void createEncryptedBackup() {
        showPasswordDialog("Create encrypted backup", true, password -> {
            try {
                byte[] plain = buildArchiveJson().toString().getBytes(StandardCharsets.UTF_8);
                pendingEncryptedBackup = BackupCrypto.encrypt(plain, password.toCharArray());
                Intent intent = new Intent(Intent.ACTION_CREATE_DOCUMENT);
                intent.addCategory(Intent.CATEGORY_OPENABLE);
                intent.setType("application/octet-stream");
                intent.putExtra(Intent.EXTRA_TITLE, "whatsarchive-secure-" + stamp() + ".wha");
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

    private void restoreEncryptedBackup(byte[] encrypted) {
        showPasswordDialog("Unlock encrypted backup", false, password -> {
            try {
                byte[] plain = BackupCrypto.decrypt(encrypted, password.toCharArray());
                int restored = restoreArchiveJson(new String(plain, StandardCharsets.UTF_8));
                Toast.makeText(this, restored + " message(s) restored; duplicates skipped", Toast.LENGTH_LONG).show();
                refreshMessages();
            } catch (Exception e) {
                Toast.makeText(this, "Could not decrypt backup. Check the password and file.", Toast.LENGTH_LONG).show();
            }
        });
    }

    private int restoreArchiveJson(String json) throws Exception {
        JSONObject root = new JSONObject(json);
        JSONArray messages = root.getJSONArray("messages");
        int restored = 0;
        for (int i = 0; i < messages.length(); i++) {
            JSONObject obj = messages.getJSONObject(i);
            String packageName = obj.optString("packageName", "com.whatsapp");
            String sender = obj.optString("sender", "Unknown chat");
            String body = obj.optString("message", "");
            long postedAt = obj.optLong("receivedAtEpochMs", System.currentTimeMillis());
            String fingerprint = obj.optString("fingerprint", "");
            if (fingerprint.isEmpty()) fingerprint = sha256(packageName + "|restore|" + sender + "|" + body + "|" + postedAt);
            long id = db.insertMessage(packageName, null, sender, body, postedAt, fingerprint);
            if (id <= 0) continue;

            if (!obj.isNull("notificationRemovedAtEpochMs")) {
                long removedAt = obj.optLong("notificationRemovedAtEpochMs", 0L);
                if (removedAt > 0) db.setRemovedAt(id, removedAt);
            }
            String snapshotPath = SnapshotRenderer.create(this, id, sender, body, postedAt, packageName);
            if (snapshotPath != null) db.setSnapshotPath(id, snapshotPath);
            restored++;
        }
        return restored;
    }

    private void showSnapshotActions(ArchiveMessage message) {
        String[] options = {"Save to Gallery", "Export to file"};
        new AlertDialog.Builder(this)
                .setTitle("Snapshot")
                .setItems(options, (dialog, which) -> {
                    if (which == 0) saveSnapshotToGallery(message);
                    else exportSnapshot(message);
                })
                .show();
    }

    private void saveSnapshotToGallery(ArchiveMessage message) {
        if (message == null || message.snapshotPath == null) {
            Toast.makeText(this, "No snapshot is available for this record", Toast.LENGTH_LONG).show();
            return;
        }
        File file = new File(message.snapshotPath);
        if (!file.exists()) {
            Toast.makeText(this, "Snapshot file is missing", Toast.LENGTH_LONG).show();
            return;
        }

        if (Build.VERSION.SDK_INT <= Build.VERSION_CODES.P &&
                checkSelfPermission(Manifest.permission.WRITE_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED) {
            pendingGalleryMessage = message;
            requestPermissions(new String[]{Manifest.permission.WRITE_EXTERNAL_STORAGE}, REQ_GALLERY_PERMISSION);
            return;
        }

        try {
            String name = "WhatsArchive_" + new SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US)
                    .format(new Date(message.postedAt)) + "_" + message.id + ".png";
            GallerySaver.savePng(this, file, name);
            Toast.makeText(this, "Snapshot saved to Gallery → Pictures/WhatsArchive", Toast.LENGTH_LONG).show();
        } catch (Exception e) {
            Toast.makeText(this, "Could not save snapshot to Gallery: " + e.getMessage(), Toast.LENGTH_LONG).show();
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

    private void showSecurityMenu() {
        if (!security.isLockEnabled()) {
            showPinSetupDialog("Create app PIN");
            return;
        }
        String[] options = {"Lock now", "Change PIN", "Disable app lock"};
        new AlertDialog.Builder(this)
                .setTitle("Archive security")
                .setItems(options, (dialog, which) -> {
                    if (which == 0) {
                        unlocked = false;
                        premiumRoot.setVisibility(View.INVISIBLE);
                        requestUnlock();
                    } else if (which == 1) {
                        showPinVerification("Enter current PIN", () -> showPinSetupDialog("Create new PIN"));
                    } else {
                        showPinVerification("Enter PIN to disable lock", () -> {
                            security.setLockEnabled(false);
                            updateLockButton();
                            Toast.makeText(this, "App lock disabled", Toast.LENGTH_SHORT).show();
                        });
                    }
                })
                .show();
    }

    private void requestUnlock() {
        if (!security.isLockEnabled()) {
            completeUnlock();
            return;
        }
        int canAuthenticate = BiometricManager.from(this)
                .canAuthenticate(BiometricManager.Authenticators.BIOMETRIC_WEAK);
        if (canAuthenticate != BiometricManager.BIOMETRIC_SUCCESS) {
            showPinUnlockDialog();
            return;
        }

        Executor executor = command -> runOnUiThread(command);
        BiometricPrompt prompt = new BiometricPrompt(this, executor, new BiometricPrompt.AuthenticationCallback() {
            @Override
            public void onAuthenticationSucceeded(BiometricPrompt.AuthenticationResult result) {
                super.onAuthenticationSucceeded(result);
                completeUnlock();
            }

            @Override
            public void onAuthenticationError(int errorCode, CharSequence errString) {
                super.onAuthenticationError(errorCode, errString);
                if (errorCode == BiometricPrompt.ERROR_NEGATIVE_BUTTON) showPinUnlockDialog();
            }
        });

        BiometricPrompt.PromptInfo info = new BiometricPrompt.PromptInfo.Builder()
                .setTitle("Unlock WhatsArchive")
                .setSubtitle("Use biometrics or your WhatsArchive PIN")
                .setNegativeButtonText("Use PIN")
                .build();
        prompt.authenticate(info);
    }

    private void completeUnlock() {
        unlocked = true;
        premiumRoot.setVisibility(View.VISIBLE);
        updateAccessStatus();
        updateLockButton();
        refreshMessages();
    }

    private void showPinUnlockDialog() {
        final EditText input = pinField("PIN");
        AlertDialog dialog = new AlertDialog.Builder(this)
                .setTitle("Unlock with PIN")
                .setView(wrapFields(input))
                .setCancelable(false)
                .setNegativeButton("Exit", (d, w) -> finish())
                .setPositiveButton("Unlock", null)
                .create();
        dialog.setOnShowListener(d -> dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v -> {
            if (security.verifyPin(input.getText().toString())) {
                dialog.dismiss();
                completeUnlock();
            } else {
                input.setError("Incorrect PIN");
            }
        }));
        dialog.show();
    }

    private void showPinVerification(String title, Runnable onSuccess) {
        final EditText input = pinField("Current PIN");
        AlertDialog dialog = new AlertDialog.Builder(this)
                .setTitle(title)
                .setView(wrapFields(input))
                .setNegativeButton("Cancel", null)
                .setPositiveButton("Continue", null)
                .create();
        dialog.setOnShowListener(d -> dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v -> {
            if (security.verifyPin(input.getText().toString())) {
                dialog.dismiss();
                onSuccess.run();
            } else input.setError("Incorrect PIN");
        }));
        dialog.show();
    }

    private void showPinSetupDialog(String title) {
        final EditText first = pinField("4–8 digit PIN");
        final EditText second = pinField("Confirm PIN");
        AlertDialog dialog = new AlertDialog.Builder(this)
                .setTitle(title)
                .setMessage("This PIN protects access to your local archive. Biometrics will be offered automatically when available.")
                .setView(wrapFields(first, second))
                .setNegativeButton("Cancel", null)
                .setPositiveButton("Save PIN", null)
                .create();
        dialog.setOnShowListener(d -> dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v -> {
            String p1 = first.getText().toString();
            String p2 = second.getText().toString();
            if (!p1.matches("\\d{4,8}")) {
                first.setError("Use 4–8 digits");
                return;
            }
            if (!p1.equals(p2)) {
                second.setError("PINs do not match");
                return;
            }
            if (security.setPin(p1)) {
                dialog.dismiss();
                updateLockButton();
                Toast.makeText(this, "App lock enabled", Toast.LENGTH_SHORT).show();
            } else Toast.makeText(this, "Could not save PIN", Toast.LENGTH_LONG).show();
        }));
        dialog.show();
    }

    private void showPasswordDialog(String title, boolean confirm, PasswordConsumer consumer) {
        final EditText first = passwordField("Backup password");
        final EditText second = confirm ? passwordField("Confirm password") : null;
        AlertDialog dialog = new AlertDialog.Builder(this)
                .setTitle(title)
                .setMessage(confirm
                        ? "Use a strong password. The backup can be restored on another device only with this password."
                        : "Enter the password used when this backup was created.")
                .setView(confirm ? wrapFields(first, second) : wrapFields(first))
                .setNegativeButton("Cancel", null)
                .setPositiveButton(confirm ? "Encrypt" : "Unlock", null)
                .create();
        dialog.setOnShowListener(d -> dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v -> {
            String password = first.getText().toString();
            if (password.length() < 8) {
                first.setError("Use at least 8 characters");
                return;
            }
            if (confirm && !password.equals(second.getText().toString())) {
                second.setError("Passwords do not match");
                return;
            }
            dialog.dismiss();
            consumer.accept(password);
        }));
        dialog.show();
    }

    private EditText pinField(String hint) {
        EditText field = new EditText(this);
        field.setHint(hint);
        field.setInputType(InputType.TYPE_CLASS_NUMBER | InputType.TYPE_NUMBER_VARIATION_PASSWORD);
        field.setSingleLine(true);
        return field;
    }

    private EditText passwordField(String hint) {
        EditText field = new EditText(this);
        field.setHint(hint);
        field.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PASSWORD);
        field.setSingleLine(true);
        return field;
    }

    private LinearLayout wrapFields(EditText... fields) {
        LinearLayout layout = new LinearLayout(this);
        layout.setOrientation(LinearLayout.VERTICAL);
        int p = dp(20);
        layout.setPadding(p, dp(8), p, 0);
        for (EditText field : fields) layout.addView(field);
        return layout;
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }

    private void confirmClear() {
        new AlertDialog.Builder(this)
                .setTitle("Clear local archive?")
                .setMessage("This permanently deletes the encrypted local message database and generated snapshots. Export a backup first if you may need the records later.")
                .setNegativeButton("Cancel", null)
                .setPositiveButton("Delete everything", (dialog, which) -> {
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
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == REQ_GALLERY_PERMISSION && pendingGalleryMessage != null) {
            ArchiveMessage message = pendingGalleryMessage;
            pendingGalleryMessage = null;
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                saveSnapshotToGallery(message);
            } else {
                Toast.makeText(this, "Storage permission is required to save Gallery snapshots on Android 8/9", Toast.LENGTH_LONG).show();
            }
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (resultCode != RESULT_OK || data == null || data.getData() == null) return;
        Uri uri = data.getData();

        try {
            if (requestCode == REQ_EXPORT_JSON && pendingJson != null) {
                writeBytes(uri, pendingJson.getBytes(StandardCharsets.UTF_8));
                pendingJson = null;
                Toast.makeText(this, "JSON archive exported", Toast.LENGTH_SHORT).show();
            } else if (requestCode == REQ_EXPORT_BACKUP && pendingEncryptedBackup != null) {
                writeBytes(uri, pendingEncryptedBackup);
                pendingEncryptedBackup = null;
                Toast.makeText(this, "Encrypted backup saved", Toast.LENGTH_SHORT).show();
            } else if (requestCode == REQ_RESTORE_BACKUP) {
                pendingRestoreData = readBytes(uri);
                if (looksLikeJson(pendingRestoreData)) {
                    int restored = restoreArchiveJson(new String(pendingRestoreData, StandardCharsets.UTF_8));
                    Toast.makeText(this, restored + " message(s) imported; duplicates skipped", Toast.LENGTH_LONG).show();
                    refreshMessages();
                } else {
                    restoreEncryptedBackup(pendingRestoreData);
                }
                pendingRestoreData = null;
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
            Toast.makeText(this, "File operation failed: " + e.getMessage(), Toast.LENGTH_LONG).show();
        }
    }

    private boolean looksLikeJson(byte[] data) {
        if (data == null) return false;
        int i = 0;
        while (i < data.length && Character.isWhitespace((char) data[i])) i++;
        return i < data.length && data[i] == '{';
    }

    private void writeBytes(Uri uri, byte[] bytes) throws Exception {
        try (OutputStream out = getContentResolver().openOutputStream(uri)) {
            if (out == null) throw new IllegalStateException("No output stream");
            out.write(bytes);
        }
    }

    private byte[] readBytes(Uri uri) throws Exception {
        try (InputStream in = getContentResolver().openInputStream(uri);
             ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            if (in == null) throw new IllegalStateException("No input stream");
            byte[] buffer = new byte[8192];
            int read;
            while ((read = in.read(buffer)) != -1) out.write(buffer, 0, read);
            return out.toByteArray();
        }
    }

    private String stamp() {
        return new SimpleDateFormat("yyyyMMdd-HHmmss", Locale.US).format(new Date());
    }

    private String sha256(String value) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(value.getBytes(StandardCharsets.UTF_8));
            StringBuilder hex = new StringBuilder();
            for (byte b : hash) hex.append(String.format("%02x", b));
            return hex.toString();
        } catch (Exception e) {
            return Integer.toHexString(value.hashCode());
        }
    }

    private interface PasswordConsumer {
        void accept(String password);
    }
}
