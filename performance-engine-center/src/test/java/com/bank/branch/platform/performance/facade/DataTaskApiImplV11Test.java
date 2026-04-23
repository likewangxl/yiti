package com.bank.branch.platform.performance.facade;

import com.bank.branch.platform.performance.api.dto.DataTaskReportResultDTO;
import com.bank.branch.platform.performance.api.dto.cmd.DataTaskStatusCmd;
import com.bank.branch.platform.performance.entity.PerfRunTask;
import com.bank.branch.platform.performance.service.DataTaskService;
import com.bank.branch.platform.performance.support.PerformanceServiceTestBase;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;

import java.time.Instant;
import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * DataTaskApiImpl V1.1 Task P6.2 单元测试（Red）.
 *
 * <p>替换 {@link DataTaskApiImpl#reportDataTaskStatus(DataTaskStatusCmd)} 的 UOE，
 * 委托 {@link DataTaskService#report(DataTaskStatusCmd)} 完成真实幂等落库 + 触发管线.
 *
 * <p>覆盖场景：
 * <ul>
 *   <li>SUCCESS 上报：Facade 委托 Service，返回 accepted=true + taskId 回显 + perfRunTaskId 非空</li>
 *   <li>FAILED 上报：Facade 委托 Service（业务语义由 Service 负责 "只记录不触发计算"）</li>
 *   <li>幂等：Service 返回 accepted=false 时，Facade 原样透传（不重复委托）</li>
 *   <li>参数校验兜底：cmd 为 null 抛 IllegalArgumentException（Facade 职责）</li>
 * </ul>
 */
class DataTaskApiImplV11Test extends PerformanceServiceTestBase {

    @Mock
    private DataTaskService dataTaskService;

    @InjectMocks
    private DataTaskApiImpl dataTaskApi;

    @Test
    @DisplayName("reportDataTaskStatus: SUCCESS 场景委托 Service + 返回 accepted=true")
    void reportDataTaskStatus_success_delegatesToService() {
        DataTaskStatusCmd cmd = sampleCmd("EXT_OK_001", "SUCCESS", null);
        DataTaskReportResultDTO expected = DataTaskReportResultDTO.builder()
                .taskId("EXT_OK_001")
                .accepted(true)
                .perfRunTaskId("RT_OK_001")
                .build();
        when(dataTaskService.report(any(DataTaskStatusCmd.class))).thenReturn(expected);

        DataTaskReportResultDTO result = dataTaskApi.reportDataTaskStatus(cmd);

        assertThat(result).isSameAs(expected);
        verify(dataTaskService, times(1)).report(eq(cmd));
    }

    @Test
    @DisplayName("reportDataTaskStatus: FAILED 场景委托 Service（不抛异常）")
    void reportDataTaskStatus_failed_stillDelegates() {
        DataTaskStatusCmd cmd = sampleCmd("EXT_FAIL_001", "FAILED", "ETL 超时");
        DataTaskReportResultDTO expected = DataTaskReportResultDTO.builder()
                .taskId("EXT_FAIL_001")
                .accepted(true)
                .perfRunTaskId("RT_FAIL_001")
                .build();
        when(dataTaskService.report(any(DataTaskStatusCmd.class))).thenReturn(expected);

        DataTaskReportResultDTO result = dataTaskApi.reportDataTaskStatus(cmd);

        assertThat(result.getTaskId()).isEqualTo("EXT_FAIL_001");
        verify(dataTaskService).report(eq(cmd));
    }

    @Test
    @DisplayName("reportDataTaskStatus: 幂等命中时透传 Service 返回的 accepted=false")
    void reportDataTaskStatus_idempotentHit_propagatesAcceptedFalse() {
        DataTaskStatusCmd cmd = sampleCmd("EXT_DUP", "SUCCESS", null);
        DataTaskReportResultDTO existing = DataTaskReportResultDTO.builder()
                .taskId("EXT_DUP")
                .accepted(false)
                .perfRunTaskId("RT_EXISTING")
                .build();
        when(dataTaskService.report(any(DataTaskStatusCmd.class))).thenReturn(existing);

        DataTaskReportResultDTO result = dataTaskApi.reportDataTaskStatus(cmd);

        assertThat(result.getAccepted()).isFalse();
        assertThat(result.getPerfRunTaskId()).isEqualTo("RT_EXISTING");
    }

    @Test
    @DisplayName("reportDataTaskStatus: cmd=null 抛 IllegalArgumentException（Facade 职责）")
    void reportDataTaskStatus_nullCmd_throwsIAE() {
        try {
            dataTaskApi.reportDataTaskStatus(null);
        } catch (IllegalArgumentException ex) {
            assertThat(ex.getMessage()).contains("cmd");
            return;
        }
        throw new AssertionError("应抛 IllegalArgumentException");
    }

    // ------------------- helpers -------------------

    private DataTaskStatusCmd sampleCmd(String taskId, String status, String errorMsg) {
        return DataTaskStatusCmd.builder()
                .taskId(taskId)
                .dataType("EMP_INDEX_RESULT")
                .dataDate(LocalDate.of(2026, 4, 1))
                .version("20260401-01")
                .status(status)
                .rowCount(100)
                .errorMsg(errorMsg)
                .sourceSystem("CORE_BANK")
                .reportedAt(Instant.now())
                .build();
    }

    /** 构造给 Service 层做辅助参考（未被直接使用）. */
    @SuppressWarnings("unused")
    private static PerfRunTask sampleTask() {
        PerfRunTask t = new PerfRunTask();
        t.setId("RT_OK_001");
        t.setTaskType("EXT_DATA");
        t.setTaskKey("EXT_OK_001");
        t.setStatus("SUCCESS");
        return t;
    }
}
