# WhatsArchive v0.1.2

A personal, offline-first Android notification archive for WhatsApp and WhatsApp Business.

## What it does

- Listens for WhatsApp / WhatsApp Business notifications after the phone owner grants Android Notification Access.
- Stores notification-visible sender/chat, message text, timestamp, source package, and notification key.
- Encrypts sender/message text at rest with AES-GCM using an Android Keystore key.
- Preserves structured MessagingStyle entries from rapid/grouped notifications when Android exposes them.
- Generates a local PNG evidence card for each newly archived record.
- Provides search, individual PNG export, and full JSON export.
- Records notification removal neutrally; removal is **not** treated as proof that the sender deleted a message.
- Uses no server, cloud account, WhatsApp login, or network permission.

## Requirements

- Android 8.0 (API 26) or newer.
- Notification access must be explicitly enabled by the phone owner.
- WhatsApp must expose message content through the Android notification for it to be archived.

See `UPLOAD_AND_BUILD.md` for the GitHub upload/build instructions and the Delete-for-everyone test.
