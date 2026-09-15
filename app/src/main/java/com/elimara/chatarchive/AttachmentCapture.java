package com.elimara.chatarchive;

import android.content.ContentResolver;
import android.content.Context;
import android.database.Cursor;
import android.net.Uri;
import android.os.Bundle;
import android.os.Parcelable;
import android.provider.OpenableColumns;
import android.webkit.MimeTypeMap;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.util.Locale;
import java.util.UUID;

public final class AttachmentCapture {
    private static final long MAX_CAPTURE_BYTES = 50L * 1024L * 1024L;

    private AttachmentCapture() {}

    /**
     * Attempts to retain media attached to one Android MessagingStyle message.
     * WhatsArchive can only preserve bytes that Android/WhatsApp expose through
     * the notification. If a media type is advertised but no readable URI is
     * available, a metadata-only record is returned so the UI can be truthful.
     */
    @SuppressWarnings("deprecation")
    public static ArchiveAttachment capture(
            Context context,
            Bundle messageBundle,
            long messageId,
            long postedAt) {

        if (context == null || messageBundle == null) return null;

        String mimeType = clean(messageBundle.getString("type"));
        Uri dataUri = null;

        try {
            Parcelable parcelable = messageBundle.getParcelable("uri");
            if (parcelable instanceof Uri) {
                dataUri = (Uri) parcelable;
            }
        } catch (Exception ignored) {
        }

        if (dataUri == null && mimeType.isEmpty()) {
            return null;
        }

        ContentResolver resolver = context.getContentResolver();

        if (mimeType.isEmpty() && dataUri != null) {
            try {
                mimeType = clean(resolver.getType(dataUri));
            } catch (Exception ignored) {
            }
        }

        if (mimeType.isEmpty()) {
            mimeType = "application/octet-stream";
        }

        ArchiveAttachment attachment = new ArchiveAttachment();
        attachment.messageId = messageId;
        attachment.mimeType = mimeType;
        attachment.createdAt = System.currentTimeMillis();
        attachment.fileName = resolveDisplayName(resolver, dataUri, mimeType, postedAt);

        if (dataUri == null) {
            attachment.captureStatus =
                    "Attachment detected, but Android did not expose the file to ChatArchive.";
            return attachment;
        }

        File attachmentDir = new File(context.getFilesDir(), "attachments");
        if (!attachmentDir.exists() && !attachmentDir.mkdirs()) {
            attachment.captureStatus = "Attachment detected, but private storage could not be prepared.";
            return attachment;
        }

        String extension = extensionFor(attachment.fileName, mimeType);
        String diskName = messageId + "_" + postedAt + "_" +
                UUID.randomUUID().toString().replace("-", "").substring(0, 10) + extension;
        File target = new File(attachmentDir, diskName);

        long total = 0L;
        try (InputStream in = resolver.openInputStream(dataUri);
             FileOutputStream out = new FileOutputStream(target)) {

            if (in == null) {
                throw new IllegalStateException("No readable input stream");
            }

            byte[] buffer = new byte[16 * 1024];
            int read;
            while ((read = in.read(buffer)) != -1) {
                total += read;
                if (total > MAX_CAPTURE_BYTES) {
                    throw new AttachmentTooLargeException();
                }
                out.write(buffer, 0, read);
            }
            out.flush();

            attachment.storedPath = target.getAbsolutePath();
            attachment.byteSize = total;
            attachment.captureStatus = "Captured from notification data";
            return attachment;

        } catch (AttachmentTooLargeException e) {
            //noinspection ResultOfMethodCallIgnored
            target.delete();
            attachment.captureStatus =
                    "Attachment detected but was larger than the 50 MB automatic capture limit.";
            return attachment;
        } catch (Exception e) {
            //noinspection ResultOfMethodCallIgnored
            target.delete();
            attachment.captureStatus =
                    "Attachment detected, but the file was not readable from the notification.";
            return attachment;
        }
    }

    private static String resolveDisplayName(
            ContentResolver resolver,
            Uri uri,
            String mimeType,
            long postedAt) {

        String displayName = "";

        if (uri != null) {
            try (Cursor cursor = resolver.query(
                    uri,
                    new String[]{OpenableColumns.DISPLAY_NAME},
                    null,
                    null,
                    null)) {
                if (cursor != null && cursor.moveToFirst()) {
                    int index = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME);
                    if (index >= 0 && !cursor.isNull(index)) {
                        displayName = clean(cursor.getString(index));
                    }
                }
            } catch (Exception ignored) {
            }

            if (displayName.isEmpty()) {
                String segment = clean(uri.getLastPathSegment());
                if (!segment.isEmpty()) displayName = segment;
            }
        }

        displayName = sanitizeDisplayName(displayName);
        if (!displayName.isEmpty()) return displayName;

        String extension = extensionFor("", mimeType);
        return "ChatArchive_" + postedAt + extension;
    }

    private static String sanitizeDisplayName(String value) {
        String cleaned = clean(value)
                .replace('/', '_')
                .replace('\\', '_')
                .replace('\n', ' ')
                .replace('\r', ' ');
        if (cleaned.length() > 120) {
            cleaned = cleaned.substring(0, 120);
        }
        return cleaned;
    }

    private static String extensionFor(String fileName, String mimeType) {
        String name = clean(fileName);
        int dot = name.lastIndexOf('.');
        if (dot >= 0 && dot < name.length() - 1) {
            String ext = name.substring(dot + 1)
                    .replaceAll("[^A-Za-z0-9]", "")
                    .toLowerCase(Locale.ROOT);
            if (!ext.isEmpty() && ext.length() <= 8) {
                return "." + ext;
            }
        }

        try {
            String ext = MimeTypeMap.getSingleton()
                    .getExtensionFromMimeType(clean(mimeType).toLowerCase(Locale.ROOT));
            if (ext != null && !ext.trim().isEmpty()) {
                return "." + ext;
            }
        } catch (Exception ignored) {
        }

        return "";
    }

    private static String clean(String value) {
        return value == null ? "" : value.trim();
    }

    private static final class AttachmentTooLargeException extends Exception {
    }
}
