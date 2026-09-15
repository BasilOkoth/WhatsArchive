package com.elimara.chatarchive;

import java.util.ArrayList;
import java.util.List;

public class ArchiveMessage {
    public long id;
    public String packageName;
    public String notificationKey;

    // v0.2.3 conversation identity
    public String conversationId;
    public String conversationName;
    public String participantName;

    // Legacy/display field kept for backward compatibility with older exports.
    public String sender;

    public String body;
    public long postedAt;
    public Long removedAt;
    public String snapshotPath;
    public String fingerprint;

    // v0.4 attachment archive. Files are copied into private app storage when
    // Android exposes a readable media URI in the notification.
    public final List<ArchiveAttachment> attachments = new ArrayList<>();

    public boolean isPossiblyDeleted() {
        return removedAt != null && removedAt >= postedAt;
    }

    public String getConversationLabel() {
        if (conversationName != null && !conversationName.trim().isEmpty()) {
            return conversationName.trim();
        }
        if (sender != null && !sender.trim().isEmpty()) {
            String value = sender.trim();
            int split = value.indexOf(" — ");
            return split > 0 ? value.substring(0, split).trim() : value;
        }
        return "Unknown chat";
    }

    public String getMessageSenderLabel() {
        if (participantName != null && !participantName.trim().isEmpty()) {
            return participantName.trim();
        }
        return getConversationLabel();
    }

    public String getLegacyDisplaySender() {
        String conversation = getConversationLabel();
        String participant = participantName == null ? "" : participantName.trim();
        if (!participant.isEmpty() && !participant.equalsIgnoreCase(conversation)) {
            return conversation + " — " + participant;
        }
        return conversation;
    }
}
