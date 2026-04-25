package com.bank.branch.platform.report.enums;

import org.junit.jupiter.api.Test;

import java.util.Arrays;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * RptErrorCode 25 条基线错误码守护测试（Task M0.3.1）.
 *
 * <p>权威来源：02-后端架构.md §6.5 基线 25 条
 * <ul>
 *   <li>400xx 业务错误 10 条（saved-query / data-version / subject / metric / export-task）</li>
 *   <li>403xx 权限 3 条（DASHBOARD / SQL_PROBE / DATA_SCOPE）</li>
 *   <li>420xx SQL 探查 9 条（含 plan F1 漏项 42004 / 42006 / 42009）</li>
 *   <li>500xx 系统 3 条（含 plan F1 漏项 50002 / 50003 EXPORT_START_FAILED）</li>
 * </ul>
 *
 * <p>M5.4 异步导出再扩展 5 条 J 章导出限制码（RPT-42207~42211），
 * 最终合计 30 条。本测试守护 V1 基线"恰好 25 条"，新增需同步更新本测试。
 */
class RptErrorCodeTest {

    @Test
    void shouldHaveExactly25ErrorCodes_BaseSet() {
        assertThat(RptErrorCode.values()).hasSize(25);
    }

    @Test
    void allCodesShouldStartWithRptPrefix() {
        for (RptErrorCode c : RptErrorCode.values()) {
            assertThat(c.getCode()).startsWith("RPT-");
        }
    }

    @Test
    void shouldContainExpectedBusinessCodes() {
        // 业务码 10 条抽样
        assertThat(RptErrorCode.SAVED_QUERY_NOT_FOUND.getCode()).isEqualTo("RPT-40001");
        assertThat(RptErrorCode.DATA_VERSION_UNAVAILABLE.getCode()).isEqualTo("RPT-40004");
        assertThat(RptErrorCode.EXPORT_TASK_NOT_FOUND.getCode()).isEqualTo("RPT-40009");
        assertThat(RptErrorCode.EXPORT_TASK_NOT_READY.getCode()).isEqualTo("RPT-40010");
        // 权限码 3 条抽样
        assertThat(RptErrorCode.DASHBOARD_NO_ACCESS.getCode()).isEqualTo("RPT-40301");
        assertThat(RptErrorCode.DATA_SCOPE_INSUFFICIENT.getCode()).isEqualTo("RPT-40303");
        // SQL 探查 9 条抽样（含 plan F1 漏项 42004 / 42006 / 42009）
        assertThat(RptErrorCode.SQL_PARSE_FAILED.getCode()).isEqualTo("RPT-42001");
        assertThat(RptErrorCode.SQL_ROW_LIMIT_EXCEEDED.getCode()).isEqualTo("RPT-42004");
        assertThat(RptErrorCode.SQL_EXECUTION_TIMEOUT.getCode()).isEqualTo("RPT-42005");
        assertThat(RptErrorCode.SQL_CONCURRENT_LIMIT.getCode()).isEqualTo("RPT-42006");
        assertThat(RptErrorCode.SQL_LENGTH_EXCEEDED.getCode()).isEqualTo("RPT-42008");
        assertThat(RptErrorCode.SQL_EXECUTION_FAILED.getCode()).isEqualTo("RPT-42009");
        // 系统码 3 条全断言（含 plan F1 漏项 50002 / 50003 EXPORT_START_FAILED）
        assertThat(RptErrorCode.CROSS_MODULE_CALL_FAILED.getCode()).isEqualTo("RPT-50001");
        assertThat(RptErrorCode.CACHE_READ_FAILED.getCode()).isEqualTo("RPT-50002");
        assertThat(RptErrorCode.EXPORT_START_FAILED.getCode()).isEqualTo("RPT-50003");
    }

    @Test
    void allMessagesShouldBeChinese() {
        for (RptErrorCode c : RptErrorCode.values()) {
            assertThat(c.getMsg())
                .as("错误码 %s 的 msg 应包含中文", c.name())
                .matches(".*[\\u4e00-\\u9fa5]+.*");
        }
    }

    @Test
    void codesShouldBeUnique() {
        long distinct = Arrays.stream(RptErrorCode.values())
            .map(RptErrorCode::getCode)
            .distinct()
            .count();
        assertThat(distinct)
            .as("RPT 25 条错误码必须唯一无重复")
            .isEqualTo(25);
    }
}
