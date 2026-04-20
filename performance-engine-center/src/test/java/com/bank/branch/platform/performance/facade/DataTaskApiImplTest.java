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
 * <p>覆盖 Plan Task 5.3 (plan L1554-1568) DoD:
 * <ul>
 *   <li>1 个 V1.1 占位方法 {@code reportDataTaskStatus} 抛
 *       {@link UnsupportedOperationException} ("V1.1 delivered")</li>
 * </ul>
 *
 * <p>纯 Mock 测试, 不启动 Spring 容器; 由于 V1.0 Facade 无任何协作者,
 * 本测试类直接 {@link InjectMocks} 空 Bean 验证 UOE 契约。
 *
 * <p>UOE 消息与 {@link PerfCalcApiImpl} / {@link MetricApiImpl} / {@link KpiApiImpl}
 * 保持一致, 消费方可通过消息串统一识别"V1.1 才交付"的占位方法。
 */
class DataTaskApiImplTest extends PerformanceServiceTestBase {

    @InjectMocks
    private DataTaskApiImpl dataTaskApi;

    // ------------------------- V1.1 契约: UOE 占位 -------------------------

    @Test
    @DisplayName("reportDataTaskStatus: V1.0 抛 UnsupportedOperationException")
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
