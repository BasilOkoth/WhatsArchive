package com.elimara.chatarchive;

import android.content.Intent;
import android.os.Bundle;

import androidx.fragment.app.FragmentActivity;

/**
 * Launcher router.
 *
 * First launch: show the Google Play Pro offer with a Continue Free option.
 * Later launches: open ChatArchive directly. Users can upgrade from MainActivity.
 */
public class ActivationActivity extends FragmentActivity {
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        if (!ProAccess.hasSeenWelcome(this) && !ProAccess.isPro(this)) {
            Intent upgrade = new Intent(this, UpgradeActivity.class);
            upgrade.putExtra(UpgradeActivity.EXTRA_FIRST_RUN, true);
            startActivity(upgrade);
            finish();
            return;
        }

        startActivity(new Intent(this, MainActivity.class));
        finish();
    }
}
