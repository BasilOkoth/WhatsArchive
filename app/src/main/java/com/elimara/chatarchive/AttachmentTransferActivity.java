package com.elimara.chatarchive;

import android.app.Activity;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.widget.Toast;

import java.io.File;
import java.io.FileInputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

public class AttachmentTransferActivity extends Activity {
    public static final String MODE_SAVE = "save";
    public static final String MODE_EXPORT = "export";

    public static final String EXTRA_MODE = "mode";
    public static final String EXTRA_PATH = "path";
    public static final String EXTRA_NAME = "name";
    public static final String EXTRA_MIME = "mime";
    public static final String EXTRA_SIZE = "size";
    public static final String EXTRA_CONVERSATION = "conversation";
    public static final String EXTRA_SENDER = "sender";
    public static final String EXTRA_BODY = "body";
    public static final String EXTRA_POSTED_AT = "posted_at";

    private static final int REQ_CREATE_DOCUMENT = 4101;

    private String mode;
    private File sourceFile;
    private String displayName;
    private String mimeType;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        mode = clean(getIntent().getStringExtra(EXTRA_MODE));
        displayName = sanitizeName(getIntent().getStringExtra(EXTRA_NAME));
        mimeType = clean(getIntent().getStringExtra(EXTRA_MIME));
        if (mimeType.isEmpty()) mimeType = "application/octet-stream";

        sourceFile = validateSource(getIntent().getStringExtra(EXTRA_PATH));
        if (sourceFile == null) {
            Toast.makeText(this, "Archived attachment is unavailable", Toast.LENGTH_LONG).show();
            finish();
            return;
        }

        if (displayName.isEmpty()) displayName = sourceFile.getName();

        if (savedInstanceState == null) {
            launchDestinationPicker();
        }
    }

    private void launchDestinationPicker() {
        try {
            Intent intent = new Intent(Intent.ACTION_CREATE_DOCUMENT);
            intent.addCategory(Intent.CATEGORY_OPENABLE);

            if (MODE_EXPORT.equals(mode)) {
                intent.setType("application/zip");
                intent.putExtra(Intent.EXTRA_TITLE, exportName(displayName));
            } else {
                mode = MODE_SAVE;
                intent.setType(mimeType);
                intent.putExtra(Intent.EXTRA_TITLE, displayName);
            }

            startActivityForResult(intent, REQ_CREATE_DOCUMENT);
        } catch (Exception e) {
            Toast.makeText(this, "No file picker is available", Toast.LENGTH_LONG).show();
            finish();
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);

        if (requestCode != REQ_CREATE_DOCUMENT) return;
        if (resultCode != RESULT_OK || data == null || data.getData() == null) {
            finish();
            return;
        }

        Uri destination = data.getData();
        try {
            if (MODE_EXPORT.equals(mode)) {
                writeExportBundle(destination);
                Toast.makeText(this, "Attachment export created", Toast.LENGTH_SHORT).show();
            } else {
                copyOriginal(destination);
                Toast.makeText(this, "Attachment saved", Toast.LENGTH_SHORT).show();
            }
        } catch (Exception e) {
            Toast.makeText(
                    this,
                    "Could not write the file: " + clean(e.getMessage()),
                    Toast.LENGTH_LONG
            ).show();
        }
        finish();
    }

    private void copyOriginal(Uri destination) throws Exception {
        try (InputStream in = new FileInputStream(sourceFile);
             OutputStream out = getContentResolver().openOutputStream(destination)) {
            if (out == null) throw new IllegalStateException("No output stream");
            copy(in, out);
        }
    }

    private void writeExportBundle(Uri destination) throws Exception {
        try (OutputStream raw = getContentResolver().openOutputStream(destination);
             ZipOutputStream zip = raw == null ? null : new ZipOutputStream(raw)) {

            if (zip == null) throw new IllegalStateException("No output stream");

            zip.putNextEntry(new ZipEntry("attachment/" + sanitizeName(displayName)));
            try (InputStream in = new FileInputStream(sourceFile)) {
                copy(in, zip);
            }
            zip.closeEntry();

            zip.putNextEntry(new ZipEntry("ChatArchive_message.txt"));
            zip.write(buildMetadata().getBytes(StandardCharsets.UTF_8));
            zip.closeEntry();
        }
    }

    private String buildMetadata() {
        String conversation = clean(getIntent().getStringExtra(EXTRA_CONVERSATION));
        String sender = clean(getIntent().getStringExtra(EXTRA_SENDER));
        String body = clean(getIntent().getStringExtra(EXTRA_BODY));
        long postedAt = getIntent().getLongExtra(EXTRA_POSTED_AT, 0L);
        long size = getIntent().getLongExtra(EXTRA_SIZE, sourceFile.length());

        String received = postedAt > 0
                ? new SimpleDateFormat("dd MMM yyyy, HH:mm:ss", Locale.getDefault())
                .format(new Date(postedAt))
                : "Unknown";

        return "ChatArchive attachment export\n" +
                "Conversation: " + conversation + "\n" +
                "Sender: " + sender + "\n" +
                "Received: " + received + "\n" +
                "Attachment: " + displayName + "\n" +
                "MIME type: " + mimeType + "\n" +
                "Size: " + size + " bytes\n\n" +
                "Message:\n" + body + "\n\n" +
                "Note: ChatArchive preserves notification data that Android exposes to the app. " +
                "This export does not prove that a sender deleted the original WhatsApp message.\n";
    }

    private File validateSource(String path) {
        if (path == null || path.trim().isEmpty()) return null;
        try {
            File root = new File(getFilesDir(), "attachments");
            File file = new File(path);
            String canonicalRoot = root.getCanonicalPath() + File.separator;
            String canonicalFile = file.getCanonicalPath();
            if (!canonicalFile.startsWith(canonicalRoot) || !file.isFile()) return null;
            return file;
        } catch (Exception e) {
            return null;
        }
    }

    private void copy(InputStream in, OutputStream out) throws Exception {
        byte[] buffer = new byte[16 * 1024];
        int read;
        while ((read = in.read(buffer)) != -1) {
            out.write(buffer, 0, read);
        }
        out.flush();
    }

    private String exportName(String originalName) {
        String value = sanitizeName(originalName);
        int dot = value.lastIndexOf('.');
        if (dot > 0) value = value.substring(0, dot);
        if (value.isEmpty()) value = "ChatArchive_attachment";
        return value + "_ChatArchive.zip";
    }

    private String sanitizeName(String value) {
        String cleaned = clean(value)
                .replace('/', '_')
                .replace('\\', '_')
                .replace('\n', ' ')
                .replace('\r', ' ');
        if (cleaned.length() > 120) cleaned = cleaned.substring(0, 120);
        return cleaned.isEmpty() ? "attachment" : cleaned;
    }

    private String clean(String value) {
        return value == null ? "" : value.trim();
    }
}
