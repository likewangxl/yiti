package com.bank.branch.platform.report.support;

import javax.crypto.Cipher;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.util.Base64;

/**
 * SQL 探查前后端传输 AES 加解密。
 * <p>AES-128-ECB + Base64 编码。密钥前后端约定一致。</p>
 */
public final class SqlCryptoUtil {

    private static final String AES_KEY = "yiti-sql-probe-k"; // 16 字节
    private static final String ALGORITHM = "AES";
    private static final String TRANSFORMATION = "AES/ECB/PKCS5Padding";

    private SqlCryptoUtil() {}

    /**
     * AES 加密 → Base64 字符串。
     */
    public static String encrypt(String plainText) {
        try {
            SecretKeySpec key = new SecretKeySpec(AES_KEY.getBytes(StandardCharsets.UTF_8), ALGORITHM);
            Cipher cipher = Cipher.getInstance(TRANSFORMATION);
            cipher.init(Cipher.ENCRYPT_MODE, key);
            byte[] encrypted = cipher.doFinal(plainText.getBytes(StandardCharsets.UTF_8));
            return Base64.getEncoder().encodeToString(encrypted);
        } catch (Exception e) {
            throw new RuntimeException("SQL encrypt failed", e);
        }
    }

    /**
     * Base64 字符串 → AES 解密。
     */
    public static String decrypt(String cipherText) {
        try {
            SecretKeySpec key = new SecretKeySpec(AES_KEY.getBytes(StandardCharsets.UTF_8), ALGORITHM);
            Cipher cipher = Cipher.getInstance(TRANSFORMATION);
            cipher.init(Cipher.DECRYPT_MODE, key);
            byte[] decoded = Base64.getDecoder().decode(cipherText);
            return new String(cipher.doFinal(decoded), StandardCharsets.UTF_8);
        } catch (Exception e) {
            throw new RuntimeException("SQL decrypt failed", e);
        }
    }
}
