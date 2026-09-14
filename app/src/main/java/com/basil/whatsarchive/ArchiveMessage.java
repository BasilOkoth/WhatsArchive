package com.basil.whatsarchive;

public class ArchiveMessage {
    public long id;
    public String packageName;
    public String notificationKey;
    public String sender;
    public String body;
    public long postedAt;
    public Long removedAt;
    public String snapshotPath;
    public String fingerprint;

    public boolean isPossiblyDeleted() {
        return removedAt != null && removedAt >= postedAt;
    }
}
