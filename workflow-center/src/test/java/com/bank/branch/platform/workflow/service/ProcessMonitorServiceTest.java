package com.bank.branch.platform.workflow.service;

import com.bank.branch.platform.auth.api.CurrentUserApi;
import com.bank.branch.platform.auth.api.OrgApi;
import com.bank.branch.platform.common.security.context.DataScopeContext;
import com.bank.branch.platform.common.security.enums.BizType;
import com.bank.branch.platform.common.security.enums.DataScopeType;
import com.bank.branch.platform.workflow.mapper.BizProcessMapMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
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
    @InjectMocks
    private ProcessMonitorService service;

    @Test
    void admin_noOrgFilter() {
        when(currentUserApi.isSystemAdmin()).thenReturn(true);
        when(bizProcessMapMapper.countMonitor(any(), any(), any(), any(), isNull())).thenReturn(0L);

        service.query("RUNNING", null, null, null, 1, 20);

        verify(bizProcessMapMapper).selectMonitorPage(any(), any(), any(), any(), isNull(), eq(0), eq(20));
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
}
