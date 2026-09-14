# Upload and build WhatsArchive v0.2.1

1. Create or open your GitHub repository.
2. Upload **all files and folders inside this project folder**, including the hidden `.github` folder.
3. Commit to `main`.
4. Open the repository's **Actions** tab.
5. Select **Build WhatsArchive APK**.
6. The push should trigger it automatically; alternatively use **Run workflow**.
7. When the build is green, download the `WhatsArchive-debug-apk` artifact.
8. Extract the artifact and install `app-debug.apk` on your Android phone.

If Android refuses to update an older debug build because of a signing mismatch, uninstall the older WhatsArchive first and then install this build. This full replacement package assumes you do not need to retain the previous local archive.
