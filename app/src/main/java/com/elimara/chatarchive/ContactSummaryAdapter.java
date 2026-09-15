package com.elimara.chatarchive;

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

public class ContactSummaryAdapter extends BaseAdapter {
    private final LayoutInflater inflater;
    private final List<ContactSummary> items = new ArrayList<>();
    private final SimpleDateFormat format = new SimpleDateFormat("dd MMM, HH:mm", Locale.getDefault());

    public ContactSummaryAdapter(Context context) {
        inflater = LayoutInflater.from(context);
    }

    public void setItems(List<ContactSummary> values) {
        items.clear();
        if (values != null) items.addAll(values);
        notifyDataSetChanged();
    }

    @Override public int getCount() { return items.size(); }
    @Override public ContactSummary getItem(int position) { return items.get(position); }
    @Override public long getItemId(int position) { return position; }

    @Override
    public View getView(int position, View convertView, ViewGroup parent) {
        Holder h;
        if (convertView == null) {
            convertView = inflater.inflate(R.layout.row_contact, parent, false);
            h = new Holder();
            h.name = convertView.findViewById(R.id.contactName);
            h.preview = convertView.findViewById(R.id.contactPreview);
            h.meta = convertView.findViewById(R.id.contactMeta);
            h.count = convertView.findViewById(R.id.contactCount);
            convertView.setTag(h);
        } else {
            h = (Holder) convertView.getTag();
        }

        ContactSummary item = getItem(position);
        h.name.setText(item.sender == null || item.sender.trim().isEmpty() ? "Unknown chat" : item.sender);
        String preview = item.latestBody == null ? "" : item.latestBody.replace('\n', ' ');
        if (preview.length() > 120) preview = preview.substring(0, 117) + "...";
        h.preview.setText(preview);

        StringBuilder meta = new StringBuilder(format.format(new Date(item.latestAt)));
        if (item.possibleDeletionCount > 0) meta.append("  •  ").append(item.possibleDeletionCount).append(" possible deletions");
        if (item.identityHint != null && !item.identityHint.trim().isEmpty()) meta.append("  •  ").append(item.identityHint);
        h.meta.setText(meta.toString());
        h.count.setText(String.valueOf(item.messageCount));
        return convertView;
    }

    private static final class Holder {
        TextView name, preview, meta, count;
    }
}
