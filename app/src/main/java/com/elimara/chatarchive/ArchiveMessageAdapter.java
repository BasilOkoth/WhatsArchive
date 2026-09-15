package com.elimara.chatarchive;

import android.content.Context;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.LinearLayout;
import android.widget.TextView;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;

public class ArchiveMessageAdapter extends BaseAdapter {
    public interface ActionListener {
        void onOpen(ArchiveMessage message);
        void onShare(ArchiveMessage message);
        void onSnapshot(ArchiveMessage message);
    }

    private final Context context;
    private final LayoutInflater inflater;
    private final List<ArchiveMessage> items = new ArrayList<>();
    private final SimpleDateFormat format =
            new SimpleDateFormat("dd MMM, HH:mm", Locale.getDefault());
    private final ActionListener listener;
    private final AttachmentActionController attachmentActions;

    public ArchiveMessageAdapter(Context context) {
        this(context, null);
    }

    public ArchiveMessageAdapter(Context context, ActionListener listener) {
        this.context = context;
        inflater = LayoutInflater.from(context);
        this.listener = listener;
        attachmentActions = new AttachmentActionController(context);
    }

    public void setItems(List<ArchiveMessage> messages) {
        items.clear();
        items.addAll(messages);
        notifyDataSetChanged();
    }

    @Override public int getCount() { return items.size(); }
    @Override public ArchiveMessage getItem(int position) { return items.get(position); }
    @Override public long getItemId(int position) { return items.get(position).id; }

    @Override
    public View getView(int position, View convertView, ViewGroup parent) {
        ViewHolder holder;

        if (convertView == null) {
            convertView = inflater.inflate(R.layout.row_message, parent, false);

            holder = new ViewHolder();
            holder.root = convertView.findViewById(R.id.rowRoot);
            holder.sender = convertView.findViewById(R.id.rowSender);
            holder.body = convertView.findViewById(R.id.rowBody);
            holder.meta = convertView.findViewById(R.id.rowMeta);
            holder.badge = convertView.findViewById(R.id.rowBadge);
            holder.timeline = convertView.findViewById(R.id.rowTimeline);
            holder.attachments = convertView.findViewById(R.id.rowAttachments);
            holder.share = convertView.findViewById(R.id.rowShare);
            holder.snapshot = convertView.findViewById(R.id.rowSnapshot);

            convertView.setTag(holder);
        } else {
            holder = (ViewHolder) convertView.getTag();
        }

        ArchiveMessage message = getItem(position);

        String preview = message.body == null
                ? ""
                : message.body.replace('\n', ' ');

        if (preview.length() > 180) {
            preview = preview.substring(0, 177) + "...";
        }

        String app = "com.whatsapp.w4b".equals(message.packageName)
                ? "WA Business"
                : "WhatsApp";

        holder.sender.setText(message.getMessageSenderLabel());
        holder.body.setText(preview);
        holder.meta.setText(
                format.format(new Date(message.postedAt)) +
                        "  •  " + app
        );

        holder.timeline.setText(
                message.removedAt == null
                        ? "Received  ✓   Archived  ✓"
                        : "Received  ✓   Archived  ✓   Notification removed  !"
        );

        if (message.isPossiblyDeleted()) {
            holder.badge.setVisibility(View.VISIBLE);
            holder.badge.setText("POSSIBLE DELETION");
            holder.badge.setBackgroundResource(R.drawable.bg_badge_warning);
        } else {
            holder.badge.setVisibility(View.GONE);
        }

        renderAttachments(holder.attachments, message);

        if (listener != null) {
            holder.root.setOnClickListener(v -> listener.onOpen(message));
            holder.share.setOnClickListener(v -> listener.onShare(message));
            holder.snapshot.setOnClickListener(v -> listener.onSnapshot(message));

            holder.share.setVisibility(View.VISIBLE);
            holder.snapshot.setVisibility(View.VISIBLE);
        } else {
            holder.root.setOnClickListener(null);
            holder.share.setVisibility(View.GONE);
            holder.snapshot.setVisibility(View.GONE);
        }

        return convertView;
    }

    private void renderAttachments(LinearLayout container, ArchiveMessage message) {
        container.removeAllViews();

        if (message.attachments == null || message.attachments.isEmpty()) {
            container.setVisibility(View.GONE);
            return;
        }

        container.setVisibility(View.VISIBLE);

        for (ArchiveAttachment attachment : message.attachments) {
            LinearLayout card = new LinearLayout(context);
            card.setOrientation(LinearLayout.VERTICAL);
            card.setBackgroundResource(R.drawable.bg_attachment_card);
            card.setPadding(dp(10), dp(9), dp(10), dp(9));

            LinearLayout.LayoutParams cardParams = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT
            );
            cardParams.topMargin = dp(7);
            container.addView(card, cardParams);

            TextView title = new TextView(context);
            title.setText(attachment.getIcon() + "  " + attachment.getDisplayName());
            title.setTextColor(context.getColor(R.color.wa_sender_name));
            title.setTextSize(13f);
            title.setTypeface(title.getTypeface(), android.graphics.Typeface.BOLD);
            title.setMaxLines(2);
            card.addView(title);

            TextView detail = new TextView(context);
            String detailText = attachment.getTypeLabel();
            if (attachment.isAvailable()) {
                detailText += "  •  " + attachment.getHumanSize();
            }
            detail.setText(detailText);
            detail.setTextColor(context.getColor(R.color.wa_text_muted));
            detail.setTextSize(11f);
            detail.setPadding(0, dp(4), 0, 0);
            card.addView(detail);

            String statusText = attachment.captureStatus == null
                    ? ""
                    : attachment.captureStatus.trim();
            if (!statusText.isEmpty() && !attachment.isAvailable()) {
                TextView status = new TextView(context);
                status.setText(statusText);
                status.setTextColor(context.getColor(R.color.wa_warning));
                status.setTextSize(10f);
                status.setPadding(0, dp(5), 0, 0);
                card.addView(status);
            }

            if (attachment.isAvailable()) {
                LinearLayout actions = new LinearLayout(context);
                actions.setOrientation(LinearLayout.HORIZONTAL);
                actions.setGravity(Gravity.CENTER_VERTICAL);
                actions.setPadding(0, dp(8), 0, 0);
                card.addView(actions, new LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.WRAP_CONTENT
                ));

                addAction(actions, "OPEN", () -> attachmentActions.open(attachment), false);
                addAction(actions, "SAVE", () -> attachmentActions.save(attachment), true);
                addAction(actions, "SHARE", () -> attachmentActions.share(attachment), true);
                addAction(actions, "EXPORT", () -> attachmentActions.exportBundle(message, attachment), true);
            }
        }
    }

    private void addAction(
            LinearLayout row,
            String label,
            Runnable action,
            boolean addMargin) {

        TextView chip = new TextView(context);
        chip.setText(label);
        chip.setTextColor(context.getColor(R.color.wa_primary_dark));
        chip.setTextSize(10f);
        chip.setGravity(Gravity.CENTER);
        chip.setTypeface(chip.getTypeface(), android.graphics.Typeface.BOLD);
        chip.setBackgroundResource(R.drawable.bg_action_chip);
        chip.setPadding(dp(5), 0, dp(5), 0);
        chip.setOnClickListener(v -> action.run());

        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                0,
                dp(34),
                1f
        );
        if (addMargin) params.leftMargin = dp(5);
        row.addView(chip, params);
    }

    private int dp(int value) {
        return Math.round(value * context.getResources().getDisplayMetrics().density);
    }

    private static class ViewHolder {
        View root;
        TextView sender;
        TextView body;
        TextView meta;
        TextView badge;
        TextView timeline;
        LinearLayout attachments;
        TextView share;
        TextView snapshot;
    }
}
