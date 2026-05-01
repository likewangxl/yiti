package com.bank.branch.platform.report.service.export;

import com.bank.branch.platform.common.web.exception.BizException;
import com.bank.branch.platform.report.entity.RptExportTask;
import com.bank.branch.platform.report.mapper.RptExportTaskMapper;
import com.bank.branch.platform.report.service.export.impl.RptExportServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * RptExportService 单元测试（Task M5.2.2）.
 *
 * <p>覆盖：
 * <ol>
 *   <li>createTask 成功路径 → 落 PENDING → RUNNING → 调 strategy.execute → updateSuccess</li>
 *   <li>未知 exportType → 抛 BIZ EXPORT_START_FAILED（含"未知 exportType"）</li>
 *   <li>exportType 空 / operatorId 空 → 抛 BIZ EXPORT_START_FAILED（含"必填"）</li>
 *   <li>strategy.execute 抛运行时异常 → updateFailed + 异常向上传</li>
 *   <li>getTask null → BIZ EXPORT_TASK_NOT_FOUND（RPT-40009）</li>
 *   <li>getTaskForOwner empId 不匹配 → BIZ EXPORT_DOWNLOAD_FORBIDDEN（RPT-42209）</li>
 *   <li>cancelTask SUCCESS/FAILED → 抛 EXPORT_TASK_NOT_READY（RPT-40010）</li>
 *   <li>cancelTask PENDING → updateStatus 'CANCELLED'</li>
 * </ol>
 */
@ExtendWith(MockitoExtension.class)
class RptExportServiceTest {

    @Mock
    private RptExportTaskMapper taskMapper;

    private FakeStrategy dynamicStrategy;
    private RptExportServiceImpl service;

    @BeforeEach
    void setUp() {
        dynamicStrategy = new FakeStrategy("DYNAMIC_QUERY");
        service = new RptExportServiceImpl(taskMapper, List.of(dynamicStrategy));
    }

    @Test
    void createTask_success_shouldGoPendingRunningSuccess() {
        Map<String, Object> params = new HashMap<>();
        params.put("dim", "EMP");

        // Mockito 陷阱：捕获到的对象是引用，service 后续 setStatus("RUNNING") 会污染原对象。
        // 用 doAnswer 在 insert 时取 status 快照
        final String[] statusOnInsert = new String[1];
        org.mockito.Mockito.doAnswer(inv -> {
            RptExportTask t = inv.getArgument(0);
            statusOnInsert[0] = t.getStatus();
            return 1;
        }).when(taskMapper).insert(any(RptExportTask.class));

        String taskId = service.createTask("DYNAMIC_QUERY", params, "E001");
        assertThat(taskId).isNotBlank();

        // insert 时刻 status 必须是 PENDING
        assertThat(statusOnInsert[0]).isEqualTo("PENDING");

        // 校验 insert 入参的不变字段
        ArgumentCaptor<RptExportTask> insertCap = ArgumentCaptor.forClass(RptExportTask.class);
        verify(taskMapper, times(1)).insert(insertCap.capture());
        assertThat(insertCap.getValue().getOperatorId()).isEqualTo("E001");
        assertThat(insertCap.getValue().getExportType()).isEqualTo("DYNAMIC_QUERY");
        assertThat(insertCap.getValue().getParamsJson()).contains("EMP");

        // verify 三段式状态机
        verify(taskMapper, times(1)).updateStatus(eq(taskId), eq("RUNNING"));
        verify(taskMapper, times(1)).updateSuccess(eq(taskId), anyString(),
            anyInt(), anyLong(), any());
        verify(taskMapper, never()).updateFailed(anyString(), anyString());
    }

    @Test
    void createTask_unknownType_shouldThrowBizException() {
        assertThatThrownBy(() -> service.createTask("UNKNOWN_TYPE", Map.of(), "E001"))
            .isInstanceOf(BizException.class)
            .hasFieldOrPropertyWithValue("code", "RPT-50003");
        verify(taskMapper, never()).insert(any(RptExportTask.class));
    }

    @Test
    void createTask_blankExportType_shouldThrowBizException() {
        assertThatThrownBy(() -> service.createTask("", Map.of(), "E001"))
            .isInstanceOf(BizException.class)
            .hasFieldOrPropertyWithValue("code", "RPT-50003");
        assertThatThrownBy(() -> service.createTask(null, Map.of(), "E001"))
            .isInstanceOf(BizException.class)
            .hasFieldOrPropertyWithValue("code", "RPT-50003");
    }

    @Test
    void createTask_blankOperatorId_shouldThrowBizException() {
        assertThatThrownBy(() -> service.createTask("DYNAMIC_QUERY", Map.of(), ""))
            .isInstanceOf(BizException.class)
            .hasFieldOrPropertyWithValue("code", "RPT-50003");
        assertThatThrownBy(() -> service.createTask("DYNAMIC_QUERY", Map.of(), null))
            .isInstanceOf(BizException.class)
            .hasFieldOrPropertyWithValue("code", "RPT-50003");
    }

    @Test
    void createTask_strategyThrows_shouldUpdateFailedAndRethrow() {
        FakeStrategy bad = new FakeStrategy("FAIL_TYPE") {
            @Override
            public int execute(RptExportTask task) {
                throw new RuntimeException("boom");
            }
        };
        service = new RptExportServiceImpl(taskMapper, List.of(bad));

        assertThatThrownBy(() -> service.createTask("FAIL_TYPE", Map.of(), "E001"))
            .isInstanceOf(RuntimeException.class)
            .hasMessageContaining("boom");

        verify(taskMapper, times(1)).updateFailed(anyString(), anyString());
        verify(taskMapper, never()).updateSuccess(anyString(), anyString(),
            anyInt(), anyLong(), any());
    }

    @Test
    void getTask_notFound_shouldThrowBiz40009() {
        when(taskMapper.selectById("MISSING")).thenReturn(null);
        assertThatThrownBy(() -> service.getTask("MISSING"))
            .isInstanceOf(BizException.class)
            .hasFieldOrPropertyWithValue("code", "RPT-40009");
    }

    @Test
    void getTaskForOwner_ownerMismatch_shouldThrow42209() {
        RptExportTask t = new RptExportTask();
        t.setId("T1");
        t.setOperatorId("E001");
        t.setStatus("PENDING");
        when(taskMapper.selectById("T1")).thenReturn(t);

        assertThatThrownBy(() -> service.getTaskForOwner("T1", "E002"))
            .isInstanceOf(BizException.class)
            .hasFieldOrPropertyWithValue("code", "RPT-42209");
    }

    @Test
    void getTaskForOwner_ownerMatch_shouldReturn() {
        RptExportTask t = new RptExportTask();
        t.setId("T2");
        t.setOperatorId("E001");
        t.setStatus("RUNNING");
        when(taskMapper.selectById("T2")).thenReturn(t);

        RptExportTask got = service.getTaskForOwner("T2", "E001");
        assertThat(got).isSameAs(t);
    }

    @Test
    void cancelTask_pending_shouldUpdateToCancelled() {
        RptExportTask t = new RptExportTask();
        t.setId("T3");
        t.setOperatorId("E001");
        t.setStatus("PENDING");
        when(taskMapper.selectById("T3")).thenReturn(t);

        service.cancelTask("T3", "E001");
        verify(taskMapper, times(1)).updateStatus(eq("T3"), eq("CANCELLED"));
    }

    @Test
    void cancelTask_alreadySuccess_shouldThrow40010() {
        RptExportTask t = new RptExportTask();
        t.setId("T4");
        t.setOperatorId("E001");
        t.setStatus("SUCCESS");
        when(taskMapper.selectById("T4")).thenReturn(t);

        assertThatThrownBy(() -> service.cancelTask("T4", "E001"))
            .isInstanceOf(BizException.class)
            .hasFieldOrPropertyWithValue("code", "RPT-40010");
        verify(taskMapper, never()).updateStatus(anyString(), eq("CANCELLED"));
    }

    @Test
    void cancelTask_alreadyFailed_shouldThrow40010() {
        RptExportTask t = new RptExportTask();
        t.setId("T5");
        t.setOperatorId("E001");
        t.setStatus("FAILED");
        when(taskMapper.selectById("T5")).thenReturn(t);

        assertThatThrownBy(() -> service.cancelTask("T5", "E001"))
            .isInstanceOf(BizException.class)
            .hasFieldOrPropertyWithValue("code", "RPT-40010");
    }

    /** 测试用最小 ExportStrategy 实现，回写 fileKey + fileSize 模拟真实行为. */
    private static class FakeStrategy implements ExportStrategy {
        private final String type;

        FakeStrategy(String type) {
            this.type = type;
        }

        @Override
        public String exportType() {
            return type;
        }

        @Override
        public int execute(RptExportTask task) {
            task.setFileKey("rpt/export/" + task.getId() + "/fake.xlsx");
            task.setFileSize(1024L);
            return 42;
        }
    }
}
