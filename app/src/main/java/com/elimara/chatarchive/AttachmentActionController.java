package com.elimara.chatarchive;

import android.app.Activity;
import android.content.ClipData;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.widget.Toast;

import androidx.core.content.FileProvider;

import java.io.File;

public class AttachmentActionController {
    private final Context context;

    public AttachmentActionController(Context context) {
        this.context = context;
    }

    public void open(ArchiveAttachment attachment) {
        File file = availableFile(attachment);
        if (file == null) return;

        try {
            Uri uri = shareUri(file);
            Intent intent = new Intent(Intent.ACTION_VIEW);
            intent.setDataAndType(uri, attachment.getMimeType());
            intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
            intent.setClipData(ClipData.newRawUri("ChatArchive attachment", uri));
            start(intent, "Open attachment");
        } catch (Exception e) {
            toast("No app is available to open this attachment");
        }
    }

    public void share(ArchiveAttachment attachment) {
        File file = availableFile(attachment);
        if (file == null) return;

        try {
            Uri uri = shareUri(file);
            Intent share = new Intent(Intent.ACTION_SEND);
            share.setType(attachment.getMimeType());
            share.putExtra(Intent.EXTRA_STREAM, uri);
            share.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
            share.setClipData(ClipData.newRawUri("ChatArchive attachment", uri));
            start(Intent.createChooser(share, "Share archived attachment"), null);
        } catch (Exception e) {
            toast("No app is available to share this attachment");
        }
    }

    public void save(ArchiveAttachment attachment) {
        File file = availableFile(attachment);
        if (file == null) return;

        Intent intent = transferIntent(AttachmentTransferActivity.MODE_SAVE, attachment, file);
        start(intent, null);
    }

    public void exportBundle(ArchiveMessage message, ArchiveAttachment attachment) {
        File file = availableFile(attachment);
        if (file == null) return;

        Intent intent = transferIntent(AttachmentTransferActivity.MODE_EXPORT, attachment, file);
        intent.putExtra(AttachmentTransferActivity.EXTRA_CONVERSATION,
                message == null ? "" : message.getConversationLabel());
        intent.putExtra(AttachmentTransferActivity.EXTRA_SENDER,
                message == null ? "" : message.getMessageSenderLabel());
        intent.putExtra(AttachmentTransferActivity.EXTRA_BODY,
                message == null || message.body == null ? "" : message.body);
        intent.putExtra(AttachmentTransferActivity.EXTRA_POSTED_AT,
                message == null ? 0L : message.postedAt);
        start(intent, null);
    }

    private Intent transferIntent(String mode, ArchiveAttachment attachment, File file) {
        Intent intent = new Intent(context, AttachmentTransferActivity.class);
        intent.putExtra(AttachmentTransferActivity.EXTRA_MODE, mode);
        intent.putExtra(AttachmentTransferActivity.EXTRA_PATH, file.getAbsolutePath());
        intent.putExtra(AttachmentTransferActivity.EXTRA_NAME, attachment.getDisplayName());
        intent.putExtra(AttachmentTransferActivity.EXTRA_MIME, attachment.getMimeType());
        intent.putExtra(AttachmentTransferActivity.EXTRA_SIZE, attachment.byteSize);
        return intent;
    }

    private File availableFile(ArchiveAttachment attachment) {
        if (attachment == null || !attachment.isAvailable()) {
            String reason = attachment == null || attachment.captureStatus == null
                    ? "The attachment file is not available"
                    : attachment.captureStatus;
            toast(reason);
            return null;
        }

        try {
            File root = new File(context.getFilesDir(), "attachments");
            File file = new File(attachment.storedPath);
            String canonicalRoot = root.getCanonicalPath() + File.separator;
            String canonicalFile = file.getCanonicalPath();
            if (!canonicalFile.startsWith(canonicalRoot) || !file.isFile()) {
                toast("The archived attachment file is missing");
                return null;
            }
            return file;
        } catch (Exception e) {
            toast("The archived attachment file could not be accessed");
            return null;
        }
    }

    private Uri shareUri(File file) {
        return FileProvider.getUriForFile(
                context,
                context.getPackageName() + ".files",
                file
        );
    }

    private void start(Intent intent, String fallbackChooserTitle) {
        if (!(context instanceof Activity)) {
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        }

        if (fallbackChooserTitle != null) {
            try {
                context.startActivity(intent);
                return;
            } catch (Exception ignored) {
                Intent chooser = Intent.createChooser(intent, fallbackChooserTitle);
                if (!(context instanceof Activity)) {
                    chooser.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                }
                context.startActivity(chooser);
                return;
            }
        }

        context.startActivity(intent);
    }

    private void toast(String message) {
        Toast.makeText(context, message, Toast.LENGTH_LONG).show();
    }
}
