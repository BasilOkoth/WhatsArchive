# WhatsArchive v0.2.1 Premium

**Developed by Basil Okoth**

WhatsArchive is a personal Android notification archive for WhatsApp and WhatsApp Business. It captures notification content locally when Android receives it and preserves an independent encrypted archive on the device.

## Premium features

- Automatic live refresh when new WhatsApp notifications are captured
- Encrypted local message storage using Android Keystore AES-GCM
- Biometric/PIN app lock
- Contact filter and status filter
- Search across sender, message text, source and date
- Clear visual separation between sender name and message body
- Possible-deletion indicator when Android reports that a notification was removed
- Timeline: Received → Archived → Notification removed
- Encrypted portable `.wha` backup and restore
- JSON export
- PNG evidence snapshots
- **Save snapshot directly to Gallery → Pictures/WhatsArchive**
- WhatsApp and WhatsApp Business support
- Premium native Android styling

## Important limitation

WhatsArchive can archive only information Android exposes in the notification. A notification being removed does **not** by itself prove that the sender used "Delete for everyone"; therefore the app labels these records as **Possible deletion** rather than making a definitive claim.

## Privacy

The app does not request Internet permission. Message sender/body fields are encrypted locally. Snapshots remain app-private unless you explicitly choose **Save to Gallery** or export them.

## Build

The repository contains `.github/workflows/build-apk.yml`. Push the project to GitHub and the workflow builds `app-debug.apk` automatically.

Build stack:
- Java 17
- Gradle 8.9
- Android API 35
- minSdk 26

## Install

Build/download the debug APK, install it on Android, then enable:

**Settings → Special app access → Notification access → WhatsArchive**

On Android versions that restrict sideloaded sensitive permissions, you may first need to open WhatsArchive's App info menu and choose **Allow restricted settings**.
