ChatArchive adaptive Android icon files

Included files:
- app/src/main/res/mipmap-anydpi-v26/ic_launcher.xml
- app/src/main/res/mipmap-anydpi-v26/ic_launcher_round.xml
- app/src/main/res/drawable/ic_launcher_foreground.png
- app/src/main/res/drawable/ic_launcher_background.xml
- app/src/main/res/mipmap-mdpi/ic_launcher.png
- app/src/main/res/mipmap-hdpi/ic_launcher.png
- app/src/main/res/mipmap-xhdpi/ic_launcher.png
- app/src/main/res/mipmap-xxhdpi/ic_launcher.png
- app/src/main/res/mipmap-xxxhdpi/ic_launcher.png
- matching ic_launcher_round.png files in each mipmap folder

How to use:
1. Upload these folders/files into your Android project.
2. In AndroidManifest.xml, make sure the application tag uses:
   android:icon="@mipmap/ic_launcher"
   android:roundIcon="@mipmap/ic_launcher_round"
3. If your manifest still points to @drawable/ic_whatsarchive, replace it.
4. Rebuild and reinstall the app.

Recommended manifest lines:
<application
    android:icon="@mipmap/ic_launcher"
    android:roundIcon="@mipmap/ic_launcher_round"
    ... >

Important:
- These files are designed to replace the current launcher icon setup.
- The black background helps preserve the neon logo look.
