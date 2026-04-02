package com.bank.branch.platform.common.security.sign;

import com.bank.branch.platform.common.web.exception.BizException;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.Map;
import java.util.TreeMap;
import java.util.stream.Collectors;

/**
 * 签名工具类
 * 基于 HmacSHA256 算法实现参数签名和验签功能
 * 用于接口防篡改和身份验证场景
 */
public class SignatureUtils {
    private static final String HMAC_SHA256 = "HmacSHA256";
    private SignatureUtils() {}

    /**
     * 对参数进行签名
     * 将参数按 key 字典序排列后拼接 timestamp 和 nonce，使用 HmacSHA256 计算签名
     *
     * @param params    业务参数
     * @param timestamp 时间戳
     * @param nonce     随机数
     * @param secretKey 密钥
     * @return Base64 编码的签名字符串
     */
    public static String sign(Map<String, String> params, long timestamp, String nonce, String secretKey) {
        String sortedParams = new TreeMap<>(params).entrySet().stream()
            .map(e -> e.getKey() + "=" + e.getValue())
            .collect(Collectors.joining("&"));
        String signContent = sortedParams + "&timestamp=" + timestamp + "&nonce=" + nonce;
        return hmacSha256(signContent, secretKey);
    }

    /**
     * 验证签名是否正确
     *
     * @param params    业务参数
     * @param timestamp 时间戳
     * @param nonce     随机数
     * @param secretKey 密钥
     * @param signature 待验证的签名
     * @return 签名一致返回 true
     */
    public static boolean verify(Map<String, String> params, long timestamp, String nonce,
                                  String secretKey, String signature) {
        return sign(params, timestamp, nonce, secretKey).equals(signature);
    }

    /**
     * HmacSHA256 计算
     *
     * @param content   待签名内容
     * @param secretKey 密钥
     * @return Base64 编码的签名结果
     */
    private static String hmacSha256(String content, String secretKey) {
        try {
            Mac mac = Mac.getInstance(HMAC_SHA256);
            SecretKeySpec keySpec = new SecretKeySpec(secretKey.getBytes(StandardCharsets.UTF_8), HMAC_SHA256);
            mac.init(keySpec);
            byte[] hash = mac.doFinal(content.getBytes(StandardCharsets.UTF_8));
            return Base64.getEncoder().encodeToString(hash);
        } catch (Exception e) {
            throw new BizException("SIGN_001", "签名计算失败", e);
        }
    }
}
