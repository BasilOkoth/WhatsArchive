package com.basil.whatsarchive;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Typeface;
import android.text.Layout;
import android.text.StaticLayout;
import android.text.TextPaint;

import java.io.File;
import java.io.FileOutputStream;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

public class SnapshotRenderer {
    public static String create(Context context, long id, String sender, String body, long timestamp, String packageName) {
        try {
            int width = 1080;
            int horizontal = 72;
            TextPaint senderPaint = new TextPaint(Paint.ANTI_ALIAS_FLAG);
            senderPaint.setColor(Color.rgb(20, 20, 20));
            senderPaint.setTextSize(54f);
            senderPaint.setTypeface(Typeface.DEFAULT_BOLD);

            TextPaint bodyPaint = new TextPaint(Paint.ANTI_ALIAS_FLAG);
            bodyPaint.setColor(Color.rgb(40, 40, 40));
            bodyPaint.setTextSize(42f);

            TextPaint smallPaint = new TextPaint(Paint.ANTI_ALIAS_FLAG);
            smallPaint.setColor(Color.rgb(95, 95, 95));
            smallPaint.setTextSize(28f);

            int contentWidth = width - (horizontal * 2);
            StaticLayout senderLayout = StaticLayout.Builder
                    .obtain(sender == null ? "" : sender, 0, sender == null ? 0 : sender.length(), senderPaint, contentWidth)
                    .setAlignment(Layout.Alignment.ALIGN_NORMAL)
                    .setIncludePad(false)
                    .build();
            StaticLayout bodyLayout = StaticLayout.Builder
                    .obtain(body == null ? "" : body, 0, body == null ? 0 : body.length(), bodyPaint, contentWidth)
                    .setAlignment(Layout.Alignment.ALIGN_NORMAL)
                    .setIncludePad(false)
                    .build();

            String date = new SimpleDateFormat("dd MMM yyyy, HH:mm:ss", Locale.getDefault()).format(new Date(timestamp));
            String source = "Source notification: " + ("com.whatsapp.w4b".equals(packageName) ? "WhatsApp Business" : "WhatsApp");
            String footer = date + "\n" + source + "\nArchived locally by WhatsArchive";
            StaticLayout footerLayout = StaticLayout.Builder
                    .obtain(footer, 0, footer.length(), smallPaint, contentWidth)
                    .setAlignment(Layout.Alignment.ALIGN_NORMAL)
                    .setIncludePad(false)
                    .build();

            int height = 72 + 50 + 50 + senderLayout.getHeight() + 38 + bodyLayout.getHeight() + 60 + footerLayout.getHeight() + 72;
            height = Math.max(height, 620);

            Bitmap bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888);
            Canvas canvas = new Canvas(bitmap);
            canvas.drawColor(Color.WHITE);

            Paint accent = new Paint(Paint.ANTI_ALIAS_FLAG);
            accent.setColor(Color.rgb(23, 107, 91));
            canvas.drawRect(0, 0, width, 22, accent);

            TextPaint headerPaint = new TextPaint(Paint.ANTI_ALIAS_FLAG);
            headerPaint.setColor(Color.rgb(23, 107, 91));
            headerPaint.setTextSize(34f);
            headerPaint.setTypeface(Typeface.DEFAULT_BOLD);
            canvas.drawText("WHATSARCHIVE • NOTIFICATION CAPTURE", horizontal, 82, headerPaint);

            int y = 132;
            canvas.save();
            canvas.translate(horizontal, y);
            senderLayout.draw(canvas);
            canvas.restore();
            y += senderLayout.getHeight() + 38;

            canvas.save();
            canvas.translate(horizontal, y);
            bodyLayout.draw(canvas);
            canvas.restore();
            y += bodyLayout.getHeight() + 60;

            Paint rule = new Paint(Paint.ANTI_ALIAS_FLAG);
            rule.setColor(Color.rgb(220, 220, 220));
            canvas.drawRect(horizontal, y, width - horizontal, y + 2, rule);
            y += 30;

            canvas.save();
            canvas.translate(horizontal, y);
            footerLayout.draw(canvas);
            canvas.restore();

            File dir = new File(context.getFilesDir(), "snapshots");
            if (!dir.exists() && !dir.mkdirs()) return null;
            File file = new File(dir, "message_" + id + ".png");
            try (FileOutputStream out = new FileOutputStream(file)) {
                bitmap.compress(Bitmap.CompressFormat.PNG, 100, out);
            }
            bitmap.recycle();
            return file.getAbsolutePath();
        } catch (Exception e) {
            return null;
        }
    }
}
