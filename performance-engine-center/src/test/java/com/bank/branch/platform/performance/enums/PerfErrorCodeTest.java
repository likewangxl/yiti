package com.bank.branch.platform.performance.enums;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * PerfErrorCode 枚举与 03 §K 权威清单对齐测试.
 *
 * <p>权威来源: docs/modules/performance-engine-center/03-接口设计与报文.md §K「错误码完整汇总」
 * <p>§K 共 25 条编码, 含 METRIC_SLOT_CONFLICT 复用 40901 独立常量, 枚举总数 = 26
 */
class PerfErrorCodeTest {

    @Test
    void k1_400xxCodes_alignWithDoc03SectionK() {
        assertThat(PerfErrorCode.METRIC_NOT_FOUND.getCode()).isEqualTo("PERF-40001");
        assertThat(PerfErrorCode.BIZ_KIND_INVALID.getCode()).isEqualTo("PERF-40002");
        assertThat(PerfErrorCode.KPI_SCHEME_NOT_FOUND.getCode()).isEqualTo("PERF-40003");
        assertThat(PerfErrorCode.TARGET_PLAN_NOT_FOUND.getCode()).isEqualTo("PERF-40004");
        assertThat(PerfErrorCode.SYS_CONTROL_VERSION_NOT_FOUND.getCode()).isEqualTo("PERF-40012");
        assertThat(PerfErrorCode.ALLOC_RELATION_NOT_FOUND.getCode()).isEqualTo("PERF-40014");
        assertThat(PerfErrorCode.IMPORT_BATCH_NOT_FOUND.getCode()).isEqualTo("PERF-40017");
        assertThat(PerfErrorCode.CYCLE_PARAM_INVALID.getCode()).isEqualTo("PERF-40019");
        assertThat(PerfErrorCode.TARGET_ADJUST_APPLY_NOT_FOUND.getCode()).isEqualTo("PERF-40020");
    }

    @Test
    void k2_409xxCodes_alignWithDoc03SectionK() {
        assertThat(PerfErrorCode.METRIC_CODE_DUP.getCode()).isEqualTo("PERF-40901");
        assertThat(PerfErrorCode.METRIC_SLOT_CONFLICT.getCode()).isEqualTo("PERF-40901"); // 复用 K.2 编号
        assertThat(PerfErrorCode.METRIC_HAS_DOWNSTREAM_REF.getCode()).isEqualTo("PERF-40902");
        assertThat(PerfErrorCode.SYS_CONTROL_VERSION_CONFLICT.getCode()).isEqualTo("PERF-40903");
        assertThat(PerfErrorCode.TARGET_ADJUST_APPLY_RUNNING.getCode()).isEqualTo("PERF-40906");
    }

    @Test
    void k3_422xxCodes_alignWithDoc03SectionK() {
        assertThat(PerfErrorCode.VALIDATION_FAILED.getCode()).isEqualTo("PERF-42200");
        assertThat(PerfErrorCode.METRIC_CALC_LOGIC_INVALID.getCode()).isEqualTo("PERF-42201");
        assertThat(PerfErrorCode.KPI_WEIGHT_SUM_INVALID.getCode()).isEqualTo("PERF-42202");
        assertThat(PerfErrorCode.IMPORT_COLUMN_MAPPING_INVALID.getCode()).isEqualTo("PERF-42203");
        assertThat(PerfErrorCode.TRIAL_RUN_TIMEOUT.getCode()).isEqualTo("PERF-42205");
        assertThat(PerfErrorCode.BATCH_QUERY_EXCEEDS_LIMIT.getCode()).isEqualTo("PERF-42206");
        assertThat(PerfErrorCode.EXPORT_ROWS_EXCEEDS_LIMIT.getCode()).isEqualTo("PERF-42207");
        assertThat(PerfErrorCode.EXPORT_TASK_NOT_FOUND.getCode()).isEqualTo("PERF-42208");
        assertThat(PerfErrorCode.EXPORT_TASK_OWNER_MISMATCH.getCode()).isEqualTo("PERF-42209");
        assertThat(PerfErrorCode.EXPORT_FILTER_SCOPE_VIOLATION.getCode()).isEqualTo("PERF-42210");
    }

    @Test
    void k4_500xxCodes_alignWithDoc03SectionK() {
        assertThat(PerfErrorCode.EXPORT_FILE_GENERATE_FAILED.getCode()).isEqualTo("PERF-50002");
        assertThat(PerfErrorCode.CALC_JOB_FAILED.getCode()).isEqualTo("PERF-50007");
    }

    /**
     * 旧的 PERF-40401/40402/40403/40406/40407/40903/40904/40905/40911/40912/40913/40914
     * 均已迁移到 §K 新编号；本测试防止历史编码被意外引用.
     */
    @Test
    void deprecatedCodes_notUsedAnywhere() {
        List<String> deprecated = List.of(
            "PERF-40401", "PERF-40402", "PERF-40403", "PERF-40406", "PERF-40407",
            "PERF-40904", "PERF-40905", "PERF-40911", "PERF-40912", "PERF-40913", "PERF-40914");
        for (PerfErrorCode c : PerfErrorCode.values()) {
            assertThat(deprecated)
                .as("常量 " + c.name() + " 使用了已废弃编号 " + c.getCode())
                .doesNotContain(c.getCode());
        }
    }

    /**
     * Task C2 架构守护测试：枚举常量总数 = 26（§K 的 25 条编码 + METRIC_SLOT_CONFLICT 复用 40901 独立常量）.
     *
     * <p>此测试作为"守护"（第一次运行即 PASS），防止未来在 §K 范围外随意新增枚举常量。
     * 若 §K 授权清单更新，同步修改此数字并更新对应 §K 文档。
     */
    @Test
    void enumSize_equalsSectionKTotal() {
        // §K 共 25 条编码（K.1: 9 + K.2: 4 + K.3: 10 + K.4: 2）
        // METRIC_SLOT_CONFLICT 复用 40901，独立常量 +1 = 26
        assertThat(PerfErrorCode.values()).hasSize(26);
    }

    /**
     * Task C2 架构守护测试：所有枚举常量的编号必须在 §K 授权清单中.
     *
     * <p>防止 §K 范围外的编号（如旧体系的 PERF-50001）被意外引入。
     */
    @Test
    void noLegacyOrUndocumentedCode_exists() {
        List<String> allowedCodes = List.of(
            "PERF-40001", "PERF-40002", "PERF-40003", "PERF-40004",
            "PERF-40012", "PERF-40014", "PERF-40017", "PERF-40019", "PERF-40020",
            "PERF-40901", "PERF-40902", "PERF-40903", "PERF-40906",
            "PERF-42200", "PERF-42201", "PERF-42202", "PERF-42203",
            "PERF-42205", "PERF-42206", "PERF-42207", "PERF-42208", "PERF-42209", "PERF-42210",
            "PERF-50002", "PERF-50007");
        for (PerfErrorCode c : PerfErrorCode.values()) {
            assertThat(allowedCodes)
                .as("常量 " + c.name() + " 编号 " + c.getCode() + " 不在 §K 授权清单")
                .contains(c.getCode());
        }
    }
}
