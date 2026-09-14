package com.basil.whatsarchive;

import android.app.Notification;
import android.content.Intent;
import android.os.Bundle;
import android.os.Parcelable;
import android.service.notification.NotificationListenerService;
import android.service.notification.StatusBarNotification;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Locale;

public class WhatsAppNotificationListener extends NotificationListenerService {
    public static final String ACTION_ARCHIVE_UPDATED =
            "com.basil.whatsarchive.ARCHIVE_UPDATED";

    private static final String WHATSAPP = "com.whatsapp";
    private static final String WHATSAPP_BUSINESS = "com.whatsapp.w4b";

    private ArchiveDbHelper db;

    @Override
    public void onCreate() {
        super.onCreate();
        db = new ArchiveDbHelper(this);
    }

    @Override
    public void onNotificationPosted(StatusBarNotification sbn) {
        if (!isWhatsApp(sbn.getPackageName())) return;

        Notification notification = sbn.getNotification();
        if (notification == null) return;
        if ((notification.flags & Notification.FLAG_GROUP_SUMMARY) != 0) return;

        Bundle extras = notification.extras;
        if (extras == null) return;

        String conversationName = firstNonBlank(
                charSequence(extras.getCharSequence(Notification.EXTRA_CONVERSATION_TITLE)),
                charSequence(extras.getCharSequence(Notification.EXTRA_TITLE)),
                "Unknown chat"
        );

        String conversationId = deriveConversationId(
                sbn, notification, conversationName);

        Parcelable[] messages = extras.getParcelableArray(Notification.EXTRA_MESSAGES);
        boolean storedStructuredMessage = false;

        if (messages != null && messages.length > 0) {
            for (Parcelable item : messages) {
                if (!(item instanceof Bundle)) continue;

                Bundle bundle = (Bundle) item;
                String body = charSequence(bundle.getCharSequence("text"));
                if (body.isEmpty()) continue;

                String participantName = extractMessageSender(bundle);
                if (participantName.equalsIgnoreCase(conversationName)) {
                    participantName = "";
                }

                String displaySender = buildDisplaySender(
                        conversationName, participantName);

                long messageTime = bundle.getLong("time", 0L);
                if (messageTime <= 0) {
                    messageTime = sbn.getPostTime() > 0
                            ? sbn.getPostTime()
                            : System.currentTimeMillis();
                }

                store(
                        sbn,
                        conversationId,
                        conversationName,
                        participantName,
                        displaySender,
                        body,
                        messageTime
                );
                storedStructuredMessage = true;
            }
        }

        if (!storedStructuredMessage) {
            String body = extractFallbackBody(extras);
            if (!body.isEmpty()) {
                long postedAt = sbn.getPostTime() > 0
                        ? sbn.getPostTime()
                        : System.currentTimeMillis();

                store(
                        sbn,
                        conversationId,
                        conversationName,
                        "",
                        conversationName,
                        body,
                        postedAt
                );
            }
        }
    }

    private void store(StatusBarNotification sbn,
                       String conversationId,
                       String conversationName,
                       String participantName,
                       String displaySender,
                       String body,
                       long postedAt) {
        String key = sbn.getKey();

        String fingerprint = sha256(
                sbn.getPackageName() + "|" +
                        clean(conversationId) + "|" +
                        clean(key) + "|" +
                        clean(participantName) + "|" +
                        body + "|" +
                        postedAt
        );

        long id = db.insertMessage(
                sbn.getPackageName(),
                key,
                conversationId,
                conversationName,
                participantName,
                displaySender,
                body,
                postedAt,
                fingerprint
        );

        if (id > 0) {
            String snapshotSender = buildSnapshotSender(
                    conversationName, participantName);

            String snapshotPath = SnapshotRenderer.create(
                    this,
                    id,
                    snapshotSender,
                    body,
                    postedAt,
                    sbn.getPackageName()
            );

            if (snapshotPath != null) {
                db.setSnapshotPath(id, snapshotPath);
            }

            broadcastArchiveUpdate();
        }
    }

    @Override
    public void onNotificationRemoved(StatusBarNotification sbn) {
        if (!isWhatsApp(sbn.getPackageName())) return;
        db.markMostRecentRemoved(sbn.getKey(), System.currentTimeMillis());
        broadcastArchiveUpdate();
    }

    private void broadcastArchiveUpdate() {
        Intent intent = new Intent(ACTION_ARCHIVE_UPDATED);
        intent.setPackage(getPackageName());
        sendBroadcast(intent);
    }

    private boolean isWhatsApp(String packageName) {
        return WHATSAPP.equals(packageName) ||
                WHATSAPP_BUSINESS.equals(packageName);
    }

    /**
     * Identity priority:
     * 1) Android conversation shortcut id - normally tied to the actual chat.
     * 2) Notification tag - often stable per WhatsApp conversation.
     * 3) Full Android notification key.
     * 4) Name hash only as a last-resort fallback.
     *
     * Raw identifiers are hashed before storage so WhatsArchive does not
     * persist WhatsApp's internal shortcut/tag value as plain text.
     */
    private String deriveConversationId(StatusBarNotification sbn,
                                        Notification notification,
                                        String conversationName) {
        String packageName = clean(sbn.getPackageName());

        String shortcutId = clean(notification.getShortcutId());
        if (!shortcutId.isEmpty()) {
            return "shortcut:" + sha256(packageName + "|" + shortcutId);
        }

        String tag = clean(sbn.getTag());
        if (!tag.isEmpty()) {
            return "tag:" + sha256(packageName + "|" + tag);
        }

        String key = clean(sbn.getKey());
        if (!key.isEmpty()) {
            return "key:" + sha256(packageName + "|" + key);
        }

        return "name:" + sha256(
                packageName + "|" +
                        clean(conversationName).toLowerCase(Locale.ROOT)
        );
    }

    @SuppressWarnings("deprecation")
    private String extractMessageSender(Bundle bundle) {
        String legacy = charSequence(bundle.getCharSequence("sender"));
        if (!legacy.isEmpty()) return legacy;

        if (android.os.Build.VERSION.SDK_INT >= 28) {
            try {
                Parcelable senderPerson = bundle.getParcelable("sender_person");
                if (senderPerson != null &&
                        "android.app.Person".equals(senderPerson.getClass().getName())) {
                    Object name = senderPerson.getClass()
                            .getMethod("getName")
                            .invoke(senderPerson);
                    if (name instanceof CharSequence) {
                        return charSequence((CharSequence) name);
                    }
                }
            } catch (Exception ignored) {
            }
        }
        return "";
    }

    private String extractFallbackBody(Bundle extras) {
        String bigText = charSequence(
                extras.getCharSequence(Notification.EXTRA_BIG_TEXT));
        if (!bigText.isEmpty()) return bigText;

        String text = charSequence(
                extras.getCharSequence(Notification.EXTRA_TEXT));
        if (!text.isEmpty()) return text;

        CharSequence[] lines =
                extras.getCharSequenceArray(Notification.EXTRA_TEXT_LINES);

        if (lines != null && lines.length > 0) {
            StringBuilder joined = new StringBuilder();
            for (CharSequence line : lines) {
                if (line == null) continue;
                if (joined.length() > 0) joined.append("\n");
                joined.append(line);
            }
            return joined.toString().trim();
        }

        return "";
    }

    private String buildDisplaySender(String conversationName,
                                      String participantName) {
        String conversation = clean(conversationName);
        if (conversation.isEmpty()) conversation = "Unknown chat";

        String participant = clean(participantName);
        if (!participant.isEmpty() &&
                !participant.equalsIgnoreCase(conversation)) {
            return conversation + " — " + participant;
        }
        return conversation;
    }

    private String buildSnapshotSender(String conversationName,
                                       String participantName) {
        String conversation = clean(conversationName);
        if (conversation.isEmpty()) conversation = "Unknown chat";

        String participant = clean(participantName);
        if (!participant.isEmpty() &&
                !participant.equalsIgnoreCase(conversation)) {
            return participant + " • " + conversation;
        }
        return conversation;
    }

    private String charSequence(CharSequence value) {
        return value == null ? "" : value.toString().trim();
    }

    private String firstNonBlank(String... values) {
        for (String value : values) {
            if (value != null && !value.trim().isEmpty()) {
                return value.trim();
            }
        }
        return "";
    }

    private String clean(String value) {
        return value == null ? "" : value.trim();
    }

    private String sha256(String value) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(
                    value.getBytes(StandardCharsets.UTF_8));

            StringBuilder hex = new StringBuilder();
            for (byte b : hash) {
                hex.append(String.format(Locale.US, "%02x", b));
            }
            return hex.toString();
        } catch (Exception e) {
            return Integer.toHexString(value.hashCode());
        }
    }
}
