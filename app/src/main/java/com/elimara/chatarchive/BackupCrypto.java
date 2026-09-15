package com.elimara.chatarchive;

import java.io.ByteArrayOutputStream;
import java.nio.ByteBuffer;
import java.security.SecureRandom;

import javax.crypto.Cipher;
import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.PBEKeySpec;
import javax.crypto.spec.SecretKeySpec;

public final class BackupCrypto {
    private static final byte[] MAGIC = new byte[]{'C','A','B','1'};
    private static final int ITERATIONS = 150000;
    private BackupCrypto() {}

    public static byte[] encrypt(byte[] plain, char[] password) throws Exception {
        byte[] salt = new byte[16];
        byte[] iv = new byte[12];
        SecureRandom random = new SecureRandom();
        random.nextBytes(salt); random.nextBytes(iv);
        SecretKeySpec key = derive(password, salt);
        Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
        cipher.init(Cipher.ENCRYPT_MODE, key, new GCMParameterSpec(128, iv));
        byte[] encrypted = cipher.doFinal(plain);
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        out.write(MAGIC); out.write(salt); out.write(iv); out.write(encrypted);
        return out.toByteArray();
    }

    public static byte[] decrypt(byte[] data, char[] password) throws Exception {
        if (data == null || data.length < 32) throw new IllegalArgumentException("Invalid backup");
        ByteBuffer buffer = ByteBuffer.wrap(data);
        byte[] magic = new byte[4]; buffer.get(magic);
        for (int i=0;i<4;i++) if (magic[i] != MAGIC[i]) throw new IllegalArgumentException("Invalid backup");
        byte[] salt = new byte[16]; buffer.get(salt);
        byte[] iv = new byte[12]; buffer.get(iv);
        byte[] encrypted = new byte[buffer.remaining()]; buffer.get(encrypted);
        Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
        cipher.init(Cipher.DECRYPT_MODE, derive(password, salt), new GCMParameterSpec(128, iv));
        return cipher.doFinal(encrypted);
    }

    private static SecretKeySpec derive(char[] password, byte[] salt) throws Exception {
        PBEKeySpec spec = new PBEKeySpec(password, salt, ITERATIONS, 256);
        byte[] bytes = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(spec).getEncoded();
        spec.clearPassword();
        return new SecretKeySpec(bytes, "AES");
    }
}
