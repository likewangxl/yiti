package com.bank.branch.platform.yundun.service;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/** 云盾隐私字段脱敏工具的边界测试。 */
class ViolationDataMaskerTest {

    @Test
    void nameMaskMustPreserveFirstAndLastUnicodeCodePoints() {
        assertThat(ViolationDataMasker.maskName(null)).isNull();
        assertThat(ViolationDataMasker.maskName("")).isEmpty();
        assertThat(ViolationDataMasker.maskName("甲")).isEqualTo("甲");
        assertThat(ViolationDataMasker.maskName("甲乙")).isEqualTo("甲*");
        assertThat(ViolationDataMasker.maskName("甲乙丙")).isEqualTo("甲*丙");
        assertThat(ViolationDataMasker.maskName("甲乙丙丁")).isEqualTo("甲**丁");
        assertThat(ViolationDataMasker.maskName("𠮷乙𠀀")).isEqualTo("𠮷*𠀀");
    }

    @Test
    void idNumberMaskMustReplaceLastSixCharactersOrEverything() {
        assertThat(ViolationDataMasker.maskIdNumber(null)).isNull();
        assertThat(ViolationDataMasker.maskIdNumber("")).isEmpty();
        assertThat(ViolationDataMasker.maskIdNumber("12345")).isEqualTo("*****");
        assertThat(ViolationDataMasker.maskIdNumber("123456")).isEqualTo("******");
        assertThat(ViolationDataMasker.maskIdNumber("1234567")).isEqualTo("1******");
        assertThat(ViolationDataMasker.maskIdNumber("11010519491231002X"))
                .isEqualTo("110105194912******");
    }

    @Test
    void clientNameMaskMustOnlyMaskOneToFourHanCharacterPersonNames() {
        assertThat(ViolationDataMasker.maskChineseName(null)).isNull();
        assertThat(ViolationDataMasker.maskChineseName("")).isEmpty();
        assertThat(ViolationDataMasker.maskChineseName("中国银行")).isEqualTo("中**行");
        assertThat(ViolationDataMasker.maskChineseName("张三")).isEqualTo("张*");
        assertThat(ViolationDataMasker.maskChineseName("中国银行股")).isEqualTo("中国银行股");
        assertThat(ViolationDataMasker.maskChineseName("阿里巴巴集团")).isEqualTo("阿里巴巴集团");
        assertThat(ViolationDataMasker.maskChineseName("中国银行A")).isEqualTo("中国银行A");
        assertThat(ViolationDataMasker.maskChineseName("中国 银行")).isEqualTo("中国 银行");
        assertThat(ViolationDataMasker.maskChineseName("中国银行·")).isEqualTo("中国银行·");
    }
}
