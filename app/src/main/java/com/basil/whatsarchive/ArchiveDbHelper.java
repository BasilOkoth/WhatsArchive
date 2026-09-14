package com.basil.whatsarchive;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;

import java.util.ArrayList;
import java.util.List;

public class ArchiveDbHelper extends SQLiteOpenHelper {
    private static final String DB_NAME = "whatsarchive.db";
    private static final int DB_VERSION = 1;
    private final CryptoManager crypto = new CryptoManager();

    public ArchiveDbHelper(Context context) {
        super(context, DB_NAME, null, DB_VERSION);
    }

    @Override
    public void onCreate(SQLiteDatabase db) {
        db.execSQL("CREATE TABLE messages (" +
                "id INTEGER PRIMARY KEY AUTOINCREMENT," +
                "package_name TEXT NOT NULL," +
                "notification_key TEXT," +
                "sender_enc TEXT NOT NULL," +
                "body_enc TEXT NOT NULL," +
                "posted_at INTEGER NOT NULL," +
                "removed_at INTEGER," +
                "fingerprint TEXT NOT NULL UNIQUE," +
                "snapshot_path TEXT" +
                ")");
        db.execSQL("CREATE INDEX idx_messages_posted ON messages(posted_at DESC)");
        db.execSQL("CREATE INDEX idx_messages_key ON messages(notification_key)");
    }

    @Override
    public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
        // Schema is unchanged in v0.2; existing v0.1 archives remain compatible.
    }

    public synchronized long insertMessage(String packageName,
                                           String notificationKey,
                                           String sender,
                                           String body,
                                           long postedAt,
                                           String fingerprint) {
        ContentValues values = new ContentValues();
        values.put("package_name", packageName);
        values.put("notification_key", notificationKey);
        values.put("sender_enc", crypto.encrypt(sender));
        values.put("body_enc", crypto.encrypt(body));
        values.put("posted_at", postedAt);
        values.put("fingerprint", fingerprint);
        return getWritableDatabase().insertWithOnConflict(
                "messages", null, values, SQLiteDatabase.CONFLICT_IGNORE);
    }

    public synchronized void setSnapshotPath(long id, String path) {
        ContentValues values = new ContentValues();
        values.put("snapshot_path", path);
        getWritableDatabase().update("messages", values, "id=?", new String[]{String.valueOf(id)});
    }

    public synchronized void setRemovedAt(long id, Long removedAt) {
        ContentValues values = new ContentValues();
        if (removedAt == null) values.putNull("removed_at");
        else values.put("removed_at", removedAt);
        getWritableDatabase().update("messages", values, "id=?", new String[]{String.valueOf(id)});
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
            while (cursor.moveToNext()) result.add(fromCursor(cursor));
        } finally {
            cursor.close();
        }
        return result;
    }

    public synchronized void clearAll() {
        getWritableDatabase().delete("messages", null, null);
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
        return message;
    }
}
