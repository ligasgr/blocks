package blocks.secrets.config;

import javax.crypto.Cipher;
import javax.crypto.spec.SecretKeySpec;
import java.util.Base64;

import static java.nio.charset.StandardCharsets.UTF_8;

public class EncryptionUtil {

    private static final String KEY = obtainKey();
    private static final SecretKeySpec AES_KEY = new SecretKeySpec(KEY.getBytes(UTF_8), "AES");

    public static String encrypt(String value) {
        try {
            Cipher cipher = Cipher.getInstance("AES");
            cipher.init(Cipher.ENCRYPT_MODE, AES_KEY);
            return Base64.getEncoder().encodeToString(cipher.doFinal(value.getBytes(UTF_8)));
        } catch (Exception e) {
            throw new RuntimeException("Failed to encrypt value", e);
        }
    }

    public static String decrypt(String value) {
        try {
            Cipher cipher = Cipher.getInstance("AES");
            cipher.init(Cipher.DECRYPT_MODE, AES_KEY);
            return new String(cipher.doFinal(Base64.getDecoder().decode(value)), UTF_8);
        } catch (Exception e) {
            throw new RuntimeException("Failed to decrypt value", e);
        }
    }

    private static String obtainKey() {
        String ekey = System.getenv("EKEY");
        if (ekey == null || ekey.trim().equals("")) {
            throw new RuntimeException("Failed to initialize EncryptionUtil. Key was missing");
        }
        return ekey;
    }
}
