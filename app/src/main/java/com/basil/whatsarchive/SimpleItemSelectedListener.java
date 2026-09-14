package com.basil.whatsarchive;

import android.view.View;
import android.widget.AdapterView;

public class SimpleItemSelectedListener implements AdapterView.OnItemSelectedListener {
    private final Runnable action;

    public SimpleItemSelectedListener(Runnable action) {
        this.action = action;
    }

    @Override
    public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
        action.run();
    }

    @Override
    public void onNothingSelected(AdapterView<?> parent) {
    }
}
