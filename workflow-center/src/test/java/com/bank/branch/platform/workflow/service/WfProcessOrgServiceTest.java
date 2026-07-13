package com.bank.branch.platform.workflow.service;

import com.bank.branch.platform.auth.api.OrgApi;
import com.bank.branch.platform.auth.api.dto.OrgDTO;
import com.bank.branch.platform.workflow.entity.WfProcessOrg;
import com.bank.branch.platform.workflow.mapper.WfProcessOrgMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentMatchers;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class WfProcessOrgServiceTest {

    @Mock private WfProcessOrgMapper mapper;
    @Mock private OrgApi orgApi;
    @InjectMocks private WfProcessOrgService service;

    @Test
    void record_insertsMainOrg() {
        OrgDTO org = new OrgDTO();
        org.setOrgCode("ORG_A");
        when(orgApi.getUserMainOrg("E001")).thenReturn(org);

        service.record("PID_1", "E001", "START");

        verify(mapper).insertIgnore(ArgumentMatchers.<WfProcessOrg>argThat(r ->
                "PID_1".equals(r.getProcessInstanceId())
                        && "ORG_A".equals(r.getOrgCode())
                        && "START".equals(r.getSource())
                        && r.getId() != null));
    }

    @Test
    void record_skipsWhenEmpIdNull() {
        service.record("PID_1", null, "ASSIGN");
        verifyNoInteractions(mapper, orgApi);
    }

    @Test
    void record_skipsWhenOrgUnknown() {
        when(orgApi.getUserMainOrg("E404")).thenReturn(null);
        service.record("PID_1", "E404", "ASSIGN");
        verify(mapper, never()).insertIgnore(any());
    }
}
