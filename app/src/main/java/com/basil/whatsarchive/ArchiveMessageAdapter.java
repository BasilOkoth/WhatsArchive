package com.basil.whatsarchive;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
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

    private final LayoutInflater inflater;
    private final List<ArchiveMessage> items = new ArrayList<>();
    private final SimpleDateFormat format = new SimpleDateFormat("dd MMM, HH:mm", Locale.getDefault());
    private final ActionListener listener;

    public ArchiveMessageAdapter(Context context) {
        this(context, null);
    }

    public ArchiveMessageAdapter(Context context, ActionListener listener) {
        inflater = LayoutInflater.from(context);
        this.listener = listener;
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
            holder.share = convertView.findViewById(R.id.rowShare);
            holder.snapshot = convertView.findViewById(R.id.rowSnapshot);
            convertView.setTag(holder);
        } else {
            holder = (ViewHolder) convertView.getTag();
        }

        ArchiveMessage message = getItem(position);
        String preview = message.body == null ? "" : message.body.replace('\n', ' ');
        if (preview.length() > 180) preview = preview.substring(0, 177) + "...";
        String app = "com.whatsapp.w4b".equals(message.packageName) ? "WA Business" : "WhatsApp";

        holder.sender.setText(message.sender == null ? "Unknown chat" : message.sender);
        holder.body.setText(preview);
        holder.meta.setText(format.format(new Date(message.postedAt)) + "  •  " + app);
        holder.timeline.setText(message.removedAt == null
                ? "Received  ✓   Archived  ✓"
                : "Received  ✓   Archived  ✓   Notification removed  !");

        if (message.isPossiblyDeleted()) {
            holder.badge.setVisibility(View.VISIBLE);
            holder.badge.setText("POSSIBLE DELETION");
            holder.badge.setBackgroundResource(R.drawable.bg_badge_warning);
        } else {
            holder.badge.setVisibility(View.GONE);
        }

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

    private static class ViewHolder {
        View root;
        TextView sender;
        TextView body;
        TextView meta;
        TextView badge;
        TextView timeline;
        TextView share;
        TextView snapshot;
    }
}
