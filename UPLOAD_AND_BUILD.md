# Upload and build WhatsArchive yourself

## Fastest route: GitHub

1. Create a new empty GitHub repository, for example `whatsarchive`.
2. Extract this ZIP on your computer.
3. Upload **the contents inside the extracted folder** to the root of the repository. Make sure `.github/workflows/build-apk.yml` is included.
4. Commit the files to `main`.
5. Open the repository's **Actions** tab.
6. Open **Build WhatsArchive APK**.
7. If it did not start automatically, click **Run workflow**.
8. When the build is green, open the run and download the artifact named **WhatsArchive-debug-apk**.
9. Extract the downloaded artifact ZIP. Inside is `app-debug.apk`.
10. Transfer `app-debug.apk` to your Android phone and install it. Android may ask you to allow installs from that browser/file manager.

## First phone test

1. Open WhatsArchive.
2. Tap **Enable notification access**.
3. Turn on access for **WhatsArchive**.
4. Ensure WhatsApp notification previews are enabled on the phone.
5. From another phone/account, send a normal text message to this phone.
6. Do not open WhatsApp yet. Open WhatsArchive and confirm the message was archived.
7. On the sender's phone, use **Delete for everyone**.
8. Return to WhatsArchive. The independently archived notification record should remain.

Important: WhatsArchive can only preserve content Android exposed in a notification. If WhatsApp did not generate a notification, previews are hidden, Android suppresses the notification, or media content is not included in the notification, the app cannot reconstruct that missing content.

## Android Studio route

Open the extracted folder in Android Studio, let Gradle sync, then choose **Build > Build APK(s)**. The debug APK is created under `app/build/outputs/apk/debug/`.
