# ChatArchive — Google Play setup

## Permanent app identity

Use this exact package/application ID in Google Play:

`com.elimara.chatarchive`

This is the permanent package ID intended for the first Google Play release.

ChatArchive v0.6.0 targets Android 16 / API 36.

## Monetization

ChatArchive uses a **Free + Lifetime Pro** model.

### Free
- notification capture
- conversation archive
- search
- normal message viewing/sharing

### Lifetime Pro
- encrypted backup
- restore/import
- JSON export
- premium archive and attachment tools designated as Pro

Create a **one-time product** with this exact product ID:

`chatarchive_pro_lifetime`

Recommended public product name: **ChatArchive Lifetime Pro**

Recommended description: **Unlock encrypted backup, restore/import, archive export and premium ChatArchive tools with one purchase. No recurring subscription.**

Set a base price and review Google Play's localized regional prices. The app displays the localized price returned by Google Play.

## Internal testing

1. Create ChatArchive in Play Console using `com.elimara.chatarchive`.
2. Create and activate `chatarchive_pro_lifetime`.
3. Build and sign an Android App Bundle (`.aab`).
4. Upload it to **Internal testing** first.
5. Add tester accounts and install from the Google Play testing link.
6. Test purchase, cancel, pending payment, restore purchase, reinstall, notification capture, attachment handling, PIN/biometric lock, export and backup.

## Privacy and permissions

Before production review:
- publish the privacy policy included as `PRIVACY_POLICY.md` at a public URL;
- put that URL in the Play Console Privacy Policy field;
- complete Data Safety based on the final production behavior;
- verify the in-app Notification Access disclosure accurately describes the final app;
- do not claim that ChatArchive can recover attachment bytes that Android never exposed.

## Production hardening

The first billing implementation is client-side. Once revenue becomes material, verify purchase tokens through a secure backend using the Google Play Developer API. Never embed service-account credentials in the APK.
