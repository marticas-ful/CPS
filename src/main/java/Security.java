import javax.crypto.Cipher;
import javax.crypto.spec.IvParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.util.Base64;

public class Security {

    private static String KEY = "eJ8p2L7tX5qR9vF3nA4kC6bY1mZ0sD8h";
    private static String IV = "t6skcv82nIBe9JSl920nGDkwpSnodU23";

    // AES encryption will be used: https://www.techtarget.com/searchsecurity/definition/Advanced-Encryption-Standard

    public static String encrypt(String plainText) {
        try {
            byte[] keyBytes = KEY.getBytes(StandardCharsets.UTF_8);
            byte[] ivBytes = IV.getBytes(StandardCharsets.UTF_8);

            // Ensure length of 16 bytes
            byte[] keyBytes16 = new byte[16];
            System.arraycopy(keyBytes, 0, keyBytes16, 0, Math.min(keyBytes.length, 16));
            byte[] ivBytes16 = new byte[16];
            System.arraycopy(ivBytes, 0, ivBytes16, 0, Math.min(ivBytes.length, 16));

            SecretKeySpec secretKey = new SecretKeySpec(keyBytes16, "AES");
            IvParameterSpec ivSpec = new IvParameterSpec(ivBytes16);

            Cipher cipher = Cipher.getInstance("AES/CBC/PKCS5Padding");
            cipher.init(Cipher.ENCRYPT_MODE, secretKey, ivSpec);
            byte[] encryptedData = cipher.doFinal(plainText.getBytes(StandardCharsets.UTF_8));
            return Base64.getEncoder().encodeToString(encryptedData);

        } catch (Exception e) {

            return null;

        }
    }

    // Reverse 'encrypt'
    public static String decrypt(String encryptedText) {
        try {
            if (encryptedText == null || encryptedText.isEmpty()) {
                return null;
            }

            byte[] keyBytes = KEY.getBytes(StandardCharsets.UTF_8);
            byte[] ivBytes = IV.getBytes(StandardCharsets.UTF_8);

            byte[] keyBytes16 = new byte[16];
            System.arraycopy(keyBytes, 0, keyBytes16, 0, Math.min(keyBytes.length, 16));

            byte[] ivBytes16 = new byte[16];
            System.arraycopy(ivBytes, 0, ivBytes16, 0, Math.min(ivBytes.length, 16));

            SecretKeySpec secretKey = new SecretKeySpec(keyBytes16, "AES");
            IvParameterSpec ivSpec = new IvParameterSpec(ivBytes16);

            try {
                byte[] encryptedData = Base64.getDecoder().decode(encryptedText);

                Cipher cipher = Cipher.getInstance("AES/CBC/PKCS5Padding");
                cipher.init(Cipher.DECRYPT_MODE, secretKey, ivSpec);

                byte[] decryptedData = cipher.doFinal(encryptedData);

                return new String(decryptedData, StandardCharsets.UTF_8);
            } catch (IllegalArgumentException e) {
                return null;
            } catch (Exception e) {
                return null;
            }
        } catch (Exception e) {
            return null;
        }
    }
}