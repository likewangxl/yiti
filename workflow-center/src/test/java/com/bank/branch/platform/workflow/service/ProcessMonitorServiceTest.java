package com.bank.branch.platform.workflow.service;

import com.bank.branch.platform.auth.api.CurrentUserApi;
import com.bank.branch.platform.auth.api.OrgApi;
import com.bank.branch.platform.common.security.context.DataScopeContext;
import com.bank.branch.platform.common.security.enums.BizType;
import com.bank.branch.platform.common.security.enums.DataScopeType;
import com.bank.branch.platform.workflow.api.dto.ProcessMonitorItemDTO;
import com.bank.branch.platform.workflow.entity.BizProcessMap;
import com.bank.branch.platform.workflow.mapper.BizProcessMapMapper;
import org.flowable.engine.TaskService;
import org.flowable.task.api.Task;
import org.flowable.task.api.TaskQuery;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * ProcessMonitorService 单元测试：验证数据范围（DataScopeContext）到 orgScope 的转换
 * 是否正确传递给 Mapper（null=全行不过滤；ORG=单机构过滤）。
 */
@ExtendWith(MockitoExtension.class)
class ProcessMonitorServiceTest {

    @Mock
    private BizProcessMapMapper bizProcessMapMapper;
    @Mock
    private CurrentUserApi currentUserApi;
    @Mock
    private OrgApi orgApi;
    @Mock
    private TaskService taskService;
    @InjectMocks
    private ProcessMonitorService service;

    @Test
    void admin_noOrgFilter() {
        when(currentUserApi.isSystemAdmin()).thenReturn(true);
        when(bizProcessMapMapper.countMonitor(any(), any(), any(), any(), isNull())).thenReturn(1L);
        when(bizProcessMapMapper.selectMonitorPage(any(), any(), any(), any(), isNull(), eq(0), eq(20)))
                .thenReturn(java.util.List.of());

        service.query("RUNNING", null, null, null, 1, 20);

        verify(bizProcessMapMapper).selectMonitorPage(any(), any(), any(), any(), isNull(), eq(0), eq(20));
    }

    @Test
    void zeroCount_skipsPageQuery() {
        when(currentUserApi.isSystemAdmin()).thenReturn(true);
        when(bizProcessMapMapper.countMonitor(any(), any(), any(), any(), isNull())).thenReturn(0L);

        service.query("RUNNING", null, null, null, 1, 20);

        // total==0 时不应再查分页，省一次 DB 往返
        verify(bizProcessMapMapper, never())
                .selectMonitorPage(any(), any(), any(), any(), any(), anyInt(), anyInt());
    }

    @Test
    void secretary_orgScopeApplied() {
        // 构造 ORG 范围的 DataScopeContext，断言 selectMonitorPage/countMonitor 收到含 orgCode 的集合
        DataScopeContext ctx = new DataScopeContext();
        ctx.setBizType(BizType.WORKFLOW_MONITOR);
        ctx.setScope(DataScopeType.ORG);
        ctx.setOrgCode("ORG_A");
        DataScopeContext.set(ctx);
        try {
            when(currentUserApi.isSystemAdmin()).thenReturn(false);
            when(bizProcessMapMapper.countMonitor(any(), any(), any(), any(),
                    argThat(c -> c != null && c.contains("ORG_A")))).thenReturn(1L);
            when(bizProcessMapMapper.selectMonitorPage(any(), any(), any(), any(),
                    argThat(c -> c != null && c.contains("ORG_A")), eq(0), eq(20)))
                    .thenReturn(java.util.List.of());

            service.query("RUNNING", null, null, null, 1, 20);

            verify(bizProcessMapMapper).selectMonitorPage(any(), any(), any(), any(),
                    argThat(c -> c != null && c.contains("ORG_A")), eq(0), eq(20));
        } finally {
            DataScopeContext.clear();
        }
    }

    @Test
    void nonEmptyRows_populatesCurrentTaskId() {
        // 覆盖 toDtos()/resolveActiveTaskIds() 批量补全路径：一行 RUNNING 流程有活跃任务，
        // 断言 taskService 按 processInstanceIdIn(...).active() 查出的 taskId 正确写回对应行。
        BizProcessMap row = new BizProcessMap();
        row.setProcessInstanceId("PI-1");
        row.setBusinessKey("ALLOC_ADJUST:1");
        row.setBizType("ALLOC_ADJUST");
        row.setProcessStatus("RUNNING");
        row.setCurrentAssignee("E001");

        when(currentUserApi.isSystemAdmin()).thenReturn(true);
        when(bizProcessMapMapper.countMonitor(any(), any(), any(), any(), isNull())).thenReturn(1L);
        when(bizProcessMapMapper.selectMonitorPage(any(), any(), any(), any(), isNull(), eq(0), eq(20)))
                .thenReturn(List.of(row));

        TaskQuery taskQuery = mock(TaskQuery.class);
        when(taskService.createTaskQuery()).thenReturn(taskQuery);
        when(taskQuery.processInstanceIdIn(any())).thenReturn(taskQuery);
        when(taskQuery.active()).thenReturn(taskQuery);
        Task task = mock(Task.class);
        when(task.getProcessInstanceId()).thenReturn("PI-1");
        when(task.getId()).thenReturn("TASK-1");
        when(taskQuery.list()).thenReturn(List.of(task));

        var page = service.query("RUNNING", null, null, null, 1, 20);

        assertThat(page.getRecords()).hasSize(1);
        ProcessMonitorItemDTO dto = page.getRecords().get(0);
        assertThat(dto.getProcessInstanceId()).isEqualTo("PI-1");
        assertThat(dto.getCurrentTaskId()).isEqualTo("TASK-1");
    }
}
