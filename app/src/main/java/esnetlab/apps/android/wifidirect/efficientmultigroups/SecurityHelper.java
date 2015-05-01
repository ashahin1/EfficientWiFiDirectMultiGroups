package esnetlab.apps.android.wifidirect.efficientmultigroups;

import android.util.Base64;

import java.security.SecureRandom;

import javax.crypto.Cipher;
import javax.crypto.KeyGenerator;
import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;


/**
 * @author almozavr ;)
 *         https://github.com/dirong/android-turbo/blob/411c446ec8f9ba3ffe4219f1bc4bd9b6a1abd0da/android-turbo/src/net/dirong/turbo/util/SecurityHelper.java
 */

public class SecurityHelper {

    /* Security */
    private static final int BASE64_FLAGS = Base64.DEFAULT | Base64.NO_WRAP;
    private static final String seedKey = "tworock";
    private static final String TAG = null;

    private static byte[] getRawKey(byte[] seed) throws Exception {
        KeyGenerator kgen = KeyGenerator.getInstance("AES");
        SecureRandom sr = SecureRandom.getInstance("SHA1PRNG");
        sr.setSeed(seed);
        kgen.init(128, sr); // 192 and 256 bits may not be available
        SecretKey skey = kgen.generateKey();
        byte[] raw = skey.getEncoded();
        return raw;
    }

    public static String encodeString(String str) {
        if (str == null)
            return null;

        byte[] result = null;
        try {
            SecretKeySpec skeySpec = new SecretKeySpec(EfficientWiFiP2pGroupsActivity.PASSWORD.getBytes()/*getRawKey(seedKey.getBytes())*/, "AES");
            Cipher cipher = Cipher.getInstance("AES");
            cipher.init(Cipher.ENCRYPT_MODE, skeySpec);

            result = cipher.doFinal(str.getBytes());
        } catch (Exception e) {
            e.printStackTrace();
        }

        return Base64.encodeToString(result, BASE64_FLAGS);
    }

    public static String decodeString(String encrypted) {
        String result = null;
        try {
            SecretKeySpec skeySpec = new SecretKeySpec(EfficientWiFiP2pGroupsActivity.PASSWORD.getBytes()/*getRawKey(seedKey.getBytes())*/, "AES");
            Cipher cipher = Cipher.getInstance("AES");
            cipher.init(Cipher.DECRYPT_MODE, skeySpec);

            byte[] encryptedBytes = Base64.decode(encrypted, BASE64_FLAGS);
            byte[] decrypted = cipher.doFinal(encryptedBytes);
            result = new String(decrypted);
        } catch (Exception e) {
            e.printStackTrace();
        }
        return result;
    }

    public static byte[] encodeBytes(byte[] bytes, int size) {
        String str = new String(bytes, 0, size);
        return encodeString(str).getBytes();
    }

    public static byte[] decodeBytes(byte[] bytes, int size) {
        String str = new String(bytes, 0, size);
        return decodeString(str).getBytes();
    }
}