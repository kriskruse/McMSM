package dk.mcmsm.util;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SecretCipherTest {

    @Test
    void encryptDecryptRoundtrips(@TempDir Path tempDir) {
        var cipher = new SecretCipher(tempDir.toString());
        var plaintext = "$2a$10$qHYxkSYufHxbpDlwWtxLg.KUQGjf9nMbghpJ5HiZf44Hv03NEC3nu";

        var encrypted = cipher.encrypt(plaintext);

        assertTrue(cipher.isEncrypted(encrypted));
        assertNotEquals(plaintext, encrypted);
        assertEquals(plaintext, cipher.decrypt(encrypted));
    }

    @Test
    void encryptIsIdempotent(@TempDir Path tempDir) {
        var cipher = new SecretCipher(tempDir.toString());
        var encryptedOnce = cipher.encrypt("secret-value");

        var encryptedTwice = cipher.encrypt(encryptedOnce);

        assertEquals(encryptedOnce, encryptedTwice);
    }

    @Test
    void decryptPassesThroughPlaintext(@TempDir Path tempDir) {
        var cipher = new SecretCipher(tempDir.toString());
        assertEquals("not-encrypted", cipher.decrypt("not-encrypted"));
    }

    @Test
    void nullAndEmptyValuesArePassedThrough(@TempDir Path tempDir) {
        var cipher = new SecretCipher(tempDir.toString());
        assertNull(cipher.encrypt(null));
        assertEquals("", cipher.encrypt(""));
        assertNull(cipher.decrypt(null));
        assertEquals("", cipher.decrypt(""));
    }

    @Test
    void masterKeyFilePersistsAcrossInstances(@TempDir Path tempDir) throws Exception {
        var first = new SecretCipher(tempDir.toString());
        var encrypted = first.encrypt("persisted-secret");
        assertTrue(Files.exists(tempDir.resolve(".master.key")));

        var second = new SecretCipher(tempDir.toString());

        assertEquals("persisted-secret", second.decrypt(encrypted));
    }

    @Test
    void cipherTextDiffersBetweenEncryptCalls(@TempDir Path tempDir) {
        var cipher = new SecretCipher(tempDir.toString());
        var a = cipher.encrypt("same-input");
        var b = cipher.encrypt("same-input");
        assertNotEquals(a, b);
        assertFalse(a.isEmpty());
    }
}
