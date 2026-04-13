package com.bank.branch.platform.customer.facade;

import com.bank.branch.platform.customer.entity.TouchTask;
import com.bank.branch.platform.customer.enums.SlaStatus;
import com.bank.branch.platform.customer.enums.TouchTaskStatus;
import com.bank.branch.platform.customer.mapper.TouchTaskMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * TouchTaskQueryApiImpl 单元测试（TDD）
 * 验证委托调用路径正确。
 */
@ExtendWith(MockitoExtension.class)
class TouchTaskQueryApiImplTest {

    @Mock
    private TouchTaskMapper touchTaskMapper;

    @InjectMocks
    private TouchTaskQueryApiImpl touchTaskQueryApiImpl;

    @Test
    void getTaskById_shouldDelegateToTouchTaskMapper() {
        // given
        TouchTask task = new TouchTask();
        task.setId("task-001");
        task.setTaskNo("TOUCH_20240101_0001");
        task.setCustId("cust-001");
        task.setTaskStatus(TouchTaskStatus.PENDING.getCode());
        when(touchTaskMapper.selectById("task-001")).thenReturn(task);

        // when
        TouchTask result = touchTaskQueryApiImpl.getTaskById("task-001");

        // then
        assertThat(result).isNotNull();
        assertThat(result.getId()).isEqualTo("task-001");
        assertThat(result.getTaskNo()).isEqualTo("TOUCH_20240101_0001");
        verify(touchTaskMapper).selectById("task-001");
    }

    @Test
    void getTaskById_shouldReturnNullWhenNotFound() {
        // given
        when(touchTaskMapper.selectById("not-exist")).thenReturn(null);

        // when
        TouchTask result = touchTaskQueryApiImpl.getTaskById("not-exist");

        // then
        assertThat(result).isNull();
        verify(touchTaskMapper).selectById("not-exist");
    }

    @Test
    void getTaskStatus_shouldReturnTaskStatusString() {
        // given
        TouchTask task = new TouchTask();
        task.setId("task-001");
        task.setTaskStatus(TouchTaskStatus.PENDING.getCode());
        when(touchTaskMapper.selectById("task-001")).thenReturn(task);

        // when
        String status = touchTaskQueryApiImpl.getTaskStatus("task-001");

        // then
        assertThat(status).isEqualTo(TouchTaskStatus.PENDING.getCode());
        verify(touchTaskMapper).selectById("task-001");
    }

    @Test
    void getTaskStatus_shouldReturnNullWhenTaskNotFound() {
        // given
        when(touchTaskMapper.selectById("not-exist")).thenReturn(null);

        // when
        String status = touchTaskQueryApiImpl.getTaskStatus("not-exist");

        // then
        assertThat(status).isNull();
        verify(touchTaskMapper).selectById("not-exist");
    }

    @Test
    void getSlaStatus_shouldReturnSlaStatusString() {
        // given
        TouchTask task = new TouchTask();
        task.setId("task-001");
        task.setSlaStatus(SlaStatus.GREEN.getCode());
        when(touchTaskMapper.selectById("task-001")).thenReturn(task);

        // when
        String slaStatus = touchTaskQueryApiImpl.getSlaStatus("task-001");

        // then
        assertThat(slaStatus).isEqualTo(SlaStatus.GREEN.getCode());
        verify(touchTaskMapper).selectById("task-001");
    }
}
