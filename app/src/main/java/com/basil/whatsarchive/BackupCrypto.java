package com.basil.whatsarchive;

import java.io.ByteArrayOutputStream;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;

import javax.crypto.Cipher;
import javax.crypto.SecretKey;
import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.PBEKeySpec;
import javax.crypto.spec.SecretKeySpec;

public final class BackupCrypto {
    private static final byte[] MAGIC = "WARCH02".getBytes(StandardCharsets.US_ASCII);
    private static final int ITERATIONS = 180_000;
    private static final int SALT_LENGTH = 16;
    private static final int IV_LENGTH = 12;

    private BackupCrypto() {}

    public static byte[] encrypt(byte[] plaintext, char[] password) throws Exception {
        SecureRandom random = new SecureRandom();
        byte[] salt = new byte[SALT_LENGTH];
        byte[] iv = new byte[IV_LENGTH];
        random.nextBytes(salt);
        random.nextBytes(iv);

        SecretKey key = derive(password, salt);
        Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
        cipher.init(Cipher.ENCRYPT_MODE, key, new GCMParameterSpec(128, iv));
        byte[] ciphertext = cipher.doFinal(plaintext);

        ByteArrayOutputStream out = new ByteArrayOutputStream();
        out.write(MAGIC);
        out.write(salt);
        out.write(iv);
        out.write(ciphertext);
        return out.toByteArray();
    }

    public static byte[] decrypt(byte[] encrypted, char[] password) throws Exception {
        int minimum = MAGIC.length + SALT_LENGTH + IV_LENGTH + 16;
        if (encrypted == null || encrypted.length < minimum) throw new IllegalArgumentException("Invalid backup file");
        ByteBuffer buffer = ByteBuffer.wrap(encrypted);
        byte[] magic = new byte[MAGIC.length];
        buffer.get(magic);
        for (int i = 0; i < MAGIC.length; i++) {
            if (magic[i] != MAGIC[i]) throw new IllegalArgumentException("Not a WhatsArchive v0.2 backup");
        }
        byte[] salt = new byte[SALT_LENGTH];
        byte[] iv = new byte[IV_LENGTH];
        buffer.get(salt);
        buffer.get(iv);
        byte[] ciphertext = new byte[buffer.remaining()];
        buffer.get(ciphertext);

        SecretKey key = derive(password, salt);
        Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
        cipher.init(Cipher.DECRYPT_MODE, key, new GCMParameterSpec(128, iv));
        return cipher.doFinal(ciphertext);
    }

    private static SecretKey derive(char[] password, byte[] salt) throws Exception {
        PBEKeySpec spec = new PBEKeySpec(password, salt, ITERATIONS, 256);
        try {
            byte[] encoded = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256")
                    .generateSecret(spec).getEncoded();
            return new SecretKeySpec(encoded, "AES");
        } finally {
            spec.clearPassword();
        }
    }
}
