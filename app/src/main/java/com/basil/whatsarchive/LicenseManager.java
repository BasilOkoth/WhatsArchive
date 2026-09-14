package com.basil.whatsarchive;

import android.content.Context;
import android.content.SharedPreferences;
import android.util.Base64;

import org.json.JSONObject;

import java.nio.charset.StandardCharsets;
import java.security.KeyFactory;
import java.security.PublicKey;
import java.security.SecureRandom;
import java.security.Signature;
import java.security.spec.X509EncodedKeySpec;
import java.util.Locale;

/**
 * Offline commercial licensing for ChatArchive.
 *
 * The APK contains ONLY the public verification key. Genuine licenses are
 * created outside the app using Basil's private signing key. A copied APK can
 * therefore be installed, but it cannot unlock itself on a different device.
 */
public final class LicenseManager {
    private static final String PREFS = "whatsarchive_commercial_license";
    private static final String KEY_INSTALLATION_ID = "installation_id";
    private static final String KEY_LICENSE_CODE = "license_code";
    private static final String PREFIX = "CAPRO1";
    private static final String LEGACY_PREFIX = "WAPRO1";

    // Matching PRIVATE key must NEVER be committed to GitHub or the APK.
    private static final String PUBLIC_KEY_BASE64 =
            "MFkwEwYHKoZIzj0CAQYIKoZIzj0DAQcDQgAEv2qws5+4HVvAozMiF2nl7YgfsdPAAO39162TmrDEXn5zIaqkMcDMcUr9xuYhzJKN5HqIChiv5LR1fjTKDZ4xdA==";

    private final SharedPreferences prefs;

    public LicenseManager(Context context) {
        prefs = context.getApplicationContext()
                .getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }

    public String getInstallationId() {
        String existing = prefs.getString(KEY_INSTALLATION_ID, "");
        if (existing != null && !existing.trim().isEmpty()) {
            return existing.trim().toUpperCase(Locale.ROOT);
        }

        byte[] random = new byte[6];
        new SecureRandom().nextBytes(random);
        StringBuilder hex = new StringBuilder();
        for (byte b : random) {
            hex.append(String.format(Locale.US, "%02X", b));
        }

        String raw = hex.toString();
        String installationId = "WA-" + raw.substring(0, 4)
                + "-" + raw.substring(4, 8)
                + "-" + raw.substring(8, 12);

        prefs.edit().putString(KEY_INSTALLATION_ID, installationId).apply();
        return installationId;
    }

    public boolean isLicensed() {
        String code = prefs.getString(KEY_LICENSE_CODE, "");
        return validate(code).valid;
    }

    public LicenseInfo getLicenseInfo() {
        String code = prefs.getString(KEY_LICENSE_CODE, "");
        Validation validation = validate(code);
        return validation.valid ? validation.info : null;
    }

    public Validation activate(String code) {
        Validation validation = validate(code);
        if (validation.valid) {
            prefs.edit()
                    .putString(KEY_LICENSE_CODE, normalize(code))
                    .apply();
        }
        return validation;
    }

    public void clearLicense() {
        prefs.edit().remove(KEY_LICENSE_CODE).apply();
    }

    public Validation validate(String code) {
        try {
            String normalized = normalize(code);
            if (normalized.isEmpty()) {
                return Validation.error(
                        "Enter the license code you received after payment.");
            }

            String[] parts = normalized.split("\\.");
            if (parts.length != 3 || !(PREFIX.equals(parts[0]) || LEGACY_PREFIX.equals(parts[0]))) {
                return Validation.error(
                        "This is not a valid ChatArchive license code.");
            }

            byte[] payloadBytes = decodeUrlBase64(parts[1]);
            byte[] signatureBytes = decodeUrlBase64(parts[2]);

            if (!verifySignature(payloadBytes, signatureBytes)) {
                return Validation.error(
                        "License signature is invalid or the code was altered.");
            }

            JSONObject payload = new JSONObject(
                    new String(payloadBytes, StandardCharsets.UTF_8));

            if (payload.optInt("v", 0) != 1) {
                return Validation.error(
                        "This license version is not supported.");
            }

            String licensedInstallation = payload
                    .optString("installationId", "")
                    .trim()
                    .toUpperCase(Locale.ROOT);

            String actualInstallation = getInstallationId()
                    .trim()
                    .toUpperCase(Locale.ROOT);

            if (!actualInstallation.equals(licensedInstallation)) {
                return Validation.error(
                        "This license belongs to a different installation. "
                                + "Send the Installation ID shown on this phone to the seller.");
            }

            String plan = payload.optString("plan", "").trim();
            if (!"LIFETIME_PRO".equals(plan)) {
                return Validation.error(
                        "This license plan is not supported by this build.");
            }

            String customer = payload.optString("customer", "").trim();
            long issuedAt = payload.optLong("issuedAt", 0L);

            LicenseInfo info = new LicenseInfo(
                    licensedInstallation,
                    plan,
                    customer,
                    issuedAt
            );
            return Validation.success(info);
        } catch (Exception e) {
            return Validation.error(
                    "The license code could not be verified.");
        }
    }

    private boolean verifySignature(
            byte[] payload,
            byte[] signatureBytes) throws Exception {

        byte[] publicKeyBytes = Base64.decode(
                PUBLIC_KEY_BASE64,
                Base64.DEFAULT);

        PublicKey publicKey = KeyFactory.getInstance("EC")
                .generatePublic(
                        new X509EncodedKeySpec(publicKeyBytes));

        Signature verifier =
                Signature.getInstance("SHA256withECDSA");
        verifier.initVerify(publicKey);
        verifier.update(payload);
        return verifier.verify(signatureBytes);
    }

    private String normalize(String code) {
        return code == null
                ? ""
                : code.replaceAll("\\s+", "").trim();
    }

    private byte[] decodeUrlBase64(String value) {
        String padded = value;
        int remainder = padded.length() % 4;

        if (remainder == 2) padded += "==";
        else if (remainder == 3) padded += "=";
        else if (remainder == 1) {
            throw new IllegalArgumentException("Bad Base64");
        }

        return Base64.decode(
                padded,
                Base64.URL_SAFE | Base64.NO_WRAP);
    }

    public static final class LicenseInfo {
        public final String installationId;
        public final String plan;
        public final String customer;
        public final long issuedAt;

        LicenseInfo(
                String installationId,
                String plan,
                String customer,
                long issuedAt) {
            this.installationId = installationId;
            this.plan = plan;
            this.customer = customer;
            this.issuedAt = issuedAt;
        }

        public String displayPlan() {
            return "LIFETIME_PRO".equals(plan)
                    ? "Lifetime Pro"
                    : plan;
        }
    }

    public static final class Validation {
        public final boolean valid;
        public final String error;
        public final LicenseInfo info;

        private Validation(
                boolean valid,
                String error,
                LicenseInfo info) {
            this.valid = valid;
            this.error = error;
            this.info = info;
        }

        static Validation success(LicenseInfo info) {
            return new Validation(true, "", info);
        }

        static Validation error(String error) {
            return new Validation(false, error, null);
        }
    }
}
