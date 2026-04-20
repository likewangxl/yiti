package com.bank.branch.platform.performance.facade;

import com.bank.branch.platform.performance.api.dto.PerfRunTaskDTO;
import com.bank.branch.platform.performance.entity.PerfRunTask;
import com.bank.branch.platform.performance.service.PerfRunTaskService;
import com.bank.branch.platform.performance.support.PerformanceServiceTestBase;
import com.bank.branch.platform.performance.support.RunTaskTestDataBuilder;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;

import java.time.LocalDate;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * PerfCalcApiImpl 单元测试.
 *
 * <p>覆盖 Plan Task 4.3 (plan L1471-1480) DoD:
 * <ul>
 *   <li>1 个 V1.0 实现方法 (getRunTask) 存在 / 不存在 / 全字段装配 3 场景</li>
 *   <li>2 个 V1.1 占位方法 (triggerKpiCalc / triggerRecalc) 统一抛
 *       {@link UnsupportedOperationException} ("V1.1 delivered")</li>
 * </ul>
 *
 * <p>纯 Mock 测试, 不启动 Spring 容器; 无 @Cacheable 注解故无需 AOP 覆盖,
 * 本层只断言 Service 交互 + Assembler 映射正确性。
 *
 * <p>测试数据前缀 {@code TEST_RT_} (由 {@link RunTaskTestDataBuilder} 统一约定)。
 */
class PerfCalcApiImplTest extends PerformanceServiceTestBase {

    @Mock
    private PerfRunTaskService perfRunTaskService;

    @InjectMocks
    private PerfCalcApiImpl perfCalcApi;

    // ------------------------- V1.1 契约: UOE 占位 -------------------------

    @Test
    @DisplayName("triggerKpiCalc: V1.0 抛 UnsupportedOperationException")
    void triggerKpiCalc_throwsUOE() {
        assertThatThrownBy(() -> perfCalcApi.triggerKpiCalc(LocalDate.of(2026, 4, 20)))
                .isInstanceOf(UnsupportedOperationException.class)
                .hasMessage("V1.1 delivered");
        verifyNoInteractions(perfRunTaskService);
    }

    @Test
    @DisplayName("triggerRecalc: V1.0 抛 UnsupportedOperationException")
    void triggerRecalc_throwsUOE() {
        assertThatThrownBy(() -> perfCalcApi.triggerRecalc(
                "MONTHLY",
                LocalDate.of(2026, 1, 1),
                LocalDate.of(2026, 3, 31),
                "补录 Q1 数据",
                "E001"))
                .isInstanceOf(UnsupportedOperationException.class)
                .hasMessage("V1.1 delivered");
        verifyNoInteractions(perfRunTaskService);
    }

    // ------------------------- V1.0 实现: getRunTask -------------------------

    @Test
    @DisplayName("getRunTask: 存在时返回 Optional 包装的 DTO")
    void getRunTask_whenExists_returnsOptional() {
        PerfRunTask task = RunTaskTestDataBuilder.task("FACADE_OK", "E001");
        String id = task.getId();
        when(perfRunTaskService.getById(id)).thenReturn(Optional.of(task));

        Optional<PerfRunTaskDTO> dto = perfCalcApi.getRunTask(id);

        assertThat(dto).isPresent();
        assertThat(dto.get().getId()).isEqualTo(id);
        assertThat(dto.get().getTaskType()).isEqualTo("METRIC_RUN");
        assertThat(dto.get().getStatus()).isEqualTo("RUNNING");
        assertThat(dto.get().getStartedBy()).isEqualTo("E001");
    }

    @Test
    @DisplayName("getRunTask: 不存在时返回 Optional.empty")
    void getRunTask_whenNotExists_returnsEmpty() {
        when(perfRunTaskService.getById("NO_SUCH")).thenReturn(Optional.empty());

        assertThat(perfCalcApi.getRunTask("NO_SUCH")).isEmpty();
    }

    @Test
    @DisplayName("getRunTask: 装配器完整映射 Entity 全部对外字段到 DTO")
    void getRunTask_assemblesAllFields() {
        PerfRunTask task = RunTaskTestDataBuilder.task(
                "FACADE_FULL", "KPI_RUN",
                LocalDate.of(2026, 4, 20), "SUCCESS", "E002");
        task.setEndTime(task.getStartTime().plusMinutes(5));
        task.setErrorMsg("no-error");
        task.setResultPreviewJson("{\"ok\":true}");
        String id = task.getId();
        when(perfRunTaskService.getById(id)).thenReturn(Optional.of(task));

        Optional<PerfRunTaskDTO> dto = perfCalcApi.getRunTask(id);

        assertThat(dto).isPresent();
        PerfRunTaskDTO value = dto.get();
        // 主键 + 业务字段
        assertThat(value.getId()).isEqualTo(id);
        assertThat(value.getTaskType()).isEqualTo("KPI_RUN");
        // DDL 字段 task_key 直接映射为 DTO 同名字段 taskKey
        assertThat(value.getTaskKey()).isEqualTo("TEST_RT_FACADE_FULL");
        assertThat(value.getDataDate()).isEqualTo(LocalDate.of(2026, 4, 20));
        assertThat(value.getDataVersion()).isEqualTo("V_TEST_FACADE_FULL");
        assertThat(value.getStatus()).isEqualTo("SUCCESS");
        assertThat(value.getStartedBy()).isEqualTo("E002");
        assertThat(value.getStartTime()).isEqualTo(task.getStartTime());
        assertThat(value.getEndTime()).isEqualTo(task.getEndTime());
        assertThat(value.getErrorMsg()).isEqualTo("no-error");
        assertThat(value.getResultPreviewJson()).isEqualTo("{\"ok\":true}");
    }
}
