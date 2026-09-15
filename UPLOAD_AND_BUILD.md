# Upload and build

1. Upload the complete contents of this folder to the root of the GitHub repository.
2. Commit to `main`.
3. GitHub Actions will run the Android debug build workflow.
4. Download the `ChatArchive-debug-apk` artifact for device testing.
5. For Google Play, generate a **signed Android App Bundle (AAB)** in Android Studio.
6. Create ChatArchive in Play Console with package `com.elimara.chatarchive`.
7. Upload the AAB to **Internal testing** before production.
8. Create the one-time product `chatarchive_pro_lifetime` before testing billing.

See `PLAY_CONSOLE_SETUP.md` and `PRIVACY_POLICY.md`.
