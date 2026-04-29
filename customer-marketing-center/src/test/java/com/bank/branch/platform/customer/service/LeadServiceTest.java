package com.bank.branch.platform.customer.service;

import com.bank.branch.platform.common.web.PageResult;
import com.bank.branch.platform.common.web.exception.BizException;
import com.bank.branch.platform.customer.entity.CustLead;
import com.bank.branch.platform.customer.enums.CustomerErrorCode;
import com.bank.branch.platform.customer.enums.LeadOp;
import com.bank.branch.platform.customer.enums.LeadStatus;
import com.bank.branch.platform.customer.mapper.CustLeadMapper;
import com.bank.branch.platform.customer.mapper.LeadImportBatchMapper;
import com.bank.branch.platform.workflow.api.WorkflowApi;
import com.bank.branch.platform.workflow.api.dto.StartProcessCmd;
import com.bank.branch.platform.workflow.api.dto.WorkflowLaunchResp;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;

import java.util.Collections;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * LeadService 单元测试（TDD）
 * 使用 MockitoExtension，不需要 Spring 上下文。
 */
@ExtendWith(MockitoExtension.class)
class LeadServiceTest {

    @Mock
    private CustLeadMapper leadMapper;

    @Mock
    private LeadImportBatchMapper batchMapper;

    @Mock
    private WorkflowApi workflowApi;

    @Mock
    private ApplicationEventPublisher eventPublisher;

    @InjectMocks
    private LeadService leadService;

    // ==================== createDraft ====================

    @Test
    void createDraft_shouldInsertAndReturnLead() {
        // given
        when(leadMapper.insert(any(CustLead.class))).thenReturn(1);

        // when
        CustLead result = leadService.createDraft(
                "测试企业", "91110000123456789X",
                "张三", "13800000001",
                "IT", null, "CORPORATE", 0, null, null, 0,
                "优质客户", null, null, "INTERNAL", null, null,
                "E001", "ORG001"
        );

        // then
        assertThat(result).isNotNull();
        assertThat(result.getId()).isNotNull();
        assertThat(result.getLeadNo()).startsWith("LEAD_");
        assertThat(result.getLeadStatus()).isEqualTo(LeadStatus.DRAFT.getCode());
        assertThat(result.getLeadOp()).isEqualTo(LeadOp.CREATE.getCode());
        assertThat(result.getVersionNo()).isEqualTo(1);
        assertThat(result.getIsLatest()).isEqualTo(1);
        assertThat(result.getCreatedBy()).isEqualTo("E001");
        assertThat(result.getOwnerOrgId()).isEqualTo("ORG001");
        assertThat(result.getDeleted()).isEqualTo(0);
        verify(leadMapper).insert(any(CustLead.class));
    }

    @Test
    void createDraft_shouldThrowWhenLeadNoExists() {
        // given: 模拟数据库唯一键冲突（leadNo 重复时 DB 抛出 DuplicateKeyException）
        when(leadMapper.insert(any(CustLead.class)))
                .thenThrow(new org.springframework.dao.DuplicateKeyException("Duplicate entry"));

        // when/then
        assertThatThrownBy(() -> leadService.createDraft(
                "测试企业", "91110000123456789X",
                "张三", "13800000001",
                "IT", null, "CORPORATE", 0, null, null, 0,
                null, null, null, null, null, null,
                "E001", "ORG001"
        )).isInstanceOf(RuntimeException.class);
    }

    // ==================== updateDraft ====================

    @Test
    void updateDraft_shouldUpdateFields() {
        // given: 存在草稿状态线索
        CustLead existing = buildDraftLead("lead-001");
        when(leadMapper.selectById("lead-001")).thenReturn(existing);
        when(leadMapper.updateById(any(CustLead.class))).thenReturn(1);

        // when
        CustLead result = leadService.updateDraft(
                "lead-001", "更新企业名", null, "李四", "13900000002",
                null, null, null, null, null, null, null,
                null, null, null, null, null, null, "E001"
        );

        // then
        assertThat(result).isNotNull();
        verify(leadMapper).updateById(any(CustLead.class));
    }

    @Test
    void updateDraft_shouldThrowWhenNotDraft() {
        // given: 线索处于 SUBMITTED 状态（非草稿）
        CustLead existing = buildDraftLead("lead-001");
        existing.setLeadStatus(LeadStatus.SUBMITTED.getCode());
        when(leadMapper.selectById("lead-001")).thenReturn(existing);

        // when/then
        assertThatThrownBy(() -> leadService.updateDraft(
                "lead-001", "更新企业名", null, null, null,
                null, null, null, null, null, null, null,
                null, null, null, null, null, null, "E001"
        ))
                .isInstanceOf(BizException.class)
                .hasFieldOrPropertyWithValue("code", CustomerErrorCode.LEAD_EDIT_FORBIDDEN.getCode());

        verify(leadMapper, never()).updateById(any());
    }

    // ==================== deleteDraft ====================

    @Test
    void deleteDraft_shouldSoftDelete() {
        // given: 存在草稿线索
        CustLead existing = buildDraftLead("lead-001");
        when(leadMapper.selectById("lead-001")).thenReturn(existing);
        when(leadMapper.updateById(any(CustLead.class))).thenReturn(1);

        // when
        leadService.deleteDraft("lead-001", "E001");

        // then: 验证 deleted=1 被设置
        ArgumentCaptor<CustLead> captor = ArgumentCaptor.forClass(CustLead.class);
        verify(leadMapper).updateById(captor.capture());
        assertThat(captor.getValue().getDeleted()).isEqualTo(1);
    }

    // ==================== submitForApproval ====================

    @Test
    void submitForApproval_shouldLockAndStartWorkflow() {
        // given
        CustLead existing = buildDraftLead("lead-001");
        when(leadMapper.selectForUpdate("lead-001")).thenReturn(existing);
        when(leadMapper.updateStatusById(anyString(), anyString(), anyString())).thenReturn(1);

        WorkflowLaunchResp launchResp = new WorkflowLaunchResp();
        launchResp.setProcessInstanceId("proc-001");
        launchResp.setBusinessKey("LEAD:lead-001");
        when(workflowApi.startProcess(any(StartProcessCmd.class))).thenReturn(launchResp);

        // when
        leadService.submitForApproval("lead-001", "E001", "ORG001");

        // then: 先更新为 SUBMITTED，再启动流程，再更新为 IN_APPROVAL
        verify(leadMapper).selectForUpdate("lead-001");
        verify(workflowApi).startProcess(any(StartProcessCmd.class));
        // 状态更新被调用两次：SUBMITTED + IN_APPROVAL
        verify(leadMapper, times(2)).updateStatusById(anyString(), anyString(), anyString());
    }

    @Test
    void submitForApproval_shouldThrowWhenNotDraft() {
        // given: 线索处于 IN_APPROVAL 状态
        CustLead existing = buildDraftLead("lead-001");
        existing.setLeadStatus(LeadStatus.IN_APPROVAL.getCode());
        when(leadMapper.selectForUpdate("lead-001")).thenReturn(existing);

        // when/then
        assertThatThrownBy(() -> leadService.submitForApproval("lead-001", "E001", "ORG001"))
                .isInstanceOf(BizException.class)
                .hasFieldOrPropertyWithValue("code", CustomerErrorCode.LEAD_NOT_SUBMITTABLE.getCode());

        verify(workflowApi, never()).startProcess(any());
    }

    // ==================== getById ====================

    @Test
    void getById_shouldReturnLead() {
        // given
        CustLead existing = buildDraftLead("lead-001");
        when(leadMapper.selectById("lead-001")).thenReturn(existing);

        // when
        CustLead result = leadService.getById("lead-001");

        // then
        assertThat(result).isNotNull();
        assertThat(result.getId()).isEqualTo("lead-001");
    }

    @Test
    void getById_shouldThrowWhenNotFound() {
        // given
        when(leadMapper.selectById("not-exist")).thenReturn(null);

        // when/then
        assertThatThrownBy(() -> leadService.getById("not-exist"))
                .isInstanceOf(BizException.class)
                .hasFieldOrPropertyWithValue("code", CustomerErrorCode.LEAD_NOT_FOUND.getCode());
    }

    // ==================== listPage ====================

    @Test
    void listPage_shouldCalculateOffset() {
        // given: pageNo=2, pageSize=10 -> offset = (2-1)*10 = 10
        CustLead lead = buildDraftLead("lead-001");
        when(leadMapper.selectPage(isNull(), isNull(), isNull(), eq(10), eq(10)))
                .thenReturn(Collections.singletonList(lead));
        when(leadMapper.countPage(isNull(), isNull(), isNull())).thenReturn(15L);

        // when
        PageResult<CustLead> result = leadService.listPage(null, null, null, 2, 10);

        // then
        assertThat(result.getPageNo()).isEqualTo(2);
        assertThat(result.getPageSize()).isEqualTo(10);
        assertThat(result.getTotal()).isEqualTo(15L);
        assertThat(result.getRecords()).hasSize(1);
        verify(leadMapper).selectPage(null, null, null, 10, 10);
    }

    // ==================== 辅助方法 ====================

    private CustLead buildDraftLead(String id) {
        CustLead lead = new CustLead();
        lead.setId(id);
        lead.setLeadNo("LEAD_20260414_0001");
        lead.setLeadStatus(LeadStatus.DRAFT.getCode());
        lead.setLeadOp(LeadOp.CREATE.getCode());
        lead.setVersionNo(1);
        lead.setIsLatest(1);
        lead.setCustName("测试企业");
        lead.setCreatedBy("E001");
        lead.setOwnerOrgId("ORG001");
        lead.setDeleted(0);
        return lead;
    }
}
