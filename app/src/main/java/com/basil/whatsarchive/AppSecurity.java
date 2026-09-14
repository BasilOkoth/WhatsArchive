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
    private static final String KEY_AUTO_LOCK_MS = "auto_lock_ms";
    private static final String KEY_BACKGROUNDED_AT = "backgrounded_at";
    private static final String KEY_FAILED_ATTEMPTS = "failed_attempts";
    private static final String KEY_COOLDOWN_UNTIL = "cooldown_until";

    private static final int ITERATIONS = 120_000;
    private static final int MAX_ATTEMPTS_BEFORE_COOLDOWN = 5;
    private static final long COOLDOWN_MS = 30_000L;

    public static final long AUTO_LOCK_IMMEDIATELY = 0L;
    public static final long AUTO_LOCK_30_SECONDS = 30_000L;
    public static final long AUTO_LOCK_1_MINUTE = 60_000L;
    public static final long AUTO_LOCK_5_MINUTES = 5 * 60_000L;
    public static final long AUTO_LOCK_NEVER = -1L;

    private final SharedPreferences prefs;

    public AppSecurity(Context context) {
        prefs = context.getApplicationContext()
                .getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }

    public boolean isLockEnabled() {
        return prefs.getBoolean(KEY_ENABLED, false)
                && prefs.contains(KEY_HASH)
                && prefs.contains(KEY_SALT);
    }

    public void setLockEnabled(boolean enabled) {
        SharedPreferences.Editor editor = prefs.edit()
                .putBoolean(KEY_ENABLED, enabled);

        if (!enabled) {
            editor.remove(KEY_BACKGROUNDED_AT)
                    .remove(KEY_FAILED_ATTEMPTS)
                    .remove(KEY_COOLDOWN_UNTIL);
        }

        editor.apply();
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

            SharedPreferences.Editor editor = prefs.edit()
                    .putString(KEY_SALT, Base64.encodeToString(salt, Base64.NO_WRAP))
                    .putString(KEY_HASH, Base64.encodeToString(hash, Base64.NO_WRAP))
                    .putBoolean(KEY_ENABLED, true)
                    .putInt(KEY_FAILED_ATTEMPTS, 0)
                    .remove(KEY_COOLDOWN_UNTIL);

            if (!prefs.contains(KEY_AUTO_LOCK_MS)) {
                editor.putLong(KEY_AUTO_LOCK_MS, AUTO_LOCK_IMMEDIATELY);
            }

            editor.apply();
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    /**
     * Verifies a PIN and enforces a short brute-force cooldown.
     */
    public boolean verifyPin(String pin) {
        if (isPinAttemptBlocked()) return false;

        try {
            String saltString = prefs.getString(KEY_SALT, null);
            String hashString = prefs.getString(KEY_HASH, null);

            if (saltString == null || hashString == null || pin == null) {
                recordFailedAttempt();
                return false;
            }

            byte[] salt = Base64.decode(saltString, Base64.NO_WRAP);
            byte[] expected = Base64.decode(hashString, Base64.NO_WRAP);
            byte[] actual = derive(pin.toCharArray(), salt);

            boolean valid = constantTimeEquals(expected, actual);

            if (valid) {
                prefs.edit()
                        .putInt(KEY_FAILED_ATTEMPTS, 0)
                        .remove(KEY_COOLDOWN_UNTIL)
                        .apply();
            } else {
                recordFailedAttempt();
            }

            return valid;
        } catch (Exception e) {
            recordFailedAttempt();
            return false;
        }
    }

    public boolean isPinAttemptBlocked() {
        return getCooldownRemainingMillis() > 0;
    }

    public long getCooldownRemainingMillis() {
        long until = prefs.getLong(KEY_COOLDOWN_UNTIL, 0L);
        long remaining = until - System.currentTimeMillis();

        if (remaining <= 0 && until > 0) {
            prefs.edit().remove(KEY_COOLDOWN_UNTIL).apply();
            return 0L;
        }

        return Math.max(0L, remaining);
    }

    public long getCooldownRemainingSeconds() {
        long remaining = getCooldownRemainingMillis();
        return remaining <= 0 ? 0 : ((remaining + 999L) / 1000L);
    }

    private void recordFailedAttempt() {
        int attempts = prefs.getInt(KEY_FAILED_ATTEMPTS, 0) + 1;

        SharedPreferences.Editor editor = prefs.edit();

        if (attempts >= MAX_ATTEMPTS_BEFORE_COOLDOWN) {
            editor.putInt(KEY_FAILED_ATTEMPTS, 0)
                    .putLong(
                            KEY_COOLDOWN_UNTIL,
                            System.currentTimeMillis() + COOLDOWN_MS
                    );
        } else {
            editor.putInt(KEY_FAILED_ATTEMPTS, attempts);
        }

        editor.apply();
    }

    public void setAutoLockTimeout(long timeoutMs) {
        if (timeoutMs != AUTO_LOCK_IMMEDIATELY
                && timeoutMs != AUTO_LOCK_30_SECONDS
                && timeoutMs != AUTO_LOCK_1_MINUTE
                && timeoutMs != AUTO_LOCK_5_MINUTES
                && timeoutMs != AUTO_LOCK_NEVER) {
            timeoutMs = AUTO_LOCK_IMMEDIATELY;
        }

        prefs.edit().putLong(KEY_AUTO_LOCK_MS, timeoutMs).apply();
    }

    public long getAutoLockTimeout() {
        return prefs.getLong(KEY_AUTO_LOCK_MS, AUTO_LOCK_IMMEDIATELY);
    }

    public String getAutoLockLabel() {
        long timeout = getAutoLockTimeout();

        if (timeout == AUTO_LOCK_NEVER) return "Never";
        if (timeout == AUTO_LOCK_30_SECONDS) return "After 30 seconds";
        if (timeout == AUTO_LOCK_1_MINUTE) return "After 1 minute";
        if (timeout == AUTO_LOCK_5_MINUTES) return "After 5 minutes";
        return "Immediately";
    }

    public void recordBackgrounded() {
        if (!isLockEnabled()) return;

        prefs.edit()
                .putLong(KEY_BACKGROUNDED_AT, System.currentTimeMillis())
                .apply();
    }

    public void clearBackgroundMark() {
        prefs.edit().remove(KEY_BACKGROUNDED_AT).apply();
    }

    public boolean shouldAutoLockNow() {
        if (!isLockEnabled()) return false;

        long timeout = getAutoLockTimeout();
        if (timeout == AUTO_LOCK_NEVER) return false;

        long backgroundedAt = prefs.getLong(KEY_BACKGROUNDED_AT, 0L);
        if (backgroundedAt <= 0L) return false;

        long elapsed = System.currentTimeMillis() - backgroundedAt;

        // If the wall clock moved backwards, prefer the secure behavior.
        if (elapsed < 0L) return true;

        return elapsed >= timeout;
    }

    private byte[] derive(char[] pin, byte[] salt) throws Exception {
        PBEKeySpec spec = new PBEKeySpec(pin, salt, ITERATIONS, 256);

        try {
            return SecretKeyFactory
                    .getInstance("PBKDF2WithHmacSHA256")
                    .generateSecret(spec)
                    .getEncoded();
        } finally {
            spec.clearPassword();
            Arrays.fill(pin, '\0');
        }
    }

    private boolean constantTimeEquals(byte[] a, byte[] b) {
        if (a == null || b == null || a.length != b.length) return false;

        int diff = 0;
        for (int i = 0; i < a.length; i++) {
            diff |= a[i] ^ b[i];
        }

        return diff == 0;
    }
}
