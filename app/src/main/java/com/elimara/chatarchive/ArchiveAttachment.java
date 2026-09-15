package com.elimara.chatarchive;

import java.io.File;
import java.util.Locale;

public class ArchiveAttachment {
    public long id;
    public long messageId;
    public String mimeType;
    public String fileName;
    public String storedPath;
    public long byteSize;
    public String captureStatus;
    public long createdAt;

    public boolean isAvailable() {
        return storedPath != null
                && !storedPath.trim().isEmpty()
                && new File(storedPath).isFile();
    }

    public String getDisplayName() {
        String value = fileName == null ? "" : fileName.trim();
        return value.isEmpty() ? "Attachment" : value;
    }

    public String getMimeType() {
        String value = mimeType == null ? "" : mimeType.trim();
        return value.isEmpty() ? "application/octet-stream" : value;
    }

    public String getTypeLabel() {
        String mime = getMimeType().toLowerCase(Locale.ROOT);
        if (mime.startsWith("image/")) return "Image";
        if (mime.startsWith("audio/")) return "Audio";
        if (mime.startsWith("video/")) return "Video";
        if ("application/pdf".equals(mime)) return "PDF";
        if (mime.contains("word") || mime.contains("document")) return "Document";
        if (mime.contains("sheet") || mime.contains("excel")) return "Spreadsheet";
        if (mime.contains("presentation") || mime.contains("powerpoint")) return "Presentation";
        return "File";
    }

    public String getIcon() {
        String mime = getMimeType().toLowerCase(Locale.ROOT);
        if (mime.startsWith("image/")) return "📷";
        if (mime.startsWith("audio/")) return "🎤";
        if (mime.startsWith("video/")) return "🎥";
        if ("application/pdf".equals(mime)) return "📄";
        return "📎";
    }

    public String getHumanSize() {
        if (byteSize <= 0) return "size unavailable";
        if (byteSize < 1024) return byteSize + " B";
        double kb = byteSize / 1024.0;
        if (kb < 1024) return String.format(Locale.getDefault(), "%.1f KB", kb);
        double mb = kb / 1024.0;
        if (mb < 1024) return String.format(Locale.getDefault(), "%.1f MB", mb);
        double gb = mb / 1024.0;
        return String.format(Locale.getDefault(), "%.1f GB", gb);
    }
}
