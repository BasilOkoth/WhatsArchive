package com.elimara.chatarchive;

import android.app.Activity;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.Toast;

import androidx.biometric.BiometricManager;
import androidx.biometric.BiometricPrompt;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.FragmentActivity;

import java.util.concurrent.Executor;

public class LockActivity extends FragmentActivity {
    private AppSecurity security;
    private EditText pinInput;
    private Button biometricButton;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        security = new AppSecurity(this);

        if (!security.isLockEnabled()) {
            setResult(Activity.RESULT_OK);
            finish();
            return;
        }

        setContentView(R.layout.activity_lock);
        pinInput = findViewById(R.id.lockPin);
        biometricButton = findViewById(R.id.unlockBiometricButton);

        findViewById(R.id.unlockPinButton).setOnClickListener(v -> unlockWithPin());
        findViewById(R.id.exitLockButton).setOnClickListener(v -> {
            setResult(Activity.RESULT_CANCELED);
            finish();
        });
        biometricButton.setOnClickListener(v -> showBiometric());

        boolean canBio = BiometricManager.from(this)
                .canAuthenticate(BiometricManager.Authenticators.BIOMETRIC_STRONG |
                        BiometricManager.Authenticators.DEVICE_CREDENTIAL)
                == BiometricManager.BIOMETRIC_SUCCESS;
        biometricButton.setVisibility(canBio ? View.VISIBLE : View.GONE);
        if (canBio) showBiometric();
    }

    private void unlockWithPin() {
        if (security.verifyPin(pinInput.getText().toString())) {
            setResult(Activity.RESULT_OK);
            finish();
        } else {
            pinInput.setError("Incorrect PIN");
        }
    }

    private void showBiometric() {
        Executor executor = ContextCompat.getMainExecutor(this);
        BiometricPrompt prompt = new BiometricPrompt(this, executor,
                new BiometricPrompt.AuthenticationCallback() {
                    @Override public void onAuthenticationSucceeded(BiometricPrompt.AuthenticationResult result) {
                        super.onAuthenticationSucceeded(result);
                        setResult(Activity.RESULT_OK);
                        finish();
                    }
                    @Override public void onAuthenticationError(int errorCode, CharSequence errString) {
                        super.onAuthenticationError(errorCode, errString);
                        if (errorCode != BiometricPrompt.ERROR_USER_CANCELED &&
                                errorCode != BiometricPrompt.ERROR_NEGATIVE_BUTTON) {
                            Toast.makeText(LockActivity.this, errString, Toast.LENGTH_SHORT).show();
                        }
                    }
                });

        BiometricPrompt.PromptInfo info = new BiometricPrompt.PromptInfo.Builder()
                .setTitle("Unlock ChatArchive")
                .setSubtitle("Use biometrics or your device credential")
                .setAllowedAuthenticators(BiometricManager.Authenticators.BIOMETRIC_STRONG |
                        BiometricManager.Authenticators.DEVICE_CREDENTIAL)
                .build();
        prompt.authenticate(info);
    }

    @Override public void onBackPressed() {
        setResult(Activity.RESULT_CANCELED);
        finish();
    }
}
