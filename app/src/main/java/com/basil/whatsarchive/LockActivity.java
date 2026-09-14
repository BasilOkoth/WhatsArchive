package com.basil.whatsarchive;

import android.app.AlertDialog;
import android.os.Bundle;
import android.text.InputType;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

import androidx.biometric.BiometricManager;
import androidx.biometric.BiometricPrompt;
import androidx.fragment.app.FragmentActivity;

import java.util.concurrent.Executor;

public class LockActivity extends FragmentActivity {
    private AppSecurity security;
    private EditText pinInput;
    private TextView statusText;
    private TextView timeoutText;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        security = new AppSecurity(this);

        if (!security.isLockEnabled()) {
            finish();
            return;
        }

        setContentView(R.layout.activity_lock);

        pinInput = findViewById(R.id.lockPinInput);
        statusText = findViewById(R.id.lockStatus);
        timeoutText = findViewById(R.id.lockTimeoutText);

        Button unlockButton = findViewById(R.id.lockUnlockButton);
        Button biometricButton = findViewById(R.id.lockBiometricButton);
        Button settingsButton = findViewById(R.id.lockTimeoutButton);
        Button exitButton = findViewById(R.id.lockExitButton);

        unlockButton.setOnClickListener(v -> unlockWithPin());
        biometricButton.setOnClickListener(v -> requestBiometric(false));
        settingsButton.setOnClickListener(v -> showPinForTimeoutSettings());
        exitButton.setOnClickListener(v -> moveTaskToBack(true));

        updateTimeoutText();

        if (canUseBiometrics()) {
            biometricButton.setVisibility(View.VISIBLE);
            requestBiometric(false);
        } else {
            biometricButton.setVisibility(View.GONE);
        }
    }

    private void unlockWithPin() {
        if (security.isPinAttemptBlocked()) {
            showCooldown();
            return;
        }

        String pin = pinInput.getText().toString();

        if (security.verifyPin(pin)) {
            unlockAndFinish();
        } else {
            if (security.isPinAttemptBlocked()) {
                showCooldown();
            } else {
                pinInput.setError("Incorrect PIN");
                statusText.setText("PIN not accepted");
            }
        }
    }

    private boolean canUseBiometrics() {
        return BiometricManager.from(this)
                .canAuthenticate(
                        BiometricManager.Authenticators.BIOMETRIC_WEAK
                ) == BiometricManager.BIOMETRIC_SUCCESS;
    }

    private void requestBiometric(boolean openSettingsAfter) {
        if (!canUseBiometrics()) {
            Toast.makeText(
                    this,
                    "Biometrics are not available on this phone",
                    Toast.LENGTH_SHORT
            ).show();
            return;
        }

        Executor executor = command -> runOnUiThread(command);

        BiometricPrompt prompt = new BiometricPrompt(
                this,
                executor,
                new BiometricPrompt.AuthenticationCallback() {
                    @Override
                    public void onAuthenticationSucceeded(
                            BiometricPrompt.AuthenticationResult result) {
                        super.onAuthenticationSucceeded(result);

                        if (openSettingsAfter) {
                            showTimeoutDialog();
                        } else {
                            unlockAndFinish();
                        }
                    }

                    @Override
                    public void onAuthenticationError(
                            int errorCode,
                            CharSequence errString) {
                        super.onAuthenticationError(errorCode, errString);
                    }
                }
        );

        BiometricPrompt.PromptInfo info =
                new BiometricPrompt.PromptInfo.Builder()
                        .setTitle("Unlock ChatArchive")
                        .setSubtitle(
                                openSettingsAfter
                                        ? "Authenticate to change auto-lock timing"
                                        : "Private archive protected"
                        )
                        .setNegativeButtonText("Use PIN")
                        .build();

        prompt.authenticate(info);
    }

    private void unlockAndFinish() {
        security.clearBackgroundMark();
        finish();
    }

    private void showCooldown() {
        long seconds = security.getCooldownRemainingSeconds();

        statusText.setText(
                "Too many incorrect PIN attempts. Try again in "
                        + seconds + " seconds."
        );
    }

    private void showPinForTimeoutSettings() {
        if (canUseBiometrics()) {
            requestBiometric(true);
            return;
        }

        final EditText input = new EditText(this);
        input.setHint("Current PIN");
        input.setInputType(
                InputType.TYPE_CLASS_NUMBER
                        | InputType.TYPE_NUMBER_VARIATION_PASSWORD
        );

        AlertDialog dialog =
                new AlertDialog.Builder(this)
                        .setTitle("Security settings")
                        .setMessage(
                                "Authenticate before changing the auto-lock timeout."
                        )
                        .setView(input)
                        .setNegativeButton("Cancel", null)
                        .setPositiveButton("Continue", null)
                        .create();

        dialog.setOnShowListener(d ->
                dialog.getButton(AlertDialog.BUTTON_POSITIVE)
                        .setOnClickListener(v -> {
                            if (security.isPinAttemptBlocked()) {
                                input.setError(
                                        "Try again in "
                                                + security
                                                .getCooldownRemainingSeconds()
                                                + " seconds"
                                );
                                return;
                            }

                            if (security.verifyPin(
                                    input.getText().toString())) {
                                dialog.dismiss();
                                showTimeoutDialog();
                            } else {
                                input.setError("Incorrect PIN");
                            }
                        })
        );

        dialog.show();
    }

    private void showTimeoutDialog() {
        String[] labels = {
                "Immediately",
                "After 30 seconds",
                "After 1 minute",
                "After 5 minutes",
                "Never until the app is closed"
        };

        long[] values = {
                AppSecurity.AUTO_LOCK_IMMEDIATELY,
                AppSecurity.AUTO_LOCK_30_SECONDS,
                AppSecurity.AUTO_LOCK_1_MINUTE,
                AppSecurity.AUTO_LOCK_5_MINUTES,
                AppSecurity.AUTO_LOCK_NEVER
        };

        int checked = 0;
        long current = security.getAutoLockTimeout();

        for (int i = 0; i < values.length; i++) {
            if (values[i] == current) {
                checked = i;
                break;
            }
        }

        final int[] selected = {checked};

        new AlertDialog.Builder(this)
                .setTitle("Auto-lock ChatArchive")
                .setSingleChoiceItems(
                        labels,
                        checked,
                        (dialog, which) -> selected[0] = which
                )
                .setNegativeButton("Cancel", null)
                .setPositiveButton(
                        "Save",
                        (dialog, which) -> {
                            security.setAutoLockTimeout(
                                    values[selected[0]]
                            );
                            updateTimeoutText();

                            Toast.makeText(
                                    this,
                                    "Auto-lock set to "
                                            + security.getAutoLockLabel(),
                                    Toast.LENGTH_SHORT
                            ).show();
                        }
                )
                .show();
    }

    private void updateTimeoutText() {
        timeoutText.setText(
                "Auto-lock: " + security.getAutoLockLabel()
        );
    }

    @Override
    public void onBackPressed() {
        // Do not let Back reveal the archive behind the lock screen.
        moveTaskToBack(true);
    }
}
