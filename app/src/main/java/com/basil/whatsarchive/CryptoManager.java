package com.basil.whatsarchive;

import android.security.keystore.KeyGenParameterSpec;
import android.security.keystore.KeyProperties;
import android.util.Base64;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.KeyStore;

import javax.crypto.Cipher;
import javax.crypto.KeyGenerator;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;

public class CryptoManager {
    private static final String KEYSTORE = "AndroidKeyStore";
    private static final String ALIAS = "WhatsArchiveKeyV1";

    private SecretKey getOrCreateKey() throws Exception {
        KeyStore keyStore = KeyStore.getInstance(KEYSTORE);
        keyStore.load(null);
        if (keyStore.containsAlias(ALIAS)) {
            return ((KeyStore.SecretKeyEntry) keyStore.getEntry(ALIAS, null)).getSecretKey();
        }

        KeyGenerator keyGenerator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, KEYSTORE);
        KeyGenParameterSpec spec = new KeyGenParameterSpec.Builder(
                ALIAS,
                KeyProperties.PURPOSE_ENCRYPT | KeyProperties.PURPOSE_DECRYPT)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setKeySize(256)
                .build();
        keyGenerator.init(spec);
        return keyGenerator.generateKey();
    }

    public String encrypt(String value) {
        if (value == null) value = "";
        try {
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.ENCRYPT_MODE, getOrCreateKey());
            byte[] iv = cipher.getIV();
            byte[] ciphertext = cipher.doFinal(value.getBytes(StandardCharsets.UTF_8));
            ByteBuffer buffer = ByteBuffer.allocate(4 + iv.length + ciphertext.length);
            buffer.putInt(iv.length);
            buffer.put(iv);
            buffer.put(ciphertext);
            return Base64.encodeToString(buffer.array(), Base64.NO_WRAP);
        } catch (Exception e) {
            throw new IllegalStateException("Unable to encrypt archive content", e);
        }
    }

    public String decrypt(String encoded) {
        if (encoded == null || encoded.isEmpty()) return "";
        try {
            byte[] all = Base64.decode(encoded, Base64.NO_WRAP);
            ByteBuffer buffer = ByteBuffer.wrap(all);
            int ivLength = buffer.getInt();
            if (ivLength < 12 || ivLength > 16) throw new IllegalArgumentException("Invalid IV");
            byte[] iv = new byte[ivLength];
            buffer.get(iv);
            byte[] ciphertext = new byte[buffer.remaining()];
            buffer.get(ciphertext);

            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.DECRYPT_MODE, getOrCreateKey(), new GCMParameterSpec(128, iv));
            return new String(cipher.doFinal(ciphertext), StandardCharsets.UTF_8);
        } catch (Exception e) {
            return "[Unable to decrypt archived content]";
        }
    }
}
