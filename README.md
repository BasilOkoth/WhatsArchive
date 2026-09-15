# ChatArchive

**A product of Elimara Technologies Limited**  
Created by Basil Okoth.

ChatArchive is a privacy-focused Android utility that creates a local archive from supported WhatsApp and WhatsApp Business notifications. It groups records by conversation, preserves message context, supports local encrypted storage, and can retain attachment bytes when Android exposes a readable attachment URI through the notification.


## Play release identity

- application ID: `com.elimara.chatarchive`
- compile/target SDK: 36
- version: 0.6.0 (`versionCode 14`)

## v0.6.0 highlights

- conversation-based local message archive
- possible-notification-removal indicator
- private attachment retention where Android permits it
- attachment **Open / Save / Share / Export** actions
- message snapshots
- search and conversation grouping
- PIN / biometric app lock
- encrypted archive backup and restore
- JSON export
- Google Play **Free + Lifetime Pro** model
- one-time Play product: `chatarchive_pro_lifetime`
- Elimara Technologies Limited product branding

## Important limitation

ChatArchive cannot reconstruct a photo, document, video or voice note that Android/WhatsApp never exposes to the notification listener. When only attachment metadata is visible, ChatArchive records that fact rather than claiming the file was recovered.

## Build

See `BUILD_APK.md`.

## Google Play

See `PLAY_CONSOLE_SETUP.md`.

## Privacy direction

The archive is designed to remain local to the user's device unless the user explicitly chooses to export/share it. Before publishing, ensure your Play Store privacy policy and Data Safety answers exactly match the final production behavior.
