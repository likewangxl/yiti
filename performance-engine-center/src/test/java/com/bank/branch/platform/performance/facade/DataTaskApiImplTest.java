package com.bank.branch.platform.performance.facade;

import com.bank.branch.platform.performance.api.dto.cmd.DataTaskStatusCmd;
import com.bank.branch.platform.performance.support.PerformanceServiceTestBase;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.InjectMocks;

import java.time.Instant;
import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * DataTaskApiImpl 单元测试.
 *
 * <p>V1.1 Task P6.1 阶段：Controller 已通过 {@code @MockBean} 隔离走通 IT；
 * Facade 真实实现在 Task P6.2 交付前暂保留 UOE 占位，本测试守护该契约不被意外破坏.
 *
 * <p>Task P6.2 完成后，本类将被 {@code DataTaskApiImplV11Test} 覆盖的正向行为替代；
 * 在此之前，仍校验方法签名返回 {@link com.bank.branch.platform.performance.api.dto.DataTaskReportResultDTO}
 * 的 UOE 路径，消息串与 {@link PerfCalcApiImpl} / {@link MetricApiImpl} / {@link KpiApiImpl}
 * 保持一致的 "V1.1 delivered"。
 */
class DataTaskApiImplTest extends PerformanceServiceTestBase {

    @InjectMocks
    private DataTaskApiImpl dataTaskApi;

    // ------------------------- UOE 占位契约（Task P6.2 前） -------------------------

    @Test
    @DisplayName("reportDataTaskStatus: Task P6.2 前抛 UnsupportedOperationException")
    void reportDataTaskStatus_throwsUOE() {
        DataTaskStatusCmd cmd = DataTaskStatusCmd.builder()
                .taskId("EXT_TASK_001")
                .dataType("ALLOC_RELATION")
                .dataDate(LocalDate.of(2026, 4, 20))
                .version("v1")
                .status("SUCCESS")
                .rowCount(1000)
                .errorMsg(null)
                .sourceSystem("EXT_DATA_SYNC")
                .reportedAt(Instant.now())
                .build();

        assertThatThrownBy(() -> dataTaskApi.reportDataTaskStatus(cmd))
                .isInstanceOf(UnsupportedOperationException.class)
                .hasMessage("V1.1 delivered");
    }
}
