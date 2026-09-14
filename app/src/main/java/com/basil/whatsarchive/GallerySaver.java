package com.basil.whatsarchive;

import android.content.ContentResolver;
import android.content.ContentValues;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.Build;
import android.os.Environment;
import android.provider.MediaStore;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.OutputStream;

public final class GallerySaver {
    private GallerySaver() {}

    public static Uri savePng(Context context, File source, String displayName) throws Exception {
        if (source == null || !source.exists()) throw new IllegalArgumentException("Snapshot file is missing");
        if (displayName == null || displayName.trim().isEmpty()) displayName = "WhatsArchive_snapshot.png";
        if (!displayName.toLowerCase().endsWith(".png")) displayName += ".png";

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            ContentResolver resolver = context.getContentResolver();
            ContentValues values = new ContentValues();
            values.put(MediaStore.Images.Media.DISPLAY_NAME, displayName);
            values.put(MediaStore.Images.Media.MIME_TYPE, "image/png");
            values.put(MediaStore.Images.Media.RELATIVE_PATH, Environment.DIRECTORY_PICTURES + "/WhatsArchive");
            values.put(MediaStore.Images.Media.IS_PENDING, 1);

            Uri uri = resolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values);
            if (uri == null) throw new IllegalStateException("Could not create Gallery item");

            try (FileInputStream in = new FileInputStream(source);
                 OutputStream out = resolver.openOutputStream(uri)) {
                if (out == null) throw new IllegalStateException("Could not open Gallery output");
                copy(in, out);
            } catch (Exception e) {
                resolver.delete(uri, null, null);
                throw e;
            }

            ContentValues ready = new ContentValues();
            ready.put(MediaStore.Images.Media.IS_PENDING, 0);
            resolver.update(uri, ready, null, null);
            return uri;
        }

        File pictures = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES);
        File dir = new File(pictures, "WhatsArchive");
        if (!dir.exists() && !dir.mkdirs()) throw new IllegalStateException("Could not create WhatsArchive Gallery folder");
        File target = new File(dir, displayName);
        try (FileInputStream in = new FileInputStream(source);
             FileOutputStream out = new FileOutputStream(target)) {
            copy(in, out);
        }
        Uri uri = Uri.fromFile(target);
        Intent scan = new Intent(Intent.ACTION_MEDIA_SCANNER_SCAN_FILE, uri);
        context.sendBroadcast(scan);
        return uri;
    }

    private static void copy(FileInputStream in, OutputStream out) throws Exception {
        byte[] buffer = new byte[8192];
        int read;
        while ((read = in.read(buffer)) != -1) out.write(buffer, 0, read);
        out.flush();
    }
}
