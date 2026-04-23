package com.bank.branch.platform.performance.service.export;

import com.bank.branch.platform.performance.entity.PerfExportTask;
import com.bank.branch.platform.performance.enums.PerfErrorCode;
import com.bank.branch.platform.performance.exception.PerfException;
import com.bank.branch.platform.performance.mapper.PerfExportTaskMapper;
import com.bank.branch.platform.performance.service.export.impl.PerfExportServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * PerfExportService 单元测试（V1.2 Task Q6.1 Red）.
 *
 * <p>目标：验证"exportType → 策略"路由、任务生命周期 PENDING → RUNNING → SUCCESS/FAILED、
 * 未知类型 → BIZ_KIND_INVALID、owner 校验 → EXPORT_TASK_OWNER_MISMATCH。
 *
 * <p>测试风格：策略 mock 化，验证 Service 的分发 + 落库状态机 + 归属校验。
 */
class PerfExportServiceTest {

    private PerfExportTaskMapper mapper;
    private ExportStrategy kpiStrategy;
    private ExportStrategy metricStrategy;
    private ExportStrategy allocStrategy;
    private ExportStrategy detailStrategy;
    private PerfExportServiceImpl service;

    @BeforeEach
    void setUp() {
        mapper = mock(PerfExportTaskMapper.class);
        kpiStrategy = mock(ExportStrategy.class);
        metricStrategy = mock(ExportStrategy.class);
        allocStrategy = mock(ExportStrategy.class);
        detailStrategy = mock(ExportStrategy.class);

        when(kpiStrategy.exportType()).thenReturn("KPI");
        when(metricStrategy.exportType()).thenReturn("METRIC");
        when(allocStrategy.exportType()).thenReturn("ALLOC");
        when(detailStrategy.exportType()).thenReturn("DETAIL");

        when(kpiStrategy.execute(any())).thenReturn(0);
        when(metricStrategy.execute(any())).thenReturn(0);
        when(allocStrategy.execute(any())).thenReturn(0);
        when(detailStrategy.execute(any())).thenReturn(0);

        List<ExportStrategy> strategies = List.of(kpiStrategy, metricStrategy, allocStrategy, detailStrategy);
        service = new PerfExportServiceImpl(mapper, strategies);
    }

    private Map<String, Object> params() {
        Map<String, Object> m = new HashMap<>();
        m.put("schemeCode", "S_TEST");
        m.put("cycleDate", "2026-04-01");
        return m;
    }

    @Test
    @DisplayName("createTask：exportType=KPI → 路由到 kpiStrategy，状态 PENDING 落库")
    void createTask_routesByExportType_kpi() {
        String taskId = service.createTask("KPI", params(), "admin");

        assertThat(taskId).isNotBlank();
        verify(kpiStrategy).execute(any(PerfExportTask.class));
        verify(metricStrategy, never()).execute(any());
        verify(allocStrategy, never()).execute(any());
        verify(detailStrategy, never()).execute(any());

        ArgumentCaptor<PerfExportTask> captor = ArgumentCaptor.forClass(PerfExportTask.class);
        verify(mapper).insert(captor.capture());
        PerfExportTask t = captor.getValue();
        assertThat(t.getExportType()).isEqualTo("KPI");
        assertThat(t.getOperatorId()).isEqualTo("admin");
        assertThat(t.getStatus()).isEqualTo("PENDING");
        assertThat(t.getParamsJson()).contains("schemeCode");
    }

    @Test
    @DisplayName("createTask：exportType=METRIC → 路由到 metricStrategy")
    void createTask_routesByExportType_metric() {
        service.createTask("METRIC", params(), "admin");
        verify(metricStrategy).execute(any(PerfExportTask.class));
        verify(kpiStrategy, never()).execute(any());
    }

    @Test
    @DisplayName("createTask：exportType=ALLOC → 路由到 allocStrategy")
    void createTask_routesByExportType_alloc() {
        service.createTask("ALLOC", params(), "admin");
        verify(allocStrategy).execute(any(PerfExportTask.class));
    }

    @Test
    @DisplayName("createTask：exportType=DETAIL → 路由到 detailStrategy")
    void createTask_routesByExportType_detail() {
        service.createTask("DETAIL", params(), "admin");
        verify(detailStrategy).execute(any(PerfExportTask.class));
    }

    @Test
    @DisplayName("createTask：未知 exportType → 抛 BIZ_KIND_INVALID (PERF-40002)")
    void createTask_unknownType_throwsBizKindInvalid() {
        assertThatThrownBy(() -> service.createTask("UNKNOWN", params(), "admin"))
                .isInstanceOf(PerfException.class)
                .satisfies(ex -> assertThat(((PerfException) ex).getErrorCode())
                        .isEqualTo(PerfErrorCode.BIZ_KIND_INVALID));
    }

    @Test
    @DisplayName("createTask：exportType 空白 → 抛 VALIDATION_FAILED")
    void createTask_blankType_throwsValidationFailed() {
        assertThatThrownBy(() -> service.createTask("", params(), "admin"))
                .isInstanceOf(PerfException.class)
                .satisfies(ex -> assertThat(((PerfException) ex).getErrorCode())
                        .isEqualTo(PerfErrorCode.VALIDATION_FAILED));
    }

    @Test
    @DisplayName("createTask：operatorId 空白 → 抛 VALIDATION_FAILED")
    void createTask_blankOperator_throwsValidationFailed() {
        assertThatThrownBy(() -> service.createTask("KPI", params(), ""))
                .isInstanceOf(PerfException.class)
                .satisfies(ex -> assertThat(((PerfException) ex).getErrorCode())
                        .isEqualTo(PerfErrorCode.VALIDATION_FAILED));
    }

    @Test
    @DisplayName("createTask：成功路径 → RUNNING 过渡 + SUCCESS 终态 + rowCount 写入")
    void createTask_happyPath_transitionsToSuccess() {
        when(kpiStrategy.execute(any())).thenReturn(42);

        String taskId = service.createTask("KPI", params(), "admin");

        assertThat(taskId).isNotBlank();
        verify(mapper).insert(any(PerfExportTask.class));
        verify(mapper).updateStatus(eq(taskId), eq("RUNNING"));
        verify(mapper).updateSuccess(eq(taskId), any(), eq(42), any(), any());
    }

    @Test
    @DisplayName("createTask：策略抛异常 → FAILED + 回填 error_msg + 向上抛")
    void createTask_strategyThrows_marksFailed() {
        doAnswer(inv -> {
            throw new RuntimeException("MinIO down");
        }).when(kpiStrategy).execute(any());

        assertThatThrownBy(() -> service.createTask("KPI", params(), "admin"))
                .isInstanceOf(RuntimeException.class);

        verify(mapper).updateFailed(anyString(), any());
    }

    @Test
    @DisplayName("getTask：存在 → 返回；不存在 → 抛 EXPORT_TASK_NOT_FOUND (PERF-42208)")
    void getTask_notFound_throwsExportTaskNotFound() {
        when(mapper.selectById("MISSING")).thenReturn(null);

        assertThatThrownBy(() -> service.getTask("MISSING"))
                .isInstanceOf(PerfException.class)
                .satisfies(ex -> assertThat(((PerfException) ex).getErrorCode())
                        .isEqualTo(PerfErrorCode.EXPORT_TASK_NOT_FOUND));
    }

    @Test
    @DisplayName("getTaskForOwner：归属一致 → 返回；归属不一致 → 抛 EXPORT_TASK_OWNER_MISMATCH (PERF-42209)")
    void getTaskForOwner_ownerMismatch_throws() {
        PerfExportTask t = new PerfExportTask();
        t.setId("T1");
        t.setOperatorId("ALICE");
        when(mapper.selectById("T1")).thenReturn(t);

        PerfExportTask ok = service.getTaskForOwner("T1", "ALICE");
        assertThat(ok.getId()).isEqualTo("T1");

        assertThatThrownBy(() -> service.getTaskForOwner("T1", "BOB"))
                .isInstanceOf(PerfException.class)
                .satisfies(ex -> assertThat(((PerfException) ex).getErrorCode())
                        .isEqualTo(PerfErrorCode.EXPORT_TASK_OWNER_MISMATCH));
    }
}
