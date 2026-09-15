package com.elimara.chatarchive;

import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

import androidx.fragment.app.FragmentActivity;

public class UpgradeActivity extends FragmentActivity
        implements PlayBillingManager.Listener {

    public static final String EXTRA_FIRST_RUN = "first_run";

    private PlayBillingManager billing;
    private TextView statusText;
    private TextView priceText;
    private Button buyButton;
    private Button restoreButton;
    private Button continueButton;
    private boolean firstRun;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_upgrade);

        firstRun = getIntent().getBooleanExtra(EXTRA_FIRST_RUN, false);

        statusText = findViewById(R.id.proStatus);
        priceText = findViewById(R.id.proPrice);
        buyButton = findViewById(R.id.buyProButton);
        restoreButton = findViewById(R.id.restorePurchaseButton);
        continueButton = findViewById(R.id.continueFreeButton);

        buyButton.setOnClickListener(v -> billing.launchPurchase(this));
        restoreButton.setOnClickListener(v -> billing.restorePurchases());
        continueButton.setOnClickListener(v -> continueToApp());

        billing = new PlayBillingManager(this, this);

        updateUi(
                "",
                ProAccess.isPro(this)
        );

        billing.start();
    }

    private void continueToApp() {
        if (firstRun) {
            ProAccess.markWelcomeSeen(this);
            Intent intent = new Intent(this, MainActivity.class);
            intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP);
            startActivity(intent);
        }
        finish();
    }

    private void updateUi(String localizedPrice, boolean proOwned) {
        if (proOwned) {
            statusText.setVisibility(View.VISIBLE);
            statusText.setText("Lifetime Pro active through Google Play ✓");
            statusText.setTextColor(getColor(R.color.wa_success));

            priceText.setText("Your purchase is restored on supported devices through Google Play.");
            buyButton.setText("Lifetime Pro active");
            buyButton.setEnabled(false);
            restoreButton.setVisibility(View.GONE);
            continueButton.setText(firstRun ? "Open ChatArchive" : "Back to ChatArchive");
        } else {
            statusText.setVisibility(View.VISIBLE);
            statusText.setText("Free plan active");
            statusText.setTextColor(getColor(R.color.wa_text_muted));

            if (localizedPrice != null && !localizedPrice.trim().isEmpty()) {
                priceText.setText(
                        localizedPrice
                                + " one-time • Lifetime Pro • no subscription");
                buyButton.setText("Unlock Lifetime Pro • " + localizedPrice);
            } else {
                priceText.setText(
                        "One-time purchase • local price shown by Google Play");
                buyButton.setText("Unlock Lifetime Pro");
            }

            buyButton.setEnabled(true);
            restoreButton.setVisibility(View.VISIBLE);
            continueButton.setText(firstRun ? "Continue with Free" : "Not now");
        }
    }

    @Override
    public void onBillingReady(String localizedPrice, boolean proOwned) {
        runOnUiThread(() -> updateUi(localizedPrice, proOwned));
    }

    @Override
    public void onEntitlementChanged(boolean proOwned) {
        runOnUiThread(() ->
                updateUi(
                        billing == null ? "" : billing.getLocalizedPrice(),
                        proOwned
                )
        );
    }

    @Override
    public void onBillingMessage(String message) {
        if (message == null || message.trim().isEmpty()) return;
        runOnUiThread(() ->
                Toast.makeText(
                        this,
                        message,
                        Toast.LENGTH_LONG
                ).show()
        );
    }

    @Override
    protected void onDestroy() {
        if (billing != null) billing.end();
        super.onDestroy();
    }
}
