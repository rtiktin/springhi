package com.springhi.user.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.Cipher;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Base64;

@Component
public class TaxIdEncryptor {

    private static final int IV_LEN = 12;
    private static final int TAG_BITS = 128;

    private final SecretKey key;
    private final SecureRandom random = new SecureRandom();

    public TaxIdEncryptor(@Value("${app.referral.taxid.secret:dev-taxid-secret-change-me-to-32-bytes}") String secret) {
        try {
            byte[] k = MessageDigest.getInstance("SHA-256").digest(secret.getBytes(StandardCharsets.UTF_8));
            this.key = new SecretKeySpec(k, "AES");
        } catch (Exception e) {
            throw new IllegalStateException("Failed to initialize TaxIdEncryptor", e);
        }
    }

    public String encrypt(String plain) {
        if (plain == null || plain.isBlank()) return null;
        try {
            byte[] iv = new byte[IV_LEN];
            random.nextBytes(iv);
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.ENCRYPT_MODE, key, new GCMParameterSpec(TAG_BITS, iv));
            byte[] ct = cipher.doFinal(plain.trim().getBytes(StandardCharsets.UTF_8));
            return Base64.getEncoder().encodeToString(iv) + "." + Base64.getEncoder().encodeToString(ct);
        } catch (Exception e) {
            throw new IllegalStateException("Failed to encrypt tax id", e);
        }
    }

    public String decrypt(String stored) {
        if (stored == null || stored.isBlank()) return null;
        try {
            String[] parts = stored.split("\\.", 2);
            if (parts.length != 2) return null;
            byte[] iv = Base64.getDecoder().decode(parts[0]);
            byte[] ct = Base64.getDecoder().decode(parts[1]);
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.DECRYPT_MODE, key, new GCMParameterSpec(TAG_BITS, iv));
            return new String(cipher.doFinal(ct), StandardCharsets.UTF_8);
        } catch (Exception e) {
            throw new IllegalStateException("Failed to decrypt tax id", e);
        }
    }

    public static String last4(String plain) {
        if (plain == null) return null;
        String digits = plain.replaceAll("\\D", "");
        if (digits.length() < 4) return digits;
        return digits.substring(digits.length() - 4);
    }
}
