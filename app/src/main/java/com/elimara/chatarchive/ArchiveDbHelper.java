package com.elimara.chatarchive;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public class ArchiveDbHelper extends SQLiteOpenHelper {
    private static final String DB_NAME = "whatsarchive.db";
    private static final int DB_VERSION = 3;

    private final CryptoManager crypto = new CryptoManager();
    private final Context appContext;

    public ArchiveDbHelper(Context context) {
        super(context, DB_NAME, null, DB_VERSION);
        appContext = context.getApplicationContext();
    }

    @Override
    public void onCreate(SQLiteDatabase db) {
        db.execSQL("CREATE TABLE messages (" +
                "id INTEGER PRIMARY KEY AUTOINCREMENT," +
                "package_name TEXT NOT NULL," +
                "notification_key TEXT," +
                "conversation_id_enc TEXT," +
                "conversation_name_enc TEXT," +
                "participant_name_enc TEXT," +
                "sender_enc TEXT NOT NULL," +
                "body_enc TEXT NOT NULL," +
                "posted_at INTEGER NOT NULL," +
                "removed_at INTEGER," +
                "fingerprint TEXT NOT NULL UNIQUE," +
                "snapshot_path TEXT" +
                ")");
        db.execSQL("CREATE INDEX idx_messages_posted ON messages(posted_at DESC)");
        db.execSQL("CREATE INDEX idx_messages_key ON messages(notification_key)");
        createAttachmentsTable(db);
    }

    @Override
    public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
        if (oldVersion < 2) {
            db.execSQL("ALTER TABLE messages ADD COLUMN conversation_id_enc TEXT");
            db.execSQL("ALTER TABLE messages ADD COLUMN conversation_name_enc TEXT");
            db.execSQL("ALTER TABLE messages ADD COLUMN participant_name_enc TEXT");
        }
        if (oldVersion < 3) {
            createAttachmentsTable(db);
        }
    }

    private void createAttachmentsTable(SQLiteDatabase db) {
        db.execSQL("CREATE TABLE IF NOT EXISTS attachments (" +
                "id INTEGER PRIMARY KEY AUTOINCREMENT," +
                "message_id INTEGER NOT NULL," +
                "mime_type TEXT," +
                "file_name_enc TEXT," +
                "stored_path TEXT," +
                "byte_size INTEGER NOT NULL DEFAULT 0," +
                "capture_status_enc TEXT," +
                "created_at INTEGER NOT NULL" +
                ")");
        db.execSQL("CREATE INDEX IF NOT EXISTS idx_attachments_message ON attachments(message_id)");
    }

    public synchronized long insertMessage(String packageName,
                                           String notificationKey,
                                           String conversationId,
                                           String conversationName,
                                           String participantName,
                                           String sender,
                                           String body,
                                           long postedAt,
                                           String fingerprint) {
        String safeConversationName = clean(conversationName);
        String safeParticipant = clean(participantName);
        String safeSender = clean(sender);

        if (safeConversationName.isEmpty()) {
            safeConversationName = legacyConversationName(safeSender);
        }
        if (safeSender.isEmpty()) {
            safeSender = buildLegacyDisplaySender(safeConversationName, safeParticipant);
        }

        String safeConversationId = clean(conversationId);
        if (safeConversationId.isEmpty()) {
            safeConversationId = deriveLegacyConversationId(
                    packageName, notificationKey, safeConversationName);
        }

        ContentValues values = new ContentValues();
        values.put("package_name", packageName);
        values.put("notification_key", notificationKey);
        values.put("conversation_id_enc", crypto.encrypt(safeConversationId));
        values.put("conversation_name_enc", crypto.encrypt(safeConversationName));
        values.put("participant_name_enc", crypto.encrypt(safeParticipant));
        values.put("sender_enc", crypto.encrypt(safeSender));
        values.put("body_enc", crypto.encrypt(body));
        values.put("posted_at", postedAt);
        values.put("fingerprint", fingerprint);

        return getWritableDatabase().insertWithOnConflict(
                "messages", null, values, SQLiteDatabase.CONFLICT_IGNORE);
    }

    // Backward-compatible overload for old restore/import code paths.
    public synchronized long insertMessage(String packageName,
                                           String notificationKey,
                                           String sender,
                                           String body,
                                           long postedAt,
                                           String fingerprint) {
        String conversationName = legacyConversationName(sender);
        String participantName = legacyParticipantName(sender);
        String conversationId = deriveLegacyConversationId(
                packageName, notificationKey, conversationName);

        return insertMessage(
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
    }

    public synchronized long insertAttachment(long messageId, ArchiveAttachment attachment) {
        if (attachment == null || messageId <= 0) return -1L;

        ContentValues values = new ContentValues();
        values.put("message_id", messageId);
        values.put("mime_type", clean(attachment.mimeType));
        values.put("file_name_enc", crypto.encrypt(clean(attachment.fileName)));
        values.put("stored_path", clean(attachment.storedPath));
        values.put("byte_size", Math.max(0L, attachment.byteSize));
        values.put("capture_status_enc", crypto.encrypt(clean(attachment.captureStatus)));
        values.put("created_at", attachment.createdAt > 0
                ? attachment.createdAt
                : System.currentTimeMillis());

        return getWritableDatabase().insert("attachments", null, values);
    }

    public synchronized void setSnapshotPath(long id, String path) {
        ContentValues values = new ContentValues();
        values.put("snapshot_path", path);
        getWritableDatabase().update(
                "messages", values, "id=?", new String[]{String.valueOf(id)});
    }

    public synchronized void setRemovedAt(long id, Long removedAt) {
        ContentValues values = new ContentValues();
        if (removedAt == null) values.putNull("removed_at");
        else values.put("removed_at", removedAt);
        getWritableDatabase().update(
                "messages", values, "id=?", new String[]{String.valueOf(id)});
    }

    public synchronized void markMostRecentRemoved(String notificationKey, long removedAt) {
        if (notificationKey == null) return;
        SQLiteDatabase db = getWritableDatabase();
        ContentValues values = new ContentValues();
        values.put("removed_at", removedAt);
        db.update(
                "messages",
                values,
                "id=(SELECT id FROM messages WHERE notification_key=? ORDER BY posted_at DESC LIMIT 1)",
                new String[]{notificationKey}
        );
    }

    public synchronized List<ArchiveMessage> getAll() {
        List<ArchiveMessage> result = new ArrayList<>();
        Cursor cursor = getReadableDatabase().query(
                "messages", null, null, null, null, null, "posted_at DESC");
        try {
            while (cursor.moveToNext()) {
                result.add(fromCursor(cursor));
            }
        } finally {
            cursor.close();
        }
        hydrateAttachments(result);
        return result;
    }

    private void hydrateAttachments(List<ArchiveMessage> messages) {
        if (messages == null || messages.isEmpty()) return;

        Map<Long, ArchiveMessage> byId = new HashMap<>();
        for (ArchiveMessage message : messages) {
            byId.put(message.id, message);
        }

        Cursor cursor = getReadableDatabase().query(
                "attachments", null, null, null, null, null, "created_at ASC");
        try {
            while (cursor.moveToNext()) {
                long messageId = cursor.getLong(cursor.getColumnIndexOrThrow("message_id"));
                ArchiveMessage parent = byId.get(messageId);
                if (parent == null) continue;
                parent.attachments.add(attachmentFromCursor(cursor));
            }
        } finally {
            cursor.close();
        }
    }

    private ArchiveAttachment attachmentFromCursor(Cursor cursor) {
        ArchiveAttachment attachment = new ArchiveAttachment();
        attachment.id = cursor.getLong(cursor.getColumnIndexOrThrow("id"));
        attachment.messageId = cursor.getLong(cursor.getColumnIndexOrThrow("message_id"));
        attachment.mimeType = cursor.getString(cursor.getColumnIndexOrThrow("mime_type"));

        int fileNameIndex = cursor.getColumnIndexOrThrow("file_name_enc");
        attachment.fileName = cursor.isNull(fileNameIndex)
                ? ""
                : crypto.decrypt(cursor.getString(fileNameIndex));

        attachment.storedPath = cursor.getString(cursor.getColumnIndexOrThrow("stored_path"));
        attachment.byteSize = cursor.getLong(cursor.getColumnIndexOrThrow("byte_size"));

        int statusIndex = cursor.getColumnIndexOrThrow("capture_status_enc");
        attachment.captureStatus = cursor.isNull(statusIndex)
                ? ""
                : crypto.decrypt(cursor.getString(statusIndex));

        attachment.createdAt = cursor.getLong(cursor.getColumnIndexOrThrow("created_at"));
        return attachment;
    }

    public synchronized void clearAll() {
        Cursor cursor = getReadableDatabase().query(
                "attachments",
                new String[]{"stored_path"},
                "stored_path IS NOT NULL AND stored_path != ''",
                null,
                null,
                null,
                null);
        try {
            while (cursor.moveToNext()) {
                String path = cursor.getString(0);
                if (path == null || path.trim().isEmpty()) continue;
                try {
                    File file = new File(path);
                    File root = new File(appContext.getFilesDir(), "attachments");
                    String canonicalFile = file.getCanonicalPath();
                    String canonicalRoot = root.getCanonicalPath() + File.separator;
                    if (canonicalFile.startsWith(canonicalRoot)) {
                        //noinspection ResultOfMethodCallIgnored
                        file.delete();
                    }
                } catch (Exception ignored) {
                }
            }
        } finally {
            cursor.close();
        }

        SQLiteDatabase db = getWritableDatabase();
        db.delete("attachments", null, null);
        db.delete("messages", null, null);
    }

    private ArchiveMessage fromCursor(Cursor cursor) {
        ArchiveMessage message = new ArchiveMessage();
        message.id = cursor.getLong(cursor.getColumnIndexOrThrow("id"));
        message.packageName = cursor.getString(cursor.getColumnIndexOrThrow("package_name"));
        message.notificationKey = cursor.getString(cursor.getColumnIndexOrThrow("notification_key"));
        message.sender = crypto.decrypt(cursor.getString(cursor.getColumnIndexOrThrow("sender_enc")));
        message.body = crypto.decrypt(cursor.getString(cursor.getColumnIndexOrThrow("body_enc")));
        message.postedAt = cursor.getLong(cursor.getColumnIndexOrThrow("posted_at"));

        int removedIndex = cursor.getColumnIndexOrThrow("removed_at");
        message.removedAt = cursor.isNull(removedIndex) ? null : cursor.getLong(removedIndex);
        message.snapshotPath = cursor.getString(cursor.getColumnIndexOrThrow("snapshot_path"));
        message.fingerprint = cursor.getString(cursor.getColumnIndexOrThrow("fingerprint"));

        int conversationIdIndex = cursor.getColumnIndex("conversation_id_enc");
        int conversationNameIndex = cursor.getColumnIndex("conversation_name_enc");
        int participantNameIndex = cursor.getColumnIndex("participant_name_enc");

        message.conversationId = conversationIdIndex >= 0 && !cursor.isNull(conversationIdIndex)
                ? crypto.decrypt(cursor.getString(conversationIdIndex)) : "";
        message.conversationName = conversationNameIndex >= 0 && !cursor.isNull(conversationNameIndex)
                ? crypto.decrypt(cursor.getString(conversationNameIndex)) : "";
        message.participantName = participantNameIndex >= 0 && !cursor.isNull(participantNameIndex)
                ? crypto.decrypt(cursor.getString(participantNameIndex)) : "";

        // Legacy v0.1/v0.2 rows: recover the conversation/member split from
        // "Group — Member", then use the stored Android notification key as
        // the strongest remaining identity hint.
        if (clean(message.conversationName).isEmpty()) {
            message.conversationName = legacyConversationName(message.sender);
        }
        if (clean(message.participantName).isEmpty()) {
            message.participantName = legacyParticipantName(message.sender);
        }
        if (clean(message.conversationId).isEmpty()) {
            message.conversationId = deriveLegacyConversationId(
                    message.packageName,
                    message.notificationKey,
                    message.conversationName
            );
        }

        return message;
    }

    private String legacyConversationName(String sender) {
        String value = clean(sender);
        if (value.isEmpty()) return "Unknown chat";
        int split = value.indexOf(" — ");
        return split > 0 ? value.substring(0, split).trim() : value;
    }

    private String legacyParticipantName(String sender) {
        String value = clean(sender);
        int split = value.indexOf(" — ");
        if (split > 0 && split + 3 < value.length()) {
            return value.substring(split + 3).trim();
        }
        return "";
    }

    private String buildLegacyDisplaySender(String conversationName, String participantName) {
        String conversation = clean(conversationName);
        if (conversation.isEmpty()) conversation = "Unknown chat";
        String participant = clean(participantName);
        if (!participant.isEmpty() && !participant.equalsIgnoreCase(conversation)) {
            return conversation + " — " + participant;
        }
        return conversation;
    }

    private String deriveLegacyConversationId(String packageName,
                                              String notificationKey,
                                              String conversationName) {
        String pkg = clean(packageName);
        String key = clean(notificationKey);
        if (!key.isEmpty()) {
            return "legacy-key:" + sha256(pkg + "|" + key);
        }
        return "legacy-name:" + sha256(
                pkg + "|" + clean(conversationName).toLowerCase(Locale.ROOT));
    }

    private String clean(String value) {
        return value == null ? "" : value.trim();
    }

    private String sha256(String value) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(value.getBytes(StandardCharsets.UTF_8));
            StringBuilder hex = new StringBuilder();
            for (byte b : hash) hex.append(String.format(Locale.US, "%02x", b));
            return hex.toString();
        } catch (Exception e) {
            return Integer.toHexString(value.hashCode());
        }
    }
}
