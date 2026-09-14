package com.basil.whatsarchive;

import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

import androidx.fragment.app.FragmentActivity;

public class ActivationActivity extends FragmentActivity {
    private LicenseManager licenseManager;
    private TextView installationIdText;
    private TextView statusText;
    private EditText licenseInput;
    private boolean archiveOpened = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        licenseManager = new LicenseManager(this);

        if (licenseManager.isLicensed()) {
            openArchive();
            return;
        }

        setContentView(R.layout.activity_activation);

        installationIdText =
                findViewById(R.id.activationInstallationId);
        statusText =
                findViewById(R.id.activationStatus);
        licenseInput =
                findViewById(R.id.activationLicenseInput);

        Button copyIdButton =
                findViewById(R.id.copyInstallationIdButton);
        Button pasteLicenseButton =
                findViewById(R.id.pasteLicenseButton);
        Button activateButton =
                findViewById(R.id.activateButton);
        Button exitButton =
                findViewById(R.id.exitButton);

        String installationId =
                licenseManager.getInstallationId();

        installationIdText.setText(installationId);

        copyIdButton.setOnClickListener(
                v -> copyInstallationId());

        pasteLicenseButton.setOnClickListener(
                v -> pasteLicense());

        activateButton.setOnClickListener(
                v -> activate());

        exitButton.setOnClickListener(
                v -> finish());
    }

    @Override
    protected void onResume() {
        super.onResume();

        if (licenseManager != null
                && licenseManager.isLicensed()
                && !archiveOpened) {
            openArchive();
        }
    }

    private void copyInstallationId() {
        ClipboardManager clipboard =
                (ClipboardManager)
                        getSystemService(CLIPBOARD_SERVICE);

        if (clipboard != null) {
            clipboard.setPrimaryClip(
                    ClipData.newPlainText(
                            "ChatArchive Installation ID",
                            licenseManager.getInstallationId()
                    )
            );

            Toast.makeText(
                    this,
                    "Installation ID copied",
                    Toast.LENGTH_SHORT
            ).show();
        }
    }

    private void pasteLicense() {
        ClipboardManager clipboard =
                (ClipboardManager)
                        getSystemService(CLIPBOARD_SERVICE);

        if (clipboard == null
                || !clipboard.hasPrimaryClip()
                || clipboard.getPrimaryClip() == null
                || clipboard.getPrimaryClip().getItemCount() == 0) {
            Toast.makeText(
                    this,
                    "Clipboard is empty",
                    Toast.LENGTH_SHORT
            ).show();
            return;
        }

        CharSequence text =
                clipboard.getPrimaryClip()
                        .getItemAt(0)
                        .coerceToText(this);

        if (text != null) {
            licenseInput.setText(text.toString().trim());
        }
    }

    private void activate() {
        String code =
                licenseInput.getText().toString();

        LicenseManager.Validation validation =
                licenseManager.activate(code);

        if (!validation.valid) {
            statusText.setVisibility(View.VISIBLE);
            statusText.setText(validation.error);
            statusText.setTextColor(
                    getColor(R.color.wa_danger));
            return;
        }

        statusText.setVisibility(View.VISIBLE);
        statusText.setText(
                "Activated • "
                        + validation.info.displayPlan()
                        + " ✓");
        statusText.setTextColor(
                getColor(R.color.wa_success));

        Toast.makeText(
                this,
                "ChatArchive Lifetime Pro activated",
                Toast.LENGTH_LONG
        ).show();

        openArchive();
    }

    private void openArchive() {
        if (archiveOpened) return;
        archiveOpened = true;

        Intent intent =
                new Intent(this, MainActivity.class);
        intent.addFlags(
                Intent.FLAG_ACTIVITY_CLEAR_TOP);

        startActivity(intent);
        finish();
    }
}
