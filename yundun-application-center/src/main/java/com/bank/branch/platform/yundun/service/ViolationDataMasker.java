package com.bank.branch.platform.yundun.service;

/** 云盾对外展示及导出时使用的隐私字段脱敏工具。 */
public final class ViolationDataMasker {

    private static final int ID_NUMBER_MASK_LENGTH = 6;
    private static final int MAX_PERSON_NAME_LENGTH = 4;

    private ViolationDataMasker() {
    }

    /** 按 Unicode code point 脱敏姓名，保留首尾字符。 */
    public static String maskName(String value) {
        if (value == null || value.isEmpty()) {
            return value;
        }
        int[] codePoints = value.codePoints().toArray();
        if (codePoints.length == 1) {
            return value;
        }
        if (codePoints.length == 2) {
            StringBuilder masked = new StringBuilder(value.length());
            masked.appendCodePoint(codePoints[0]);
            return masked.append('*').toString();
        }

        StringBuilder masked = new StringBuilder(value.length());
        masked.appendCodePoint(codePoints[0]);
        masked.append("*".repeat(codePoints.length - 2));
        masked.appendCodePoint(codePoints[codePoints.length - 1]);
        return masked.toString();
    }

    /** 将证件号码末六个 Unicode code point 替换为星号，短号码全部替换。 */
    public static String maskIdNumber(String value) {
        if (value == null || value.isEmpty()) {
            return value;
        }
        int[] codePoints = value.codePoints().toArray();
        int keepLength = Math.max(0, codePoints.length - ID_NUMBER_MASK_LENGTH);
        StringBuilder masked = new StringBuilder(value.length());
        for (int index = 0; index < keepLength; index++) {
            masked.appendCodePoint(codePoints[index]);
        }
        masked.append("*".repeat(codePoints.length - keepLength));
        return masked.toString();
    }

    /** 仅将一至四个 Unicode HAN 字符组成的客户名称视为人名并脱敏。 */
    public static String maskChineseName(String value) {
        if (value == null || value.isEmpty()) {
            return value;
        }
        int[] codePoints = value.codePoints().toArray();
        if (codePoints.length > MAX_PERSON_NAME_LENGTH) {
            return value;
        }
        boolean allHan = value.codePoints().allMatch(
                codePoint -> Character.UnicodeScript.of(codePoint) == Character.UnicodeScript.HAN);
        return allHan ? maskName(value) : value;
    }
}
