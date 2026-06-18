package com.bank.branch.platform.performance.service.adjust;

import com.bank.branch.platform.auth.api.OrgApi;
import com.bank.branch.platform.auth.api.dto.OrgDTO;
import com.bank.branch.platform.workflow.api.WorkflowApi;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;
import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * AllocAdjustService 路由改造 + 启动变量种入：
 * - custType=CORP→对公设计器流程，RETAIL→零售设计器流程（经 resolveDesignerProcDefKey 解析 procDefKey）；
 * - 起流程把发起机构级别种为 Integer startOrgLevel（设计器网关分流依据）。
 */
class AllocAdjustServiceDesignerRouteTest {

    private String invokeResolve(AllocAdjustService svc, String custType, String bizKind) throws Exception {
        Method m = AllocAdjustService.class.getDeclaredMethod("resolveProcessKey", String.class, String.class);
        m.setAccessible(true);
        return (String) m.invoke(svc, custType, bizKind);
    }

    @Test
    void resolveProcessKey_corp_usesCorpDesignerFlow() throws Exception {
        WorkflowApi workflowApi = mock(WorkflowApi.class);
        when(workflowApi.resolveDesignerProcDefKey("alloc_corp_designer")).thenReturn("DSN_alloc_corp_designer");
        AllocAdjustService svc = AllocAdjustService.forRouteTest(workflowApi);
        assertEquals("DSN_alloc_corp_designer", invokeResolve(svc, "CORP", "CORP_LOAN"));
    }

    @Test
    void resolveProcessKey_retail_usesRetailDesignerFlow() throws Exception {
        WorkflowApi workflowApi = mock(WorkflowApi.class);
        when(workflowApi.resolveDesignerProcDefKey("alloc_retail_designer")).thenReturn("DSN_alloc_retail_designer");
        AllocAdjustService svc = AllocAdjustService.forRouteTest(workflowApi);
        assertEquals("DSN_alloc_retail_designer", invokeResolve(svc, "RETAIL", "RETAIL_CARD"));
    }

    @Test
    void resolveProcessKey_nullCustType_fallsBackToBizKindPrefix() throws Exception {
        WorkflowApi workflowApi = mock(WorkflowApi.class);
        when(workflowApi.resolveDesignerProcDefKey("alloc_corp_designer")).thenReturn("DSN_alloc_corp_designer");
        AllocAdjustService svc = AllocAdjustService.forRouteTest(workflowApi);
        assertEquals("DSN_alloc_corp_designer", invokeResolve(svc, null, "CORP_DEPOSIT"));
    }

    @Test
    void buildStartVariables_putsIntegerStartOrgLevelFromOrgApi() throws Exception {
        OrgApi orgApi = mock(OrgApi.class);
        OrgDTO org = new OrgDTO();
        org.setOrgLevel(2);
        when(orgApi.getOrg("0201")).thenReturn(org);

        AllocAdjustService svc = AllocAdjustService.forStartVarsTest(orgApi);
        Method m = AllocAdjustService.class.getDeclaredMethod("buildStartVariables", String.class, Map.class);
        m.setAccessible(true);
        @SuppressWarnings("unchecked")
        Map<String, Object> vars = (Map<String, Object>) m.invoke(svc, "0201", new HashMap<String, Object>());

        assertEquals(2, vars.get("startOrgLevel")); // Integer，匹配 EL 数字比较 startOrgLevel == 2
    }
}
