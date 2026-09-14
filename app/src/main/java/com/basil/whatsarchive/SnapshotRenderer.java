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
            senderPaint.setColor(Color.rgb(6, 63, 43));
            senderPaint.setTextSize(58f);
            senderPaint.setTypeface(Typeface.DEFAULT_BOLD);

            TextPaint bodyPaint = new TextPaint(Paint.ANTI_ALIAS_FLAG);
            bodyPaint.setColor(Color.rgb(26, 43, 36));
            bodyPaint.setTextSize(42f);

            TextPaint smallPaint = new TextPaint(Paint.ANTI_ALIAS_FLAG);
            smallPaint.setColor(Color.rgb(88, 108, 98));
            smallPaint.setTextSize(28f);

            int contentWidth = width - (horizontal * 2);
            String safeSender = sender == null ? "Unknown chat" : sender;
            String safeBody = body == null ? "" : body;

            StaticLayout senderLayout = StaticLayout.Builder
                    .obtain(safeSender, 0, safeSender.length(), senderPaint, contentWidth)
                    .setAlignment(Layout.Alignment.ALIGN_NORMAL)
                    .setIncludePad(false)
                    .build();

            StaticLayout bodyLayout = StaticLayout.Builder
                    .obtain(safeBody, 0, safeBody.length(), bodyPaint, contentWidth)
                    .setAlignment(Layout.Alignment.ALIGN_NORMAL)
                    .setIncludePad(false)
                    .build();

            String date = new SimpleDateFormat("dd MMM yyyy, HH:mm:ss", Locale.getDefault())
                    .format(new Date(timestamp));
            String source = "Source notification: "
                    + ("com.whatsapp.w4b".equals(packageName) ? "WhatsApp Business" : "WhatsApp");
            String footer = date + "\n" + source
                    + "\nArchived locally by ChatArchive\nDeveloped by Basil Okoth";

            StaticLayout footerLayout = StaticLayout.Builder
                    .obtain(footer, 0, footer.length(), smallPaint, contentWidth)
                    .setAlignment(Layout.Alignment.ALIGN_NORMAL)
                    .setIncludePad(false)
                    .build();

            int height = 72 + 50 + 50 + senderLayout.getHeight()
                    + 38 + bodyLayout.getHeight() + 60
                    + footerLayout.getHeight() + 72;
            height = Math.max(height, 650);

            Bitmap bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888);
            Canvas canvas = new Canvas(bitmap);
            canvas.drawColor(Color.rgb(240, 245, 242));

            Paint accent = new Paint(Paint.ANTI_ALIAS_FLAG);
            accent.setColor(Color.rgb(10, 106, 67));
            canvas.drawRect(0, 0, width, 24, accent);

            TextPaint headerPaint = new TextPaint(Paint.ANTI_ALIAS_FLAG);
            headerPaint.setColor(Color.rgb(6, 69, 47));
            headerPaint.setTextSize(34f);
            headerPaint.setTypeface(Typeface.DEFAULT_BOLD);
            canvas.drawText("CHATARCHIVE • NOTIFICATION CAPTURE", horizontal, 82, headerPaint);

            TextPaint labelPaint = new TextPaint(Paint.ANTI_ALIAS_FLAG);
            labelPaint.setColor(Color.rgb(88, 108, 98));
            labelPaint.setTextSize(24f);
            labelPaint.setTypeface(Typeface.DEFAULT_BOLD);
            canvas.drawText("SENDER", horizontal, 128, labelPaint);

            int y = 148;

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
            rule.setColor(Color.rgb(188, 208, 197));
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
