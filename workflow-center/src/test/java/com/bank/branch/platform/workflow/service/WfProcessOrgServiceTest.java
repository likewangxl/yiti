package com.bank.branch.platform.workflow.service;

import com.bank.branch.platform.auth.api.OrgApi;
import com.bank.branch.platform.auth.api.dto.OrgDTO;
import com.bank.branch.platform.common.web.exception.BizException;
import com.bank.branch.platform.workflow.entity.WfProcessOrg;
import com.bank.branch.platform.workflow.mapper.WfProcessOrgMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentMatchers;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
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

    @Test
    void record_swallowsExceptionWhenOrgApiThrows() {
        // 真实 OrgApi.getUserMainOrg 在 empId 无 EXT_USER_ORG 映射时抛 BizException(AUTH-40403)，
        // 而非返回 null；record() 是全挂点唯一写入入口，绝不能把异常抛给调用方（Flowable 监听器/流程启动）。
        when(orgApi.getUserMainOrg("E500")).thenThrow(new BizException("AUTH-40403", "no org"));

        service.record("PID_1", "E500", "ASSIGN");

        verify(mapper, never()).insertIgnore(any());
    }

    @Test
    void recordOrg_insertsGivenOrgWithoutUserLookup() {
        // 候选人模式（任务无 assignee）下没有"某个人"可查机构，只有解析出的审批机构编码，
        // 故需要一个直接按机构编码落快照的入口；不得回查 OrgApi。
        service.recordOrg("PID_2", "ORG_BR", "CANDIDATE");

        verify(mapper).insertIgnore(ArgumentMatchers.<WfProcessOrg>argThat(r ->
                "PID_2".equals(r.getProcessInstanceId())
                        && "ORG_BR".equals(r.getOrgCode())
                        && "CANDIDATE".equals(r.getSource())
                        && r.getId() != null));
        verifyNoInteractions(orgApi);
    }

    @Test
    void recordOrg_skipsWhenOrgCodeBlank() {
        service.recordOrg("PID_2", "  ", "CANDIDATE");
        service.recordOrg("PID_2", null, "CANDIDATE");
        service.recordOrg(null, "ORG_BR", "CANDIDATE");
        verifyNoInteractions(mapper, orgApi);
    }

    @Test
    void recordOrg_swallowsExceptionWhenMapperInsertThrows() {
        when(mapper.insertIgnore(any())).thenThrow(new DataIntegrityViolationException("boom"));

        assertDoesNotThrow(() -> service.recordOrg("PID_2", "ORG_BR", "CANDIDATE"));
    }

    @Test
    void record_swallowsExceptionWhenMapperInsertThrows() {
        // insertIgnore 只吞唯一键冲突，不吞死锁/锁等待超时/连接断开这类 DataAccessException；
        // record() 必须整体兜住，绝不能让 DB 异常穿透到调用方（4 个挂点均非 @Transactional，
        // TaskAssignmentListener 还跑在 Flowable 自己的命令执行里）。
        OrgDTO org = new OrgDTO();
        org.setOrgCode("ORG_A");
        when(orgApi.getUserMainOrg("E001")).thenReturn(org);
        when(mapper.insertIgnore(any())).thenThrow(new DataIntegrityViolationException("boom"));

        assertDoesNotThrow(() -> service.record("PID_1", "E001", "START"));
    }
}
