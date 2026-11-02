package com.foodordering.util;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("PBKDF2 Password Hashing & Verification Tests")
class PasswordHasherTest {

    @Test
    @DisplayName("Should generate valid PBKDF2 hash formatted record")
    void shouldGenerateValidPbkdf2Record() {
        String hash = PasswordHasher.hash("MySecurePassword123!");
        assertNotNull(hash);

        String[] parts = hash.split(":");
        assertEquals(4, parts.length);
        assertEquals("PBKDF2WithHmacSHA256", parts[0]);
        assertEquals("65536", parts[1]);
        assertEquals(32, parts[2].length()); // 16 bytes = 32 hex chars
        assertEquals(64, parts[3].length()); // 256 bits = 32 bytes = 64 hex chars
    }

    @Test
    @DisplayName("Should verify matching password successfully")
    void shouldVerifyMatchingPassword() {
        String password = "CorrectHorseBatteryStaple";
        String hash = PasswordHasher.hash(password);

        assertTrue(PasswordHasher.verify(password, hash));
    }

    @Test
    @DisplayName("Should reject incorrect password")
    void shouldRejectIncorrectPassword() {
        String password = "SecretPassword1";
        String hash = PasswordHasher.hash(password);

        assertFalse(PasswordHasher.verify("WrongPassword", hash));
        assertFalse(PasswordHasher.verify("secretpassword1", hash)); // case sensitivity check
        assertFalse(PasswordHasher.verify("", hash));
    }

    @Test
    @DisplayName("Salt uniqueness: repeated hashes of the same password produce distinct salts and outputs")
    void shouldProduceUniqueSalts() {
        String password = "IdenticalPasswordForSaltTest";
        String hash1 = PasswordHasher.hash(password);
        String hash2 = PasswordHasher.hash(password);

        assertNotEquals(hash1, hash2);

        String[] parts1 = hash1.split(":");
        String[] parts2 = hash2.split(":");
        assertNotEquals(parts1[2], parts2[2]); // distinct salts
        assertNotEquals(parts1[3], parts2[3]); // distinct derived keys

        // Both must still verify against the original password
        assertTrue(PasswordHasher.verify(password, hash1));
        assertTrue(PasswordHasher.verify(password, hash2));
    }

    @Test
    @DisplayName("Should support custom iteration counts >= 1,000")
    void shouldSupportCustomIterations() {
        String salt = PasswordHasher.generateSalt();
        String hash = PasswordHasher.hash("AdminPassword", salt, 10000);

        assertTrue(hash.startsWith("PBKDF2WithHmacSHA256:10000:"));
        assertTrue(PasswordHasher.verify("AdminPassword", hash));
    }

    @Test
    @DisplayName("Should reject null or blank inputs when hashing")
    void shouldRejectInvalidInputsWhenHashing() {
        assertThrows(IllegalArgumentException.class, () -> PasswordHasher.hash(null));
        assertThrows(IllegalArgumentException.class, () -> PasswordHasher.hash("  "));
        assertThrows(IllegalArgumentException.class, () -> PasswordHasher.hash("pwd", null, 65536));
        assertThrows(IllegalArgumentException.class, () -> PasswordHasher.hash("pwd", "salt", 500)); // < 1000
    }

    @Test
    @DisplayName("Should return false on malformed or corrupted hash strings without throwing unhandled exceptions")
    void shouldHandleMalformedHashGracefully() {
        String pwd = "testPassword";

        assertFalse(PasswordHasher.verify(pwd, null));
        assertFalse(PasswordHasher.verify(null, "some_hash"));
        assertFalse(PasswordHasher.verify(pwd, ""));
        assertFalse(PasswordHasher.verify(pwd, "invalid_plain_text"));
        assertFalse(PasswordHasher.verify(pwd, "PBKDF2WithHmacSHA256:not_a_number:salt:hash"));
        assertFalse(PasswordHasher.verify(pwd, "MD5:10000:salt:hash")); // wrong algorithm
        assertFalse(PasswordHasher.verify(pwd, "PBKDF2WithHmacSHA256:10:salt:hash")); // iteration count too low
        assertFalse(PasswordHasher.verify(pwd, "PBKDF2WithHmacSHA256:65536:not_hex_salt:not_hex_hash"));
    }
}
