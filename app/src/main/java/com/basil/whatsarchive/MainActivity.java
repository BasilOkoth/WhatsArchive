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
import android.view.ViewGroup;
import android.view.Window;
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
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
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
    private Spinner statusSpinner;
    private Button lockButton;
    private ListView contactList;
    private ContactSummaryAdapter contactAdapter;
    private final List<ContactSummary> visibleContacts = new ArrayList<>();
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
        statusSpinner = findViewById(R.id.statusSpinner);
        lockButton = findViewById(R.id.lockButton);
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

        openAccessButton.setOnClickListener(
                v -> startActivity(new Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS)));
        lockButton.setOnClickListener(v -> showSecurityMenu());
        backupButton.setOnClickListener(v -> createEncryptedBackup());
        restoreButton.setOnClickListener(v -> chooseBackupToRestore());
        exportButton.setOnClickListener(v -> exportJson());
        deleteButton.setOnClickListener(v -> confirmClear());

        statusSpinner.setAdapter(new ArrayAdapter<>(
                this,
                android.R.layout.simple_spinner_dropdown_item,
                Arrays.asList(
                        "All records",
                        "Possible deletion",
                        "Notification still present",
                        "WhatsApp Business"
                )
        ));
        statusSpinner.setOnItemSelectedListener(
                new SimpleItemSelectedListener(this::refreshMessages));

        searchBox.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            @Override public void onTextChanged(CharSequence s, int start, int before, int count) {
                refreshMessages();
            }
            @Override public void afterTextChanged(Editable s) {}
        });

        contactList.setOnItemClickListener((parent, view, position, id) -> {
            if (position >= 0 && position < visibleContacts.size()) {
                ContactSummary summary = visibleContacts.get(position);
                showContactThread(summary.conversationId, summary.sender);
            }
        });
    }

    @Override
    protected void onStart() {
        super.onStart();
        IntentFilter filter =
                new IntentFilter(WhatsAppNotificationListener.ACTION_ARCHIVE_UPDATED);
        if (Build.VERSION.SDK_INT >= 33) {
            registerReceiver(
                    archiveReceiver,
                    filter,
                    Context.RECEIVER_NOT_EXPORTED
            );
        } else {
            registerReceiver(archiveReceiver, filter);
        }
    }

    @Override
    protected void onStop() {
        super.onStop();
        try {
            unregisterReceiver(archiveReceiver);
        } catch (Exception ignored) {
        }
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
        NotificationManager manager =
                (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
        ComponentName componentName =
                new ComponentName(this, WhatsAppNotificationListener.class);

        boolean enabled;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
            enabled = manager != null &&
                    manager.isNotificationListenerAccessGranted(componentName);
        } else {
            String listeners = Settings.Secure.getString(
                    getContentResolver(),
                    "enabled_notification_listeners"
            );
            enabled = listeners != null &&
                    listeners.contains(getPackageName());
        }

        accessStatus.setText(enabled
                ? "● Capture active — incoming WhatsApp notifications are archived locally"
                : "● Capture inactive — enable notification access to archive messages");
        accessStatus.setBackgroundResource(
                enabled ? R.drawable.bg_status_on : R.drawable.bg_status_off);
    }

    private void updateLockButton() {
        lockButton.setText(
                security.isLockEnabled() ? "Security ✓" : "Set app lock");
    }

    private void refreshMessages() {
        if (db == null || statusSpinner == null || contactAdapter == null) {
            return;
        }

        List<ArchiveMessage> all = db.getAll();

        int possibleCount = 0;
        for (ArchiveMessage message : all) {
            if (message.isPossiblyDeleted()) possibleCount++;
        }

        String selectedStatus =
                selected(statusSpinner, "All records");
        String query = searchBox == null
                ? ""
                : searchBox.getText().toString()
                .trim()
                .toLowerCase(Locale.ROOT);

        SimpleDateFormat searchDate =
                new SimpleDateFormat(
                        "dd MMM yyyy HH:mm",
                        Locale.getDefault()
                );

        /*
         * IMPORTANT v0.3.2 FIX:
         * Group by hidden conversationId, not visible sender/group name.
         * This keeps members of one group together, while two different
         * groups that happen to have the same visible name remain separate.
         */
        Map<String, List<ArchiveMessage>> grouped =
                new LinkedHashMap<>();

        for (ArchiveMessage message : all) {
            if (!matchesStatus(message, selectedStatus)) continue;

            String conversationId = conversationKey(message);
            grouped.computeIfAbsent(
                    conversationId,
                    k -> new ArrayList<>()
            ).add(message);
        }

        visibleContacts.clear();

        for (Map.Entry<String, List<ArchiveMessage>> entry
                : grouped.entrySet()) {
            List<ArchiveMessage> messages = entry.getValue();
            if (messages.isEmpty()) continue;

            ArchiveMessage latest = messages.get(0);
            String conversationName = conversationLabel(latest);

            boolean searchMatches =
                    query.isEmpty() ||
                            conversationName
                                    .toLowerCase(Locale.ROOT)
                                    .contains(query);

            if (!searchMatches) {
                for (ArchiveMessage message : messages) {
                    String source =
                            "com.whatsapp.w4b".equals(message.packageName)
                                    ? "WhatsApp Business"
                                    : "WhatsApp";

                    String haystack =
                            (safe(message.body) + "\n" +
                                    safe(message.participantName) + "\n" +
                                    conversationLabel(message) + "\n" +
                                    source + "\n" +
                                    searchDate.format(
                                            new Date(message.postedAt)))
                                    .toLowerCase(Locale.ROOT);

                    if (haystack.contains(query)) {
                        searchMatches = true;
                        break;
                    }
                }
            }

            if (!searchMatches) continue;

            ContactSummary summary = new ContactSummary();
            summary.conversationId = entry.getKey();
            summary.sender = conversationName;
            summary.latestBody = safe(latest.body);
            summary.latestAt = latest.postedAt;
            summary.messageCount = messages.size();
            summary.packageName = latest.packageName;

            int deleted = 0;
            for (ArchiveMessage message : messages) {
                if (message.isPossiblyDeleted()) deleted++;
            }
            summary.possibleDeletionCount = deleted;

            visibleContacts.add(summary);
        }

        // If two different conversations have the same visible name,
        // keep them separate and show a short local identity hint so the
        // user can tell that they are not the same chat.
        Map<String, Integer> visibleNameCounts = new LinkedHashMap<>();
        for (ContactSummary summary : visibleContacts) {
            String normalizedName = safe(summary.sender)
                    .trim()
                    .toLowerCase(Locale.ROOT);
            visibleNameCounts.put(
                    normalizedName,
                    visibleNameCounts.getOrDefault(normalizedName, 0) + 1
            );
        }

        for (ContactSummary summary : visibleContacts) {
            String normalizedName = safe(summary.sender)
                    .trim()
                    .toLowerCase(Locale.ROOT);

            if (visibleNameCounts.getOrDefault(normalizedName, 0) > 1) {
                summary.identityHint =
                        "Separate chat • ID " +
                                shortConversationId(summary.conversationId);
            } else {
                summary.identityHint = "";
            }
        }

        contactAdapter.setItems(visibleContacts);
        archiveCount.setText(
                all.size() + " messages  •  " +
                        visibleContacts.size() +
                        " conversations shown  •  " +
                        possibleCount +
                        " possible deletions"
        );
    }

    private boolean matchesStatus(
            ArchiveMessage message,
            String status) {
        if ("Possible deletion".equals(status)) {
            return message.isPossiblyDeleted();
        }
        if ("Notification still present".equals(status)) {
            return message.removedAt == null;
        }
        if ("WhatsApp Business".equals(status)) {
            return "com.whatsapp.w4b".equals(message.packageName);
        }
        return true;
    }

    private String conversationKey(ArchiveMessage message) {
        String stored = safe(message.conversationId).trim();
        if (!stored.isEmpty()) return stored;

        String key = safe(message.notificationKey).trim();
        if (!key.isEmpty()) {
            return "legacy-key:" +
                    sha256(safe(message.packageName) + "|" + key);
        }

        return "legacy-name:" +
                sha256(
                        safe(message.packageName) + "|" +
                                conversationLabel(message)
                                        .toLowerCase(Locale.ROOT)
                );
    }

    private String conversationLabel(ArchiveMessage message) {
        if (message == null) return "Unknown chat";

        String name = safe(message.conversationName).trim();
        if (!name.isEmpty()) return name;

        String sender = safe(message.sender).trim();
        if (sender.isEmpty()) return "Unknown chat";

        int split = sender.indexOf(" — ");
        return split > 0
                ? sender.substring(0, split).trim()
                : sender;
    }

    private String participantLabel(ArchiveMessage message) {
        if (message == null) return "";

        String participant =
                safe(message.participantName).trim();
        if (!participant.isEmpty()) return participant;

        String sender = safe(message.sender).trim();
        int split = sender.indexOf(" — ");
        if (split > 0 && split + 3 < sender.length()) {
            return sender.substring(split + 3).trim();
        }

        return "";
    }

    private String messageTitle(ArchiveMessage message) {
        String conversation = conversationLabel(message);
        String participant = participantLabel(message);

        if (!participant.isEmpty() &&
                !participant.equalsIgnoreCase(conversation)) {
            return participant + " • " + conversation;
        }

        return conversation;
    }

    private String shortConversationId(String conversationId) {
        String digest = sha256(safe(conversationId));
        return digest.substring(0, Math.min(4, digest.length()))
                .toUpperCase(Locale.ROOT);
    }

    private String selected(
            Spinner spinner,
            String fallback) {
        if (spinner == null ||
                spinner.getSelectedItem() == null) {
            return fallback;
        }
        return spinner.getSelectedItem().toString();
    }

    private String safe(String value) {
        return value == null ? "" : value;
    }

    private void showContactThread(
            String conversationId,
            String conversationName) {
        List<ArchiveMessage> messages =
                new ArrayList<>();

        for (ArchiveMessage message : db.getAll()) {
            if (conversationKey(message)
                    .equals(conversationId)) {
                messages.add(message);
            }
        }

        if (messages.isEmpty()) {
            Toast.makeText(
                    this,
                    "No archived messages for this conversation",
                    Toast.LENGTH_SHORT
            ).show();
            return;
        }

        LinearLayout container =
                new LinearLayout(this);
        container.setOrientation(LinearLayout.VERTICAL);
        container.setPadding(
                dp(10), dp(6), dp(10), 0);

        TextView summary = new TextView(this);
        summary.setText(
                messages.size() +
                        (messages.size() == 1
                                ? " archived message"
                                : " archived messages") +
                        " • messages stay under this conversation"
        );
        summary.setTextColor(
                getColor(R.color.wa_text_muted));
        summary.setTextSize(12f);
        summary.setPadding(
                dp(8), dp(2), dp(8), dp(8));
        container.addView(summary);

        ListView threadList = new ListView(this);
        threadList.setDividerHeight(dp(8));
        threadList.setDivider(null);
        threadList.setCacheColorHint(
                getColor(android.R.color.transparent));
        threadList.setPadding(0, 0, 0, dp(4));

        ArchiveMessageAdapter threadAdapter =
                new ArchiveMessageAdapter(
                        this,
                        new ArchiveMessageAdapter.ActionListener() {
                            @Override
                            public void onOpen(
                                    ArchiveMessage message) {
                                showMessage(message);
                            }

                            @Override
                            public void onShare(
                                    ArchiveMessage message) {
                                shareMessage(message);
                            }

                            @Override
                            public void onSnapshot(
                                    ArchiveMessage message) {
                                showSnapshotActions(message);
                            }
                        }
                );

        threadAdapter.setItems(messages);
        threadList.setAdapter(threadAdapter);

        int threadHeight =
                (int) (getResources()
                        .getDisplayMetrics()
                        .heightPixels * 0.62f);

        container.addView(
                threadList,
                new LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        threadHeight
                )
        );

        String titleText =
                safe(conversationName).trim().isEmpty()
                        ? conversationLabel(messages.get(0))
                        : conversationName;

        SpannableString title =
                new SpannableString(titleText);
        title.setSpan(
                new ForegroundColorSpan(
                        getColor(R.color.wa_sender_name)),
                0,
                title.length(),
                Spanned.SPAN_EXCLUSIVE_EXCLUSIVE
        );

        AlertDialog dialog =
                new AlertDialog.Builder(this)
                        .setTitle(title)
                        .setView(container)
                        .setNegativeButton("Close", null)
                        .create();

        dialog.setOnShowListener(d -> {
            Window window = dialog.getWindow();
            if (window != null) {
                int height =
                        (int) (getResources()
                                .getDisplayMetrics()
                                .heightPixels * 0.82f);
                window.setLayout(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        height
                );
            }
        });

        dialog.show();
    }

    private void showMessage(ArchiveMessage message) {
        SimpleDateFormat format =
                new SimpleDateFormat(
                        "dd MMMM yyyy, HH:mm:ss",
                        Locale.getDefault()
                );

        StringBuilder timeline =
                new StringBuilder();

        timeline.append("TIMELINE\n")
                .append("✓ Received: ")
                .append(format.format(
                        new Date(message.postedAt)))
                .append("\n")
                .append("✓ Archived locally: immediately after notification capture\n");

        if (message.removedAt != null) {
            timeline.append("! Notification removed: ")
                    .append(format.format(
                            new Date(message.removedAt)))
                    .append("\n")
                    .append("Possible deletion — removal can also happen when a notification is opened, dismissed or replaced.\n");
        } else {
            timeline.append(
                    "○ Notification removal: not observed\n");
        }

        SpannableString senderTitle =
                new SpannableString(
                        messageTitle(message));

        senderTitle.setSpan(
                new ForegroundColorSpan(
                        getColor(R.color.wa_sender_name)),
                0,
                senderTitle.length(),
                Spanned.SPAN_EXCLUSIVE_EXCLUSIVE
        );

        String participant =
                participantLabel(message);
        String conversation =
                conversationLabel(message);

        StringBuilder detail =
                new StringBuilder();

        if (!participant.isEmpty() &&
                !participant.equalsIgnoreCase(conversation)) {
            detail.append("Conversation: ")
                    .append(conversation)
                    .append("\n\n");
        }

        detail.append(safe(message.body))
                .append("\n\n")
                .append(timeline);

        new AlertDialog.Builder(this)
                .setTitle(senderTitle)
                .setMessage(detail.toString())
                .setNegativeButton("Close", null)
                .setNeutralButton(
                        "Copy",
                        (d, which) ->
                                copyMessage(message))
                .setPositiveButton(
                        "Share",
                        (d, which) ->
                                shareMessage(message))
                .show();
    }

    private void copyMessage(
            ArchiveMessage message) {
        ClipboardManager clipboard =
                (ClipboardManager)
                        getSystemService(CLIPBOARD_SERVICE);

        if (clipboard != null) {
            clipboard.setPrimaryClip(
                    ClipData.newPlainText(
                            "ChatArchive message",
                            safe(message.body)
                    )
            );
            Toast.makeText(
                    this,
                    "Message copied",
                    Toast.LENGTH_SHORT
            ).show();
        }
    }

    private void shareMessage(
            ArchiveMessage message) {
        SimpleDateFormat format =
                new SimpleDateFormat(
                        "dd MMM yyyy, HH:mm",
                        Locale.getDefault()
                );

        String conversation =
                conversationLabel(message);
        String participant =
                participantLabel(message);

        StringBuilder shareText =
                new StringBuilder();

        shareText.append(conversation)
                .append("\n");

        if (!participant.isEmpty() &&
                !participant.equalsIgnoreCase(conversation)) {
            shareText.append("From: ")
                    .append(participant)
                    .append("\n");
        }

        shareText.append(safe(message.body))
                .append("\n\nReceived: ")
                .append(format.format(
                        new Date(message.postedAt)))
                .append("\nArchived locally with ChatArchive");

        Intent share =
                new Intent(Intent.ACTION_SEND);
        share.setType("text/plain");
        share.putExtra(
                Intent.EXTRA_TEXT,
                shareText.toString());

        try {
            startActivity(
                    Intent.createChooser(
                            share,
                            "Share archived message"
                    )
            );
        } catch (Exception e) {
            Toast.makeText(
                    this,
                    "No app available to share this message",
                    Toast.LENGTH_LONG
            ).show();
        }
    }

    private JSONObject buildArchiveJson()
            throws Exception {
        List<ArchiveMessage> all = db.getAll();
        JSONArray array = new JSONArray();

        for (ArchiveMessage message : all) {
            JSONObject obj = new JSONObject();
            obj.put("id", message.id);
            obj.put(
                    "packageName",
                    message.packageName);
            obj.put(
                    "source",
                    "com.whatsapp.w4b"
                            .equals(message.packageName)
                            ? "WhatsApp Business"
                            : "WhatsApp"
            );

            obj.put(
                    "notificationKey",
                    message.notificationKey == null
                            ? JSONObject.NULL
                            : message.notificationKey);
            obj.put(
                    "conversationId",
                    conversationKey(message));
            obj.put(
                    "conversationName",
                    conversationLabel(message));
            obj.put(
                    "participantName",
                    participantLabel(message));
            obj.put(
                    "sender",
                    message.sender == null
                            ? messageTitle(message)
                            : message.sender);
            obj.put(
                    "message",
                    message.body);
            obj.put(
                    "receivedAtEpochMs",
                    message.postedAt);
            obj.put(
                    "notificationRemovedAtEpochMs",
                    message.removedAt == null
                            ? JSONObject.NULL
                            : message.removedAt);
            obj.put(
                    "possibleDeletion",
                    message.isPossiblyDeleted());
            obj.put(
                    "fingerprint",
                    message.fingerprint == null
                            ? ""
                            : message.fingerprint);
            obj.put(
                    "notificationRemovalIsProofOfSenderDeletion",
                    false);

            array.put(obj);
        }

        JSONObject root = new JSONObject();
        root.put(
                "format",
                "chatarchive-export");
        root.put("version", 3);
        root.put(
                "developer",
                "Basil Okoth");
        root.put(
                "exportedAtEpochMs",
                System.currentTimeMillis());
        root.put("messages", array);

        return root;
    }

    private void exportJson() {
        try {
            pendingJson =
                    buildArchiveJson().toString(2);

            Intent intent =
                    new Intent(
                            Intent.ACTION_CREATE_DOCUMENT);
            intent.addCategory(
                    Intent.CATEGORY_OPENABLE);
            intent.setType(
                    "application/json");
            intent.putExtra(
                    Intent.EXTRA_TITLE,
                    "chatarchive-" +
                            stamp() +
                            ".json");

            startActivityForResult(
                    intent,
                    REQ_EXPORT_JSON);
        } catch (Exception e) {
            Toast.makeText(
                    this,
                    "Unable to prepare export",
                    Toast.LENGTH_LONG
            ).show();
        }
    }

    private void createEncryptedBackup() {
        showPasswordDialog(
                "Create encrypted backup",
                true,
                password -> {
                    try {
                        byte[] plain =
                                buildArchiveJson()
                                        .toString()
                                        .getBytes(
                                                StandardCharsets.UTF_8);

                        pendingEncryptedBackup =
                                BackupCrypto.encrypt(
                                        plain,
                                        password.toCharArray()
                                );

                        Intent intent =
                                new Intent(
                                        Intent.ACTION_CREATE_DOCUMENT);
                        intent.addCategory(
                                Intent.CATEGORY_OPENABLE);
                        intent.setType(
                                "application/octet-stream");
                        intent.putExtra(
                                Intent.EXTRA_TITLE,
                                "chatarchive-secure-" +
                                        stamp() +
                                        ".wha");

                        startActivityForResult(
                                intent,
                                REQ_EXPORT_BACKUP);
                    } catch (Exception e) {
                        Toast.makeText(
                                this,
                                "Backup encryption failed",
                                Toast.LENGTH_LONG
                        ).show();
                    }
                }
        );
    }

    private void chooseBackupToRestore() {
        Intent intent =
                new Intent(
                        Intent.ACTION_OPEN_DOCUMENT);
        intent.addCategory(
                Intent.CATEGORY_OPENABLE);
        intent.setType("*/*");
        startActivityForResult(
                intent,
                REQ_RESTORE_BACKUP);
    }

    private void restoreEncryptedBackup(
            byte[] encrypted) {
        showPasswordDialog(
                "Unlock encrypted backup",
                false,
                password -> {
                    try {
                        byte[] plain =
                                BackupCrypto.decrypt(
                                        encrypted,
                                        password.toCharArray()
                                );

                        int restored =
                                restoreArchiveJson(
                                        new String(
                                                plain,
                                                StandardCharsets.UTF_8
                                        )
                                );

                        Toast.makeText(
                                this,
                                restored +
                                        " message(s) restored; duplicates skipped",
                                Toast.LENGTH_LONG
                        ).show();

                        refreshMessages();
                    } catch (Exception e) {
                        Toast.makeText(
                                this,
                                "Could not decrypt backup. Check the password and file.",
                                Toast.LENGTH_LONG
                        ).show();
                    }
                }
        );
    }

    private int restoreArchiveJson(
            String json) throws Exception {
        JSONObject root =
                new JSONObject(json);
        JSONArray messages =
                root.getJSONArray("messages");

        int restored = 0;

        for (int i = 0;
             i < messages.length();
             i++) {
            JSONObject obj =
                    messages.getJSONObject(i);

            String packageName =
                    obj.optString(
                            "packageName",
                            "com.whatsapp");

            String notificationKey =
                    obj.isNull("notificationKey")
                            ? null
                            : obj.optString(
                                    "notificationKey",
                                    null);

            String sender =
                    obj.optString(
                            "sender",
                            "Unknown chat");

            String conversationName =
                    obj.optString(
                            "conversationName",
                            legacyConversationName(sender));

            String participantName =
                    obj.optString(
                            "participantName",
                            legacyParticipantName(sender));

            String conversationId =
                    obj.optString(
                            "conversationId",
                            "");

            if (conversationId.trim().isEmpty()) {
                if (notificationKey != null &&
                        !notificationKey.trim().isEmpty()) {
                    conversationId =
                            "restore-key:" +
                                    sha256(
                                            packageName +
                                                    "|" +
                                                    notificationKey
                                    );
                } else {
                    conversationId =
                            "restore-name:" +
                                    sha256(
                                            packageName +
                                                    "|" +
                                                    conversationName
                                                            .toLowerCase(
                                                                    Locale.ROOT)
                                    );
                }
            }

            String body =
                    obj.optString(
                            "message",
                            "");

            long postedAt =
                    obj.optLong(
                            "receivedAtEpochMs",
                            System.currentTimeMillis());

            String fingerprint =
                    obj.optString(
                            "fingerprint",
                            "");

            if (fingerprint.isEmpty()) {
                fingerprint =
                        sha256(
                                packageName +
                                        "|restore|" +
                                        conversationId +
                                        "|" +
                                        participantName +
                                        "|" +
                                        body +
                                        "|" +
                                        postedAt
                        );
            }

            long id =
                    db.insertMessage(
                            packageName,
                            notificationKey,
                            conversationId,
                            conversationName,
                            participantName,
                            sender,
                            body,
                            postedAt,
                            fingerprint
                    );

            if (id <= 0) continue;

            if (!obj.isNull(
                    "notificationRemovedAtEpochMs")) {
                long removedAt =
                        obj.optLong(
                                "notificationRemovedAtEpochMs",
                                0L);

                if (removedAt > 0) {
                    db.setRemovedAt(
                            id,
                            removedAt);
                }
            }

            ArchiveMessage restoredMessage =
                    new ArchiveMessage();
            restoredMessage.conversationName =
                    conversationName;
            restoredMessage.participantName =
                    participantName;
            restoredMessage.sender = sender;

            String snapshotPath =
                    SnapshotRenderer.create(
                            this,
                            id,
                            messageTitle(restoredMessage),
                            body,
                            postedAt,
                            packageName
                    );

            if (snapshotPath != null) {
                db.setSnapshotPath(
                        id,
                        snapshotPath);
            }

            restored++;
        }

        return restored;
    }

    private String legacyConversationName(
            String sender) {
        String value = safe(sender).trim();
        if (value.isEmpty()) return "Unknown chat";

        int split =
                value.indexOf(" — ");

        return split > 0
                ? value.substring(
                        0,
                        split).trim()
                : value;
    }

    private String legacyParticipantName(
            String sender) {
        String value = safe(sender).trim();
        int split =
                value.indexOf(" — ");

        if (split > 0 &&
                split + 3 < value.length()) {
            return value
                    .substring(split + 3)
                    .trim();
        }

        return "";
    }

    private void showSnapshotActions(
            ArchiveMessage message) {
        String[] options = {
                "Save snapshot to Gallery",
                "Export snapshot PNG to file"
        };

        new AlertDialog.Builder(this)
                .setTitle("Snapshot / Export")
                .setItems(
                        options,
                        (dialog, which) -> {
                            if (which == 0) {
                                saveSnapshotToGallery(
                                        message);
                            } else {
                                exportSnapshot(
                                        message);
                            }
                        }
                )
                .show();
    }

    private void saveSnapshotToGallery(
            ArchiveMessage message) {
        if (message == null ||
                message.snapshotPath == null) {
            Toast.makeText(
                    this,
                    "No snapshot is available for this record",
                    Toast.LENGTH_LONG
            ).show();
            return;
        }

        File file =
                new File(message.snapshotPath);

        if (!file.exists()) {
            Toast.makeText(
                    this,
                    "Snapshot file is missing",
                    Toast.LENGTH_LONG
            ).show();
            return;
        }

        if (Build.VERSION.SDK_INT <=
                Build.VERSION_CODES.P &&
                checkSelfPermission(
                        Manifest.permission
                                .WRITE_EXTERNAL_STORAGE)
                        != PackageManager
                        .PERMISSION_GRANTED) {
            pendingGalleryMessage = message;
            requestPermissions(
                    new String[]{
                            Manifest.permission
                                    .WRITE_EXTERNAL_STORAGE
                    },
                    REQ_GALLERY_PERMISSION
            );
            return;
        }

        try {
            String name =
                    "ChatArchive_" +
                            new SimpleDateFormat(
                                    "yyyyMMdd_HHmmss",
                                    Locale.US)
                                    .format(
                                            new Date(
                                                    message.postedAt)) +
                            "_" +
                            message.id +
                            ".png";

            GallerySaver.savePng(
                    this,
                    file,
                    name);

            Toast.makeText(
                    this,
                    "Snapshot saved to Gallery → Pictures/ChatArchive",
                    Toast.LENGTH_LONG
            ).show();
        } catch (Exception e) {
            Toast.makeText(
                    this,
                    "Could not save snapshot to Gallery: " +
                            e.getMessage(),
                    Toast.LENGTH_LONG
            ).show();
        }
    }

    private void exportSnapshot(
            ArchiveMessage message) {
        if (message == null ||
                message.snapshotPath == null) {
            Toast.makeText(
                    this,
                    "No snapshot is available for this record",
                    Toast.LENGTH_LONG
            ).show();
            return;
        }

        File file =
                new File(message.snapshotPath);

        if (!file.exists()) {
            Toast.makeText(
                    this,
                    "Snapshot file is missing",
                    Toast.LENGTH_LONG
            ).show();
            return;
        }

        pendingSnapshot = file;

        Intent intent =
                new Intent(
                        Intent.ACTION_CREATE_DOCUMENT);
        intent.addCategory(
                Intent.CATEGORY_OPENABLE);
        intent.setType("image/png");
        intent.putExtra(
                Intent.EXTRA_TITLE,
                "chatarchive-message-" +
                        message.id +
                        ".png");

        startActivityForResult(
                intent,
                REQ_EXPORT_PNG);
    }

    private void showSecurityMenu() {
        if (!security.isLockEnabled()) {
            showPinSetupDialog(
                    "Create app PIN");
            return;
        }

        String[] options = {
                "Lock now",
                "Change PIN",
                "Disable app lock"
        };

        new AlertDialog.Builder(this)
                .setTitle("Archive security")
                .setItems(
                        options,
                        (dialog, which) -> {
                            if (which == 0) {
                                unlocked = false;
                                premiumRoot.setVisibility(
                                        View.INVISIBLE);
                                requestUnlock();
                            } else if (which == 1) {
                                showPinVerification(
                                        "Enter current PIN",
                                        () ->
                                                showPinSetupDialog(
                                                        "Create new PIN")
                                );
                            } else {
                                showPinVerification(
                                        "Enter PIN to disable lock",
                                        () -> {
                                            security
                                                    .setLockEnabled(
                                                            false);
                                            updateLockButton();
                                            Toast.makeText(
                                                    this,
                                                    "App lock disabled",
                                                    Toast.LENGTH_SHORT
                                            ).show();
                                        }
                                );
                            }
                        }
                )
                .show();
    }

    private void requestUnlock() {
        if (!security.isLockEnabled()) {
            completeUnlock();
            return;
        }

        int canAuthenticate =
                BiometricManager
                        .from(this)
                        .canAuthenticate(
                                BiometricManager
                                        .Authenticators
                                        .BIOMETRIC_WEAK);

        if (canAuthenticate !=
                BiometricManager
                        .BIOMETRIC_SUCCESS) {
            showPinUnlockDialog();
            return;
        }

        Executor executor =
                command ->
                        runOnUiThread(command);

        BiometricPrompt prompt =
                new BiometricPrompt(
                        this,
                        executor,
                        new BiometricPrompt
                                .AuthenticationCallback() {
                            @Override
                            public void onAuthenticationSucceeded(
                                    BiometricPrompt
                                            .AuthenticationResult result) {
                                super
                                        .onAuthenticationSucceeded(
                                                result);
                                completeUnlock();
                            }

                            @Override
                            public void onAuthenticationError(
                                    int errorCode,
                                    CharSequence errString) {
                                super
                                        .onAuthenticationError(
                                                errorCode,
                                                errString);

                                if (errorCode ==
                                        BiometricPrompt
                                                .ERROR_NEGATIVE_BUTTON) {
                                    showPinUnlockDialog();
                                }
                            }
                        }
                );

        BiometricPrompt.PromptInfo info =
                new BiometricPrompt
                        .PromptInfo.Builder()
                        .setTitle(
                                "Unlock ChatArchive")
                        .setSubtitle(
                                "Use biometrics or your ChatArchive PIN")
                        .setNegativeButtonText(
                                "Use PIN")
                        .build();

        prompt.authenticate(info);
    }

    private void completeUnlock() {
        unlocked = true;
        premiumRoot.setVisibility(
                View.VISIBLE);
        updateAccessStatus();
        updateLockButton();
        refreshMessages();
    }

    private void showPinUnlockDialog() {
        final EditText input =
                pinField("PIN");

        AlertDialog dialog =
                new AlertDialog.Builder(this)
                        .setTitle(
                                "Unlock with PIN")
                        .setView(
                                wrapFields(input))
                        .setCancelable(false)
                        .setNegativeButton(
                                "Exit",
                                (d, w) -> finish())
                        .setPositiveButton(
                                "Unlock",
                                null)
                        .create();

        dialog.setOnShowListener(
                d ->
                        dialog.getButton(
                                        AlertDialog
                                                .BUTTON_POSITIVE)
                                .setOnClickListener(
                                        v -> {
                                            if (security
                                                    .verifyPin(
                                                            input
                                                                    .getText()
                                                                    .toString())) {
                                                dialog.dismiss();
                                                completeUnlock();
                                            } else {
                                                input.setError(
                                                        "Incorrect PIN");
                                            }
                                        }
                                )
        );

        dialog.show();
    }

    private void showPinVerification(
            String title,
            Runnable onSuccess) {
        final EditText input =
                pinField("Current PIN");

        AlertDialog dialog =
                new AlertDialog.Builder(this)
                        .setTitle(title)
                        .setView(
                                wrapFields(input))
                        .setNegativeButton(
                                "Cancel",
                                null)
                        .setPositiveButton(
                                "Continue",
                                null)
                        .create();

        dialog.setOnShowListener(
                d ->
                        dialog.getButton(
                                        AlertDialog
                                                .BUTTON_POSITIVE)
                                .setOnClickListener(
                                        v -> {
                                            if (security
                                                    .verifyPin(
                                                            input
                                                                    .getText()
                                                                    .toString())) {
                                                dialog.dismiss();
                                                onSuccess.run();
                                            } else {
                                                input.setError(
                                                        "Incorrect PIN");
                                            }
                                        }
                                )
        );

        dialog.show();
    }

    private void showPinSetupDialog(
            String title) {
        final EditText first =
                pinField("4–8 digit PIN");
        final EditText second =
                pinField("Confirm PIN");

        AlertDialog dialog =
                new AlertDialog.Builder(this)
                        .setTitle(title)
                        .setMessage(
                                "This PIN protects access to your local archive. Biometrics will be offered automatically when available.")
                        .setView(
                                wrapFields(
                                        first,
                                        second))
                        .setNegativeButton(
                                "Cancel",
                                null)
                        .setPositiveButton(
                                "Save PIN",
                                null)
                        .create();

        dialog.setOnShowListener(
                d ->
                        dialog.getButton(
                                        AlertDialog
                                                .BUTTON_POSITIVE)
                                .setOnClickListener(
                                        v -> {
                                            String p1 =
                                                    first
                                                            .getText()
                                                            .toString();

                                            String p2 =
                                                    second
                                                            .getText()
                                                            .toString();

                                            if (!p1.matches(
                                                    "\\d{4,8}")) {
                                                first.setError(
                                                        "Use 4–8 digits");
                                                return;
                                            }

                                            if (!p1.equals(p2)) {
                                                second.setError(
                                                        "PINs do not match");
                                                return;
                                            }

                                            if (security
                                                    .setPin(p1)) {
                                                dialog.dismiss();
                                                updateLockButton();

                                                Toast.makeText(
                                                        this,
                                                        "App lock enabled",
                                                        Toast.LENGTH_SHORT
                                                ).show();
                                            } else {
                                                Toast.makeText(
                                                        this,
                                                        "Could not save PIN",
                                                        Toast.LENGTH_LONG
                                                ).show();
                                            }
                                        }
                                )
        );

        dialog.show();
    }

    private void showPasswordDialog(
            String title,
            boolean confirm,
            PasswordConsumer consumer) {
        final EditText first =
                passwordField(
                        "Backup password");

        final EditText second =
                confirm
                        ? passwordField(
                                "Confirm password")
                        : null;

        AlertDialog dialog =
                new AlertDialog.Builder(this)
                        .setTitle(title)
                        .setMessage(
                                confirm
                                        ? "Use a strong password. The backup can be restored on another device only with this password."
                                        : "Enter the password used when this backup was created.")
                        .setView(
                                confirm
                                        ? wrapFields(
                                                first,
                                                second)
                                        : wrapFields(
                                                first))
                        .setNegativeButton(
                                "Cancel",
                                null)
                        .setPositiveButton(
                                confirm
                                        ? "Encrypt"
                                        : "Unlock",
                                null)
                        .create();

        dialog.setOnShowListener(
                d ->
                        dialog.getButton(
                                        AlertDialog
                                                .BUTTON_POSITIVE)
                                .setOnClickListener(
                                        v -> {
                                            String password =
                                                    first
                                                            .getText()
                                                            .toString();

                                            if (password.length() <
                                                    8) {
                                                first.setError(
                                                        "Use at least 8 characters");
                                                return;
                                            }

                                            if (confirm &&
                                                    !password.equals(
                                                            second
                                                                    .getText()
                                                                    .toString())) {
                                                second.setError(
                                                        "Passwords do not match");
                                                return;
                                            }

                                            dialog.dismiss();
                                            consumer.accept(
                                                    password);
                                        }
                                )
        );

        dialog.show();
    }

    private EditText pinField(
            String hint) {
        EditText field =
                new EditText(this);
        field.setHint(hint);
        field.setInputType(
                InputType.TYPE_CLASS_NUMBER |
                        InputType
                                .TYPE_NUMBER_VARIATION_PASSWORD);
        field.setSingleLine(true);
        return field;
    }

    private EditText passwordField(
            String hint) {
        EditText field =
                new EditText(this);
        field.setHint(hint);
        field.setInputType(
                InputType.TYPE_CLASS_TEXT |
                        InputType
                                .TYPE_TEXT_VARIATION_PASSWORD);
        field.setSingleLine(true);
        return field;
    }

    private LinearLayout wrapFields(
            EditText... fields) {
        LinearLayout layout =
                new LinearLayout(this);
        layout.setOrientation(
                LinearLayout.VERTICAL);

        int p = dp(20);
        layout.setPadding(
                p, dp(8), p, 0);

        for (EditText field : fields) {
            layout.addView(field);
        }

        return layout;
    }

    private int dp(int value) {
        return Math.round(
                value *
                        getResources()
                                .getDisplayMetrics()
                                .density);
    }

    private void confirmClear() {
        new AlertDialog.Builder(this)
                .setTitle(
                        "Clear local archive?")
                .setMessage(
                        "This permanently deletes the encrypted local message database and generated snapshots. Export a backup first if you may need the records later.")
                .setNegativeButton(
                        "Cancel",
                        null)
                .setPositiveButton(
                        "Delete everything",
                        (dialog, which) -> {
                            db.clearAll();

                            deleteRecursively(
                                    new File(
                                            getFilesDir(),
                                            "snapshots"));

                            refreshMessages();
                        }
                )
                .show();
    }

    private void deleteRecursively(
            File file) {
        if (file == null ||
                !file.exists()) {
            return;
        }

        if (file.isDirectory()) {
            File[] children =
                    file.listFiles();

            if (children != null) {
                for (File child : children) {
                    deleteRecursively(
                            child);
                }
            }
        }

        //noinspection ResultOfMethodCallIgnored
        file.delete();
    }

    @Override
    public void onRequestPermissionsResult(
            int requestCode,
            String[] permissions,
            int[] grantResults) {
        super.onRequestPermissionsResult(
                requestCode,
                permissions,
                grantResults);

        if (requestCode ==
                REQ_GALLERY_PERMISSION &&
                pendingGalleryMessage != null) {
            ArchiveMessage message =
                    pendingGalleryMessage;

            pendingGalleryMessage = null;

            if (grantResults.length > 0 &&
                    grantResults[0] ==
                            PackageManager
                                    .PERMISSION_GRANTED) {
                saveSnapshotToGallery(
                        message);
            } else {
                Toast.makeText(
                        this,
                        "Storage permission is required to save Gallery snapshots on Android 8/9",
                        Toast.LENGTH_LONG
                ).show();
            }
        }
    }

    @Override
    protected void onActivityResult(
            int requestCode,
            int resultCode,
            Intent data) {
        super.onActivityResult(
                requestCode,
                resultCode,
                data);

        if (resultCode != RESULT_OK ||
                data == null ||
                data.getData() == null) {
            return;
        }

        Uri uri = data.getData();

        try {
            if (requestCode ==
                    REQ_EXPORT_JSON &&
                    pendingJson != null) {
                writeBytes(
                        uri,
                        pendingJson.getBytes(
                                StandardCharsets.UTF_8));

                pendingJson = null;

                Toast.makeText(
                        this,
                        "JSON archive exported",
                        Toast.LENGTH_SHORT
                ).show();

            } else if (requestCode ==
                    REQ_EXPORT_BACKUP &&
                    pendingEncryptedBackup != null) {
                writeBytes(
                        uri,
                        pendingEncryptedBackup);

                pendingEncryptedBackup = null;

                Toast.makeText(
                        this,
                        "Encrypted backup saved",
                        Toast.LENGTH_SHORT
                ).show();

            } else if (requestCode ==
                    REQ_RESTORE_BACKUP) {
                pendingRestoreData =
                        readBytes(uri);

                if (looksLikeJson(
                        pendingRestoreData)) {
                    int restored =
                            restoreArchiveJson(
                                    new String(
                                            pendingRestoreData,
                                            StandardCharsets.UTF_8
                                    )
                            );

                    Toast.makeText(
                            this,
                            restored +
                                    " message(s) imported; duplicates skipped",
                            Toast.LENGTH_LONG
                    ).show();

                    refreshMessages();
                } else {
                    restoreEncryptedBackup(
                            pendingRestoreData);
                }

                pendingRestoreData = null;

            } else if (requestCode ==
                    REQ_EXPORT_PNG &&
                    pendingSnapshot != null) {
                try (FileInputStream in =
                             new FileInputStream(
                                     pendingSnapshot);
                     OutputStream out =
                             getContentResolver()
                                     .openOutputStream(
                                             uri)) {
                    if (out == null) {
                        throw new IllegalStateException(
                                "No output stream");
                    }

                    byte[] buffer =
                            new byte[8192];

                    int read;
                    while ((read = in.read(
                            buffer)) != -1) {
                        out.write(
                                buffer,
                                0,
                                read);
                    }
                }

                pendingSnapshot = null;

                Toast.makeText(
                        this,
                        "Snapshot PNG exported",
                        Toast.LENGTH_SHORT
                ).show();
            }
        } catch (Exception e) {
            Toast.makeText(
                    this,
                    "File operation failed: " +
                            e.getMessage(),
                    Toast.LENGTH_LONG
            ).show();
        }
    }

    private boolean looksLikeJson(
            byte[] data) {
        if (data == null) return false;

        int i = 0;
        while (i < data.length &&
                Character.isWhitespace(
                        (char) data[i])) {
            i++;
        }

        return i < data.length &&
                data[i] == '{';
    }

    private void writeBytes(
            Uri uri,
            byte[] bytes) throws Exception {
        try (OutputStream out =
                     getContentResolver()
                             .openOutputStream(uri)) {
            if (out == null) {
                throw new IllegalStateException(
                        "No output stream");
            }
            out.write(bytes);
        }
    }

    private byte[] readBytes(
            Uri uri) throws Exception {
        try (InputStream in =
                     getContentResolver()
                             .openInputStream(uri);
             ByteArrayOutputStream out =
                     new ByteArrayOutputStream()) {
            if (in == null) {
                throw new IllegalStateException(
                        "No input stream");
            }

            byte[] buffer =
                    new byte[8192];

            int read;
            while ((read = in.read(
                    buffer)) != -1) {
                out.write(
                        buffer,
                        0,
                        read);
            }

            return out.toByteArray();
        }
    }

    private String stamp() {
        return new SimpleDateFormat(
                "yyyyMMdd-HHmmss",
                Locale.US)
                .format(new Date());
    }

    private String sha256(
            String value) {
        try {
            MessageDigest digest =
                    MessageDigest.getInstance(
                            "SHA-256");

            byte[] hash =
                    digest.digest(
                            value.getBytes(
                                    StandardCharsets.UTF_8));

            StringBuilder hex =
                    new StringBuilder();

            for (byte b : hash) {
                hex.append(
                        String.format(
                                Locale.US,
                                "%02x",
                                b));
            }

            return hex.toString();
        } catch (Exception e) {
            return Integer.toHexString(
                    value.hashCode());
        }
    }

    private interface PasswordConsumer {
        void accept(String password);
    }
}
