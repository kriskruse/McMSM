package dk.mcmsm.util;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.Cipher;
import javax.crypto.KeyGenerator;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFilePermissions;
import java.security.SecureRandom;
import java.util.Base64;

/**
 * AES-GCM symmetric encryption helper backed by a master key stored on disk.
 * On first run the key is auto-generated and written to {@code data/.master.key}
 * (or wherever {@code app.storage.metadata-root} resolves to). On POSIX systems
 * the file is chmod 600. The {@code MCMSM_MASTER_KEY} env var, if set, overrides
 * the on-disk key entirely.
 *
 * <p>Ciphertext format: {@code enc:<base64(iv || ciphertext)>} so callers can
 * detect already-encrypted values and avoid double-encryption.
 */
@Component
public class SecretCipher {

    private static final Logger logger = LoggerFactory.getLogger(SecretCipher.class);
    private static final String CIPHER_TRANSFORM = "AES/GCM/NoPadding";
    private static final String KEY_ALGORITHM = "AES";
    private static final int KEY_SIZE_BITS = 256;
    private static final int IV_LENGTH_BYTES = 12;
    private static final int GCM_TAG_LENGTH_BITS = 128;
    private static final String ENCRYPTED_PREFIX = "enc:";
    private static final String MASTER_KEY_FILE_NAME = ".master.key";
    private static final String ENV_MASTER_KEY = "MCMSM_MASTER_KEY";

    private final SecretKey masterKey;
    private final SecureRandom secureRandom = new SecureRandom();

    /**
     * Loads the master key from env var or the metadata root directory,
     * generating a new key on first run.
     *
     * @param metadataRootPath directory containing JSON metadata files
     */
    public SecretCipher(@Value("${app.storage.metadata-root:data}") String metadataRootPath) {
        var metadataRoot = Path.of(metadataRootPath).toAbsolutePath().normalize();
        this.masterKey = resolveMasterKey(metadataRoot);
    }

    /**
     * Encrypts the given plaintext. Already-encrypted values (those carrying the
     * {@code enc:} prefix) are returned unchanged so encryption is idempotent.
     *
     * @param plaintext value to encrypt; null or blank values are passed through
     * @return encrypted value prefixed with {@code enc:} or the original input
     */
    public String encrypt(String plaintext) {
        if (plaintext == null || plaintext.isEmpty()) {
            return plaintext;
        }
        if (isEncrypted(plaintext)) {
            return plaintext;
        }
        try {
            var iv = new byte[IV_LENGTH_BYTES];
            secureRandom.nextBytes(iv);
            var cipher = Cipher.getInstance(CIPHER_TRANSFORM);
            cipher.init(Cipher.ENCRYPT_MODE, masterKey, new GCMParameterSpec(GCM_TAG_LENGTH_BITS, iv));
            var ciphertext = cipher.doFinal(plaintext.getBytes(StandardCharsets.UTF_8));
            var combined = new byte[iv.length + ciphertext.length];
            System.arraycopy(iv, 0, combined, 0, iv.length);
            System.arraycopy(ciphertext, 0, combined, iv.length, ciphertext.length);
            return ENCRYPTED_PREFIX + Base64.getEncoder().encodeToString(combined);
        } catch (Exception e) {
            throw new IllegalStateException("Failed to encrypt secret", e);
        }
    }

    /**
     * Decrypts an {@code enc:}-prefixed value. Values lacking the prefix are
     * returned unchanged so plaintext migrations and tests stay simple.
     *
     * @param value encrypted value or plaintext
     * @return decrypted plaintext or the original input
     */
    public String decrypt(String value) {
        if (value == null || value.isEmpty()) {
            return value;
        }
        if (!isEncrypted(value)) {
            return value;
        }
        try {
            var combined = Base64.getDecoder().decode(value.substring(ENCRYPTED_PREFIX.length()));
            if (combined.length <= IV_LENGTH_BYTES) {
                throw new IllegalStateException("Encrypted payload is too short");
            }
            var iv = new byte[IV_LENGTH_BYTES];
            var ciphertext = new byte[combined.length - IV_LENGTH_BYTES];
            System.arraycopy(combined, 0, iv, 0, IV_LENGTH_BYTES);
            System.arraycopy(combined, IV_LENGTH_BYTES, ciphertext, 0, ciphertext.length);
            var cipher = Cipher.getInstance(CIPHER_TRANSFORM);
            cipher.init(Cipher.DECRYPT_MODE, masterKey, new GCMParameterSpec(GCM_TAG_LENGTH_BITS, iv));
            return new String(cipher.doFinal(ciphertext), StandardCharsets.UTF_8);
        } catch (Exception e) {
            throw new IllegalStateException("Failed to decrypt secret", e);
        }
    }

    /**
     * Returns {@code true} if the value carries the encrypted-payload prefix.
     *
     * @param value candidate value
     * @return whether the value appears encrypted
     */
    public boolean isEncrypted(String value) {
        return value != null && value.startsWith(ENCRYPTED_PREFIX);
    }

    private SecretKey resolveMasterKey(Path metadataRoot) {
        var envKey = System.getenv(ENV_MASTER_KEY);
        if (envKey != null && !envKey.isBlank()) {
            logger.info("Loaded master key from {} env var.", ENV_MASTER_KEY);
            return decodeKey(envKey.trim());
        }
        var keyPath = metadataRoot.resolve(MASTER_KEY_FILE_NAME);
        try {
            Files.createDirectories(metadataRoot);
            if (Files.exists(keyPath)) {
                var encoded = Files.readString(keyPath, StandardCharsets.UTF_8).trim();
                return decodeKey(encoded);
            }
            var generated = generateKey();
            Files.writeString(keyPath, Base64.getEncoder().encodeToString(generated.getEncoded()), StandardCharsets.UTF_8);
            restrictPermissions(keyPath);
            logger.warn("Generated new master key at {}. Back this file up — losing it makes stored secrets unrecoverable.", keyPath);
            return generated;
        } catch (Exception e) {
            throw new IllegalStateException("Failed to initialize master key at " + keyPath, e);
        }
    }

    private SecretKey generateKey() {
        try {
            var keyGenerator = KeyGenerator.getInstance(KEY_ALGORITHM);
            keyGenerator.init(KEY_SIZE_BITS, secureRandom);
            return keyGenerator.generateKey();
        } catch (Exception e) {
            throw new IllegalStateException("Failed to generate AES master key", e);
        }
    }

    private SecretKey decodeKey(String base64) {
        var raw = Base64.getDecoder().decode(base64);
        if (raw.length != KEY_SIZE_BITS / 8) {
            throw new IllegalStateException("Master key must be " + (KEY_SIZE_BITS / 8) + " bytes; got " + raw.length);
        }
        return new SecretKeySpec(raw, KEY_ALGORITHM);
    }

    private void restrictPermissions(Path keyPath) {
        try {
            if (keyPath.getFileSystem().supportedFileAttributeViews().contains("posix")) {
                Files.setPosixFilePermissions(keyPath, PosixFilePermissions.fromString("rw-------"));
            } else {
                var file = keyPath.toFile();
                var ok = file.setReadable(false, false)
                        && file.setReadable(true, true)
                        && file.setWritable(false, false)
                        && file.setWritable(true, true);
                if (!ok) {
                    logger.warn("Could not restrict permissions on master key file {}", keyPath);
                }
            }
        } catch (Exception e) {
            logger.warn("Could not restrict permissions on master key file {}: {}", keyPath, e.getMessage());
        }
    }
}
