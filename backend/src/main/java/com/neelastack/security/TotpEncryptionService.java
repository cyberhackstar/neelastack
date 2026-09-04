package com.neelastack.security;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.util.Base64;

/**
 * Encrypts TOTP secrets at rest with AES-256-GCM, keyed from a single server-side
 * secret (app.mfa.encryption-key / MFA_ENCRYPTION_KEY -- validated on startup in prod
 * by StartupSecretsValidator). This is the "small AttributeConverter using a
 * server-side key from env" the master prompt describes for Section 2, kept as a plain
 * service rather than a JPA @Converter so User's own field stays a simple encrypted
 * String and this stays independently unit-testable.
 *
 * Output format: base64(iv) + ":" + base64(ciphertext+tag). A fresh random IV is
 * generated per encryption call -- GCM must never reuse an (key, IV) pair.
 */
@Component
public class TotpEncryptionService {

    private static final String ALGORITHM = "AES/GCM/NoPadding";
    private static final int IV_LENGTH_BYTES = 12;
    private static final int TAG_LENGTH_BITS = 128;

    @Value("${app.mfa.encryption-key}")
    private String base64Key;

    private final SecureRandom secureRandom = new SecureRandom();

    public String encrypt(String plaintext) {
        try {
            byte[] iv = new byte[IV_LENGTH_BYTES];
            secureRandom.nextBytes(iv);

            Cipher cipher = Cipher.getInstance(ALGORITHM);
            cipher.init(Cipher.ENCRYPT_MODE, key(), new GCMParameterSpec(TAG_LENGTH_BITS, iv));
            byte[] ciphertext = cipher.doFinal(plaintext.getBytes(StandardCharsets.UTF_8));

            return Base64.getEncoder().encodeToString(iv) + ":" + Base64.getEncoder().encodeToString(ciphertext);
        } catch (Exception e) {
            throw new IllegalStateException("Failed to encrypt TOTP secret", e);
        }
    }

    public String decrypt(String encoded) {
        try {
            String[] parts = encoded.split(":", 2);
            byte[] iv = Base64.getDecoder().decode(parts[0]);
            byte[] ciphertext = Base64.getDecoder().decode(parts[1]);

            Cipher cipher = Cipher.getInstance(ALGORITHM);
            cipher.init(Cipher.DECRYPT_MODE, key(), new GCMParameterSpec(TAG_LENGTH_BITS, iv));
            return new String(cipher.doFinal(ciphertext), StandardCharsets.UTF_8);
        } catch (Exception e) {
            throw new IllegalStateException("Failed to decrypt TOTP secret", e);
        }
    }

    private SecretKeySpec key() {
        byte[] raw = Base64.getDecoder().decode(base64Key);
        return new SecretKeySpec(raw, "AES");
    }
}
