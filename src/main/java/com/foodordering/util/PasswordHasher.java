package com.foodordering.util;

import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.PBEKeySpec;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.security.spec.InvalidKeySpecException;
import java.util.HexFormat;

/**
 * Production-grade password hashing and verification utility utilizing standard Java
 * cryptographic primitives: {@code PBKDF2WithHmacSHA256} with an independently generated
 * cryptographic salt, high iteration count (65,536 iterations), 256-bit derived key length,
 * and constant-time verification.
 *
 * <p>Format of serialized password records:
 * <pre>
 *   PBKDF2WithHmacSHA256:&lt;iterations&gt;:&lt;saltHex&gt;:&lt;derivedHashHex&gt;
 * </pre>
 * </p>
 */
public final class PasswordHasher {

    public static final String ALGORITHM = "PBKDF2WithHmacSHA256";
    public static final int DEFAULT_ITERATIONS = 65536;
    public static final int SALT_BYTE_LENGTH = 16;
    public static final int KEY_LENGTH_BITS = 256;

    private static final SecureRandom SECURE_RANDOM = new SecureRandom();

    private PasswordHasher() {
        // utility class
    }

    /**
     * Generates a cryptographically strong, random salt encoded as hexadecimal.
     *
     * @return hex-encoded salt string
     */
    public static String generateSalt() {
        byte[] salt = new byte[SALT_BYTE_LENGTH];
        SECURE_RANDOM.nextBytes(salt);
        return HexFormat.of().formatHex(salt);
    }

    /**
     * Derives a cryptographic hash from the plain password using PBKDF2WithHmacSHA256
     * with the specified salt and iteration count.
     *
     * @param plainPassword plain text password; must not be null or blank
     * @param saltHex       hex-encoded cryptographic salt; must not be null or blank
     * @param iterations    number of hashing iterations (&gt;= 1000)
     * @return serialized composite hash in format {@code PBKDF2WithHmacSHA256:<iterations>:<saltHex>:<hashHex>}
     * @throws IllegalArgumentException if inputs are invalid or iterations too low
     */
    public static String hash(String plainPassword, String saltHex, int iterations) {
        if (plainPassword == null || plainPassword.isBlank()) {
            throw new IllegalArgumentException("Password must not be null or blank.");
        }
        if (saltHex == null || saltHex.isBlank()) {
            throw new IllegalArgumentException("Salt must not be null or blank.");
        }
        if (iterations < 1000) {
            throw new IllegalArgumentException("Iteration count must be at least 1,000 (recommended 65,536).");
        }

        byte[] saltBytes;
        try {
            saltBytes = HexFormat.of().parseHex(saltHex.strip());
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("Invalid hex-encoded salt string: " + saltHex, e);
        }

        char[] passwordChars = plainPassword.toCharArray();
        PBEKeySpec spec = new PBEKeySpec(passwordChars, saltBytes, iterations, KEY_LENGTH_BITS);
        try {
            SecretKeyFactory skf = SecretKeyFactory.getInstance(ALGORITHM);
            byte[] hashBytes = skf.generateSecret(spec).getEncoded();
            String hashHex = HexFormat.of().formatHex(hashBytes);
            return ALGORITHM + ":" + iterations + ":" + saltHex.strip() + ":" + hashHex;
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("Cryptographic algorithm " + ALGORITHM + " is not available in current JVM", e);
        } catch (InvalidKeySpecException e) {
            throw new IllegalStateException("Failed to generate PBKDF2 key with supplied parameters", e);
        } finally {
            spec.clearPassword();
        }
    }

    /**
     * Generates a fresh salt and hashes the plain password with standard recommended parameters.
     *
     * @param plainPassword plain text password
     * @return serialized composite hash in format {@code PBKDF2WithHmacSHA256:<iterations>:<saltHex>:<hashHex>}
     */
    public static String hash(String plainPassword) {
        return hash(plainPassword, generateSalt(), DEFAULT_ITERATIONS);
    }

    /**
     * Verifies whether a candidate plain password matches the stored composite hash.
     * Uses constant-time comparison to protect against timing attacks.
     *
     * @param plainPassword candidate plain password
     * @param storedHash    stored composite hash record
     * @return {@code true} if candidate password matches stored hash, {@code false} otherwise
     */
    public static boolean verify(String plainPassword, String storedHash) {
        if (plainPassword == null || storedHash == null || storedHash.isBlank()) {
            return false;
        }

        String[] parts = storedHash.split(":");
        if (parts.length != 4) {
            return false; // Malformed record format
        }

        String algorithm = parts[0];
        if (!ALGORITHM.equalsIgnoreCase(algorithm)) {
            return false; // Unsupported algorithm
        }

        int iterations;
        try {
            iterations = Integer.parseInt(parts[1]);
            if (iterations < 1000) {
                return false;
            }
        } catch (NumberFormatException e) {
            return false;
        }

        String saltHex = parts[2];
        String expectedHashHex = parts[3];

        byte[] expectedHashBytes;
        try {
            expectedHashBytes = HexFormat.of().parseHex(expectedHashHex);
        } catch (IllegalArgumentException e) {
            return false; // Malformed hash hex
        }

        byte[] saltBytes;
        try {
            saltBytes = HexFormat.of().parseHex(saltHex);
        } catch (IllegalArgumentException e) {
            return false; // Malformed salt hex
        }

        char[] passwordChars = plainPassword.toCharArray();
        PBEKeySpec spec = new PBEKeySpec(passwordChars, saltBytes, iterations, expectedHashBytes.length * 8);
        try {
            SecretKeyFactory skf = SecretKeyFactory.getInstance(ALGORITHM);
            byte[] actualHashBytes = skf.generateSecret(spec).getEncoded();
            return MessageDigest.isEqual(actualHashBytes, expectedHashBytes);
        } catch (Exception e) {
            return false;
        } finally {
            spec.clearPassword();
        }
    }
}
