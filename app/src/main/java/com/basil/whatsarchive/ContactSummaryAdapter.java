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

public class ContactSummaryAdapter extends BaseAdapter {
    private final LayoutInflater inflater;
    private final List<ContactSummary> items = new ArrayList<>();
    private final SimpleDateFormat format = new SimpleDateFormat("dd MMM, HH:mm", Locale.getDefault());

    public ContactSummaryAdapter(Context context) {
        inflater = LayoutInflater.from(context);
    }

    public void setItems(List<ContactSummary> summaries) {
        items.clear();
        items.addAll(summaries);
        notifyDataSetChanged();
    }

    @Override public int getCount() { return items.size(); }
    @Override public ContactSummary getItem(int position) { return items.get(position); }
    @Override public long getItemId(int position) { return position; }

    @Override
    public View getView(int position, View convertView, ViewGroup parent) {
        ViewHolder holder;
        if (convertView == null) {
            convertView = inflater.inflate(R.layout.row_contact, parent, false);
            holder = new ViewHolder();
            holder.sender = convertView.findViewById(R.id.contactSender);
            holder.latest = convertView.findViewById(R.id.contactLatest);
            holder.meta = convertView.findViewById(R.id.contactMeta);
            holder.count = convertView.findViewById(R.id.contactCount);
            holder.warning = convertView.findViewById(R.id.contactWarning);
            convertView.setTag(holder);
        } else {
            holder = (ViewHolder) convertView.getTag();
        }

        ContactSummary item = getItem(position);
        String preview = item.latestBody == null ? "" : item.latestBody.replace('\n', ' ').trim();
        if (preview.length() > 90) preview = preview.substring(0, 87) + "...";
        String app = "com.whatsapp.w4b".equals(item.packageName) ? "WA Business" : "WhatsApp";

        holder.sender.setText(item.sender == null || item.sender.trim().isEmpty() ? "Unknown chat" : item.sender);
        holder.latest.setText(preview.isEmpty() ? "No message preview" : preview);
        holder.meta.setText(format.format(new Date(item.latestAt)) + "  •  " + app);
        holder.count.setText(item.messageCount + (item.messageCount == 1 ? " message" : " messages"));

        if (item.possibleDeletionCount > 0) {
            holder.warning.setVisibility(View.VISIBLE);
            holder.warning.setText(item.possibleDeletionCount + " possible deletion" + (item.possibleDeletionCount == 1 ? "" : "s"));
        } else {
            holder.warning.setVisibility(View.GONE);
        }

        return convertView;
    }

    private static class ViewHolder {
        TextView sender;
        TextView latest;
        TextView meta;
        TextView count;
        TextView warning;
    }
}
