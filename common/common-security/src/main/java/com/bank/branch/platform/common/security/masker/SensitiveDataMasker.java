package com.bank.branch.platform.common.security.masker;

import java.math.BigDecimal;

/**
 * 敏感数据脱敏工具类
 * 提供手机号、身份证、银行账号、金额等敏感字段的脱敏方法
 * 所有方法均为静态方法，工具类不允许实例化
 */
public class SensitiveDataMasker {
    private SensitiveDataMasker() {}

    /**
     * 手机号脱敏：保留前3后4，中间用****代替
     *
     * @param phone 原始手机号
     * @return 脱敏后的手机号，如 138****5678
     */
    public static String maskPhone(String phone) {
        if (phone == null || phone.length() < 7) return "***";
        return phone.substring(0, 3) + "****" + phone.substring(phone.length() - 4);
    }

    /**
     * 身份证号脱敏：保留前3后4，中间用*代替
     *
     * @param idCard 原始身份证号
     * @return 脱敏后的身份证号，如 110***********1234
     */
    public static String maskIdCard(String idCard) {
        if (idCard == null || idCard.length() < 7) return "***";
        int maskLen = idCard.length() - 3 - 4;
        return idCard.substring(0, 3) + "*".repeat(maskLen) + idCard.substring(idCard.length() - 4);
    }

    /**
     * 银行账号脱敏：只保留后4位，前面用****代替
     *
     * @param account 原始银行账号
     * @return 脱敏后的银行账号，如 ****7890
     */
    public static String maskBankAccount(String account) {
        if (account == null || account.length() < 4) return "****";
        return "****" + account.substring(account.length() - 4);
    }

    /**
     * 金额脱敏：统一返回固定占位符
     *
     * @param amount 原始金额
     * @return 脱敏后的金额字符串 ***.**
     */
    public static String maskAmount(BigDecimal amount) { return "***.**"; }
}
