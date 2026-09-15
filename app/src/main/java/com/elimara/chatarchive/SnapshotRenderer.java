package com.elimara.chatarchive;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.text.Layout;
import android.text.StaticLayout;
import android.text.TextPaint;

import java.io.File;
import java.io.FileOutputStream;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

public final class SnapshotRenderer {
    private SnapshotRenderer() {}

    public static String create(Context context, long id, String sender, String body, long postedAt, String packageName) {
        try {
            File dir = new File(context.getFilesDir(), "snapshots");
            if (!dir.exists() && !dir.mkdirs()) return null;
            File target = new File(dir, "message_" + id + ".png");

            int width = 1080;
            int padding = 64;
            TextPaint titlePaint = new TextPaint(Paint.ANTI_ALIAS_FLAG);
            titlePaint.setColor(Color.rgb(6,69,47));
            titlePaint.setTextSize(48f);
            titlePaint.setFakeBoldText(true);

            TextPaint bodyPaint = new TextPaint(Paint.ANTI_ALIAS_FLAG);
            bodyPaint.setColor(Color.rgb(26,43,36));
            bodyPaint.setTextSize(40f);

            TextPaint metaPaint = new TextPaint(Paint.ANTI_ALIAS_FLAG);
            metaPaint.setColor(Color.rgb(88,108,98));
            metaPaint.setTextSize(28f);

            StaticLayout title = new StaticLayout(
                    safe(sender), titlePaint, width - padding*2,
                    Layout.Alignment.ALIGN_NORMAL, 1f, 0f, false);
            StaticLayout message = new StaticLayout(
                    safe(body), bodyPaint, width - padding*2,
                    Layout.Alignment.ALIGN_NORMAL, 1.15f, 0f, false);
            String app = "com.whatsapp.w4b".equals(packageName) ? "WhatsApp Business" : "WhatsApp";
            String metaText = new SimpleDateFormat("dd MMM yyyy, HH:mm", Locale.getDefault())
                    .format(new Date(postedAt)) + " • " + app + "\nArchived locally with ChatArchive";
            StaticLayout meta = new StaticLayout(
                    metaText, metaPaint, width - padding*2,
                    Layout.Alignment.ALIGN_NORMAL, 1.1f, 0f, false);

            int height = padding + title.getHeight() + 38 + message.getHeight() + 46 + meta.getHeight() + padding;
            Bitmap bitmap = Bitmap.createBitmap(width, Math.max(height, 420), Bitmap.Config.ARGB_8888);
            Canvas canvas = new Canvas(bitmap);
            canvas.drawColor(Color.rgb(240,245,242));
            Paint border = new Paint(Paint.ANTI_ALIAS_FLAG);
            border.setStyle(Paint.Style.STROKE);
            border.setStrokeWidth(3f);
            border.setColor(Color.rgb(188,208,197));
            canvas.drawRoundRect(18,18,width-18,bitmap.getHeight()-18,28,28,border);

            canvas.save(); canvas.translate(padding,padding); title.draw(canvas); canvas.restore();
            canvas.save(); canvas.translate(padding,padding+title.getHeight()+38); message.draw(canvas); canvas.restore();
            canvas.save(); canvas.translate(padding,padding+title.getHeight()+38+message.getHeight()+46); meta.draw(canvas); canvas.restore();

            try (FileOutputStream out = new FileOutputStream(target)) {
                bitmap.compress(Bitmap.CompressFormat.PNG, 100, out);
            }
            bitmap.recycle();
            return target.getAbsolutePath();
        } catch (Exception e) {
            return null;
        }
    }

    private static String safe(String value) { return value == null ? "" : value; }
}
