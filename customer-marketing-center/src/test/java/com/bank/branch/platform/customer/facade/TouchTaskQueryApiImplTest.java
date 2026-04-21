package com.bank.branch.platform.customer.facade;

import com.bank.branch.platform.customer.api.dto.TouchTaskDTO;
import com.bank.branch.platform.customer.api.dto.TouchTaskSummaryDTO;
import com.bank.branch.platform.customer.entity.TouchTask;
import com.bank.branch.platform.customer.enums.TouchTaskStatus;
import com.bank.branch.platform.customer.mapper.TouchTaskMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * TouchTaskQueryApiImpl 单元测试（TDD Red-Green-Refactor）
 * 验证契约 §5 全部 8 个方法的委托路径正确。
 */
@ExtendWith(MockitoExtension.class)
class TouchTaskQueryApiImplTest {

    @Mock
    private TouchTaskMapper touchTaskMapper;

    @InjectMocks
    private TouchTaskQueryApiImpl touchTaskQueryApiImpl;

    // ===================== getTouchTask =====================

    @Test
    void getTouchTask_returnsOptional() {
        // given
        TouchTask task = buildTask("task-001", "cust-001", TouchTaskStatus.PENDING.getCode());
        when(touchTaskMapper.selectById("task-001")).thenReturn(task);

        // when
        Optional<TouchTaskDTO> result = touchTaskQueryApiImpl.getTouchTask("task-001");

        // then
        assertThat(result).isPresent();
        assertThat(result.get().getId()).isEqualTo("task-001");
        assertThat(result.get().getTaskStatus()).isEqualTo(TouchTaskStatus.PENDING.getCode());
        verify(touchTaskMapper).selectById("task-001");
    }

    @Test
    void getTouchTask_emptyForNull() {
        // given
        when(touchTaskMapper.selectById("not-exist")).thenReturn(null);

        // when
        Optional<TouchTaskDTO> result = touchTaskQueryApiImpl.getTouchTask("not-exist");

        // then
        assertThat(result).isEmpty();
        verify(touchTaskMapper).selectById("not-exist");
    }

    // ===================== getTouchTaskByBusinessKey =====================

    @Test
    void getTouchTaskByBusinessKey_returnsOptional() {
        // given
        TouchTask task = buildTask("task-001", "cust-001", TouchTaskStatus.PENDING.getCode());
        task.setBusinessKey("TOUCH:task-001");
        when(touchTaskMapper.selectByBusinessKey("TOUCH:task-001")).thenReturn(task);

        // when
        Optional<TouchTaskDTO> result = touchTaskQueryApiImpl.getTouchTaskByBusinessKey("TOUCH:task-001");

        // then
        assertThat(result).isPresent();
        assertThat(result.get().getBusinessKey()).isEqualTo("TOUCH:task-001");
        verify(touchTaskMapper).selectByBusinessKey("TOUCH:task-001");
    }

    @Test
    void getTouchTaskByBusinessKey_emptyForNull() {
        // given
        when(touchTaskMapper.selectByBusinessKey("TOUCH:not-exist")).thenReturn(null);

        // when
        Optional<TouchTaskDTO> result = touchTaskQueryApiImpl.getTouchTaskByBusinessKey("TOUCH:not-exist");

        // then
        assertThat(result).isEmpty();
    }

    // ===================== getEmpTouchTasks =====================

    @Test
    void getEmpTouchTasks_withStatus() {
        // given
        TouchTask task = buildTask("task-001", "cust-001", "PENDING");
        when(touchTaskMapper.selectByEmpAndStatus("E1", "PENDING")).thenReturn(List.of(task));

        // when
        List<TouchTaskDTO> result = touchTaskQueryApiImpl.getEmpTouchTasks("E1", "PENDING");

        // then
        assertThat(result).hasSize(1);
        verify(touchTaskMapper).selectByEmpAndStatus("E1", "PENDING");
    }

    @Test
    void getEmpTouchTasks_allWhenStatusNull() {
        // given
        TouchTask task1 = buildTask("task-001", "cust-001", "PENDING");
        TouchTask task2 = buildTask("task-002", "cust-001", "SUCCESS");
        when(touchTaskMapper.selectByEmp("E1")).thenReturn(List.of(task1, task2));

        // when
        List<TouchTaskDTO> result = touchTaskQueryApiImpl.getEmpTouchTasks("E1", null);

        // then
        assertThat(result).hasSize(2);
        verify(touchTaskMapper).selectByEmp("E1");
    }

    // ===================== countRunningTouchTasks =====================

    @Test
    void countRunningTouchTasks_sumsPendingAndInProgress() {
        // given
        when(touchTaskMapper.countByEmpAndStatuses("E1", List.of("PENDING", "IN_PROGRESS"))).thenReturn(3L);

        // when
        int count = touchTaskQueryApiImpl.countRunningTouchTasks("E1");

        // then
        assertThat(count).isEqualTo(3);
        verify(touchTaskMapper).countByEmpAndStatuses("E1", List.of("PENDING", "IN_PROGRESS"));
    }

    @Test
    void countRunningTouchTasks_returnsZeroWhenNull() {
        // given
        when(touchTaskMapper.countByEmpAndStatuses("E1", List.of("PENDING", "IN_PROGRESS"))).thenReturn(null);

        // when
        int count = touchTaskQueryApiImpl.countRunningTouchTasks("E1");

        // then
        assertThat(count).isZero();
    }

    // ===================== getCustomerTouchHistory =====================

    @Test
    void getCustomerTouchHistory_orderByCreatedDesc() {
        // given
        TouchTask task1 = buildTask("task-001", "cust-001", "PENDING");
        TouchTask task2 = buildTask("task-002", "cust-001", "SUCCESS");
        when(touchTaskMapper.selectByCustOrderByCreatedDesc("cust-001")).thenReturn(List.of(task1, task2));

        // when
        List<TouchTaskDTO> result = touchTaskQueryApiImpl.getCustomerTouchHistory("cust-001");

        // then
        assertThat(result).hasSize(2);
        verify(touchTaskMapper).selectByCustOrderByCreatedDesc("cust-001");
    }

    // ===================== getCustomerTouchHistoryByOrg =====================

    @Test
    void getCustomerTouchHistoryByOrg_filtersOrg() {
        // given
        TouchTask task = buildTask("task-001", "cust-001", "SUCCESS");
        task.setOrgId("ORG01");
        when(touchTaskMapper.selectByCustAndOrg("cust-001", "ORG01")).thenReturn(List.of(task));

        // when
        List<TouchTaskDTO> result = touchTaskQueryApiImpl.getCustomerTouchHistoryByOrg("cust-001", "ORG01");

        // then
        assertThat(result).hasSize(1);
        verify(touchTaskMapper).selectByCustAndOrg("cust-001", "ORG01");
    }

    // ===================== hasCompletedFirstTouch =====================

    @Test
    void hasCompletedFirstTouch_trueWhenExists() {
        // given
        when(touchTaskMapper.countFirstTouchSuccess("cust-001", "ORG01")).thenReturn(1L);

        // when
        boolean result = touchTaskQueryApiImpl.hasCompletedFirstTouch("cust-001", "ORG01");

        // then
        assertThat(result).isTrue();
        verify(touchTaskMapper).countFirstTouchSuccess("cust-001", "ORG01");
    }

    @Test
    void hasCompletedFirstTouch_falseWhenNone() {
        // given
        when(touchTaskMapper.countFirstTouchSuccess("cust-001", "ORG01")).thenReturn(0L);

        // when
        boolean result = touchTaskQueryApiImpl.hasCompletedFirstTouch("cust-001", "ORG01");

        // then
        assertThat(result).isFalse();
    }

    @Test
    void hasCompletedFirstTouch_falseWhenNull() {
        // given
        when(touchTaskMapper.countFirstTouchSuccess("cust-001", "ORG01")).thenReturn(null);

        // when
        boolean result = touchTaskQueryApiImpl.hasCompletedFirstTouch("cust-001", "ORG01");

        // then
        assertThat(result).isFalse();
    }

    // ===================== getOrgTouchSummary =====================

    @Test
    void getOrgTouchSummary_aggregates() {
        // given
        when(touchTaskMapper.countByOrgBetween("ORG01", "2026-01-01", "2026-01-31", null)).thenReturn(10L);
        when(touchTaskMapper.countByOrgBetween("ORG01", "2026-01-01", "2026-01-31", "PENDING")).thenReturn(3L);
        when(touchTaskMapper.countByOrgBetween("ORG01", "2026-01-01", "2026-01-31", "IN_PROGRESS")).thenReturn(2L);
        when(touchTaskMapper.countByOrgBetween("ORG01", "2026-01-01", "2026-01-31", "SUCCESS")).thenReturn(4L);
        when(touchTaskMapper.countByOrgBetween("ORG01", "2026-01-01", "2026-01-31", "CANCELLED")).thenReturn(1L);
        when(touchTaskMapper.countSlaWarningByOrgBetween("ORG01", "2026-01-01", "2026-01-31")).thenReturn(2L);
        when(touchTaskMapper.avgDurationHoursByOrgBetween("ORG01", "2026-01-01", "2026-01-31")).thenReturn(3.5);

        // when
        TouchTaskSummaryDTO dto = touchTaskQueryApiImpl.getOrgTouchSummary("ORG01", "2026-01-01", "2026-01-31");

        // then
        assertThat(dto.getOrgId()).isEqualTo("ORG01");
        assertThat(dto.getTotalCount()).isEqualTo(10L);
        assertThat(dto.getPendingCount()).isEqualTo(3L);
        assertThat(dto.getInProgressCount()).isEqualTo(2L);
        assertThat(dto.getSuccessCount()).isEqualTo(4L);
        assertThat(dto.getCancelledCount()).isEqualTo(1L);
        assertThat(dto.getSlaWarningCount()).isEqualTo(2L);
        assertThat(dto.getAvgDurationHours()).isEqualTo(3.5);
    }

    // ===================== 辅助方法 =====================

    private TouchTask buildTask(String id, String custId, String status) {
        TouchTask task = new TouchTask();
        task.setId(id);
        task.setTaskNo("TOUCH_" + id);
        task.setCustId(custId);
        task.setTaskStatus(status);
        task.setSlaStatus("GREEN");
        return task;
    }
}
