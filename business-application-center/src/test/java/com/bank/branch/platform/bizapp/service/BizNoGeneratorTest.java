package com.bank.branch.platform.bizapp.service;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * BizNoGenerator 单元测试（TDD）。
 */
@ExtendWith(MockitoExtension.class)
class BizNoGeneratorTest {

    @InjectMocks
    private BizNoGenerator bizNoGenerator;

    @Test
    void generateLoanNo_shouldMatchFormat() {
        // LA + yyyyMMdd(8位) + 6位随机数 = 16位
        String loanNo = bizNoGenerator.generateLoanNo();
        assertThat(loanNo).isNotNull();
        assertThat(loanNo).startsWith("LA");
        assertThat(loanNo).hasSize(16);
        // yyyyMMdd 部分应是数字
        assertThat(loanNo.substring(2, 10)).matches("\\d{8}");
        // 后6位应是数字
        assertThat(loanNo.substring(10)).matches("\\d{6}");
    }

    @Test
    void generateSupportNo_shouldMatchFormat() {
        // SR + yyyyMMdd(8位) + 6位随机数 = 16位
        String supportNo = bizNoGenerator.generateSupportNo();
        assertThat(supportNo).isNotNull();
        assertThat(supportNo).startsWith("SR");
        assertThat(supportNo).hasSize(16);
        // yyyyMMdd 部分应是数字
        assertThat(supportNo.substring(2, 10)).matches("\\d{8}");
        // 后6位应是数字
        assertThat(supportNo.substring(10)).matches("\\d{6}");
    }
}
