package com.bank.branch.platform.workflow.service.flow;

import com.bank.branch.platform.common.web.exception.BizException;
import com.bank.branch.platform.workflow.entity.WfFlowDef;
import com.bank.branch.platform.workflow.mapper.WfFlowDefMapper;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * DesignerFlowLookupService.resolveDeployedProcDefKey：
 * 已发布→返 deployedProcDefKey；未发布/不存在→抛 BizException(WF-40401)。
 */
class DesignerFlowLookupServiceTest {

    private WfFlowDef def(String status, String key) {
        WfFlowDef d = new WfFlowDef();
        d.setFlowKey("alloc_corp_designer");
        d.setStatus(status);
        d.setDeployedProcDefKey(key);
        return d;
    }

    @Test
    void resolve_published_returnsKey() {
        WfFlowDefMapper mapper = mock(WfFlowDefMapper.class);
        when(mapper.selectByFlowKey("alloc_corp_designer"))
                .thenReturn(def("PUBLISHED", "DSN_alloc_corp_designer"));
        DesignerFlowLookupService svc = new DesignerFlowLookupService(mapper);
        assertEquals("DSN_alloc_corp_designer", svc.resolveDeployedProcDefKey("alloc_corp_designer"));
    }

    @Test
    void resolve_draft_throws() {
        WfFlowDefMapper mapper = mock(WfFlowDefMapper.class);
        when(mapper.selectByFlowKey("alloc_corp_designer")).thenReturn(def("DRAFT", null));
        DesignerFlowLookupService svc = new DesignerFlowLookupService(mapper);
        assertThrows(BizException.class, () -> svc.resolveDeployedProcDefKey("alloc_corp_designer"));
    }

    @Test
    void resolve_publishedButBlankKey_throws() {
        WfFlowDefMapper mapper = mock(WfFlowDefMapper.class);
        when(mapper.selectByFlowKey("alloc_corp_designer")).thenReturn(def("PUBLISHED", "  "));
        DesignerFlowLookupService svc = new DesignerFlowLookupService(mapper);
        assertThrows(BizException.class, () -> svc.resolveDeployedProcDefKey("alloc_corp_designer"));
    }

    @Test
    void resolve_missing_throws() {
        WfFlowDefMapper mapper = mock(WfFlowDefMapper.class);
        when(mapper.selectByFlowKey("none")).thenReturn(null);
        DesignerFlowLookupService svc = new DesignerFlowLookupService(mapper);
        assertThrows(BizException.class, () -> svc.resolveDeployedProcDefKey("none"));
    }
}
