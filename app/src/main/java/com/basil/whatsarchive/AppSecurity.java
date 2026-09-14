package com.basil.whatsarchive;

import android.content.Context;
import android.content.SharedPreferences;
import android.util.Base64;

import java.security.SecureRandom;
import java.util.Arrays;

import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.PBEKeySpec;

public class AppSecurity {
    private static final String PREFS = "whatsarchive_security";
    private static final String KEY_ENABLED = "lock_enabled";
    private static final String KEY_SALT = "pin_salt";
    private static final String KEY_HASH = "pin_hash";
    private static final int ITERATIONS = 120_000;

    private final SharedPreferences prefs;

    public AppSecurity(Context context) {
        prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }

    public boolean isLockEnabled() {
        return prefs.getBoolean(KEY_ENABLED, false) && prefs.contains(KEY_HASH) && prefs.contains(KEY_SALT);
    }

    public void setLockEnabled(boolean enabled) {
        prefs.edit().putBoolean(KEY_ENABLED, enabled).apply();
    }

    public boolean hasPin() {
        return prefs.contains(KEY_HASH) && prefs.contains(KEY_SALT);
    }

    public boolean setPin(String pin) {
        if (pin == null || !pin.matches("\\d{4,8}")) return false;
        try {
            byte[] salt = new byte[16];
            new SecureRandom().nextBytes(salt);
            byte[] hash = derive(pin.toCharArray(), salt);
            prefs.edit()
                    .putString(KEY_SALT, Base64.encodeToString(salt, Base64.NO_WRAP))
                    .putString(KEY_HASH, Base64.encodeToString(hash, Base64.NO_WRAP))
                    .putBoolean(KEY_ENABLED, true)
                    .apply();
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    public boolean verifyPin(String pin) {
        try {
            String saltString = prefs.getString(KEY_SALT, null);
            String hashString = prefs.getString(KEY_HASH, null);
            if (saltString == null || hashString == null || pin == null) return false;
            byte[] salt = Base64.decode(saltString, Base64.NO_WRAP);
            byte[] expected = Base64.decode(hashString, Base64.NO_WRAP);
            byte[] actual = derive(pin.toCharArray(), salt);
            return constantTimeEquals(expected, actual);
        } catch (Exception e) {
            return false;
        }
    }

    private byte[] derive(char[] pin, byte[] salt) throws Exception {
        PBEKeySpec spec = new PBEKeySpec(pin, salt, ITERATIONS, 256);
        try {
            return SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(spec).getEncoded();
        } finally {
            spec.clearPassword();
            Arrays.fill(pin, '\0');
        }
    }

    private boolean constantTimeEquals(byte[] a, byte[] b) {
        if (a == null || b == null || a.length != b.length) return false;
        int diff = 0;
        for (int i = 0; i < a.length; i++) diff |= a[i] ^ b[i];
        return diff == 0;
    }
}
