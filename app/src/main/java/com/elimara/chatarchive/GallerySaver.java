package com.elimara.chatarchive;

import android.content.ContentValues;
import android.content.Context;
import android.os.Build;
import android.os.Environment;
import android.provider.MediaStore;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.OutputStream;

public final class GallerySaver {
    private GallerySaver() {}

    public static void savePng(Context context, File source, String name) throws Exception {
        if (Build.VERSION.SDK_INT >= 29) {
            ContentValues values = new ContentValues();
            values.put(MediaStore.Images.Media.DISPLAY_NAME, name);
            values.put(MediaStore.Images.Media.MIME_TYPE, "image/png");
            values.put(MediaStore.Images.Media.RELATIVE_PATH, Environment.DIRECTORY_PICTURES + "/ChatArchive");
            values.put(MediaStore.Images.Media.IS_PENDING, 1);
            android.net.Uri uri = context.getContentResolver().insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values);
            if (uri == null) throw new IllegalStateException("Could not create gallery item");
            try (FileInputStream in = new FileInputStream(source);
                 OutputStream out = context.getContentResolver().openOutputStream(uri)) {
                if (out == null) throw new IllegalStateException("No output stream");
                copy(in,out);
            }
            values.clear(); values.put(MediaStore.Images.Media.IS_PENDING, 0);
            context.getContentResolver().update(uri, values, null, null);
        } else {
            File dir = new File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES), "ChatArchive");
            if (!dir.exists() && !dir.mkdirs()) throw new IllegalStateException("Could not create Pictures/ChatArchive");
            try (FileInputStream in = new FileInputStream(source);
                 FileOutputStream out = new FileOutputStream(new File(dir, name))) {
                copy(in,out);
            }
        }
    }

    private static void copy(java.io.InputStream in, java.io.OutputStream out) throws Exception {
        byte[] buffer = new byte[8192]; int read;
        while ((read=in.read(buffer))!=-1) out.write(buffer,0,read);
    }
}
