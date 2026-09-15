package com.elimara.chatarchive;

import android.content.Context;
import android.content.SharedPreferences;
import android.util.Base64;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;

public final class AppSecurity {
    private static final String PREFS = "chatarchive_security";
    private static final String KEY_PIN_HASH = "pin_hash";
    private static final String KEY_SALT = "pin_salt";
    private static final String KEY_LOCK_ENABLED = "lock_enabled";

    private final SharedPreferences prefs;

    public AppSecurity(Context context) {
        prefs = context.getApplicationContext().getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }

    public boolean isLockEnabled() {
        return prefs.getBoolean(KEY_LOCK_ENABLED, false) && !prefs.getString(KEY_PIN_HASH, "").isEmpty();
    }

    public boolean setPin(String pin) {
        if (pin == null || !pin.matches("\\d{4,8}")) return false;
        try {
            byte[] salt = new byte[16];
            new SecureRandom().nextBytes(salt);
            String hash = hash(pin, salt);
            prefs.edit()
                    .putString(KEY_SALT, Base64.encodeToString(salt, Base64.NO_WRAP))
                    .putString(KEY_PIN_HASH, hash)
                    .putBoolean(KEY_LOCK_ENABLED, true)
                    .apply();
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    public boolean verifyPin(String pin) {
        try {
            String saltText = prefs.getString(KEY_SALT, "");
            String expected = prefs.getString(KEY_PIN_HASH, "");
            if (saltText.isEmpty() || expected.isEmpty()) return false;
            byte[] salt = Base64.decode(saltText, Base64.NO_WRAP);
            return MessageDigest.isEqual(
                    expected.getBytes(StandardCharsets.UTF_8),
                    hash(pin == null ? "" : pin, salt).getBytes(StandardCharsets.UTF_8));
        } catch (Exception e) {
            return false;
        }
    }

    public void setLockEnabled(boolean enabled) {
        prefs.edit().putBoolean(KEY_LOCK_ENABLED, enabled).apply();
    }

    public void clearBackgroundMark() {
        // Kept for compatibility with earlier ChatArchive builds.
    }

    private String hash(String pin, byte[] salt) throws Exception {
        MessageDigest digest = MessageDigest.getInstance("SHA-256");
        digest.update(salt);
        byte[] value = pin.getBytes(StandardCharsets.UTF_8);
        for (int i = 0; i < 50000; i++) {
            digest.update(value);
            value = digest.digest();
            digest.reset();
            digest.update(salt);
        }
        return Base64.encodeToString(value, Base64.NO_WRAP);
    }
}
