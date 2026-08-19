package com.bank.branch.platform.customer.service;

import com.alibaba.excel.EasyExcel;
import com.bank.branch.platform.auth.api.dto.DataScopeContext;
import com.bank.branch.platform.common.security.enums.DataScopeType;
import com.bank.branch.platform.common.web.PageResult;
import com.bank.branch.platform.common.web.exception.BizException;
import com.bank.branch.platform.customer.dto.resp.LeadApprovalExportRow;
import com.bank.branch.platform.customer.entity.CustLead;
import com.bank.branch.platform.customer.enums.CustomerErrorCode;
import com.bank.branch.platform.customer.mapper.CustLeadMapper;
import com.bank.branch.platform.workflow.api.WorkflowQueryApi;
import com.bank.branch.platform.workflow.api.dto.TaskRespDTO;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.io.ByteArrayOutputStream;
import java.util.List;
import java.util.Set;

/** 线索审批页适配服务：列表复用工作流，导出读取业务快照。 */
@Service
@RequiredArgsConstructor
public class LeadApprovalService {

    private static final Set<String> REVIEW_RESULTS = Set.of("APPROVED", "REJECTED");

    private final WorkflowQueryApi workflowQueryApi;
    private final CustLeadMapper leadMapper;

    public PageResult<TaskRespDTO> list(String tab, String keyword, int pageNo,
                                        int pageSize, String empId) {
        int safePageNo = Math.max(pageNo, 1);
        int safePageSize = Math.min(Math.max(pageSize, 1), 100);
        if ("HISTORY".equalsIgnoreCase(tab)) {
            return workflowQueryApi.queryDoneList(empId, "LEAD", keyword, safePageNo, safePageSize);
        }
        return workflowQueryApi.queryTodoList(empId, "LEAD", keyword, safePageNo, safePageSize);
    }

    /** 按当前 LEAD 数据范围导出全部已处理线索。 */
    public byte[] exportReviewed(String keyword, String result, DataScopeContext scopeContext) {
        String normalized = StringUtils.hasText(result) ? result.trim().toUpperCase() : null;
        if (normalized != null && !REVIEW_RESULTS.contains(normalized)) {
            throw new BizException(CustomerErrorCode.LEAD_APPROVAL_RESULT_INVALID.getCode(),
                    CustomerErrorCode.LEAD_APPROVAL_RESULT_INVALID.getMessage());
        }
        DataScopeType scopeType = scopeContext == null || scopeContext.scopeType() == null
                ? DataScopeType.SELF_CREATED : scopeContext.scopeType();
        String empId = scopeContext == null ? null : scopeContext.empId();
        Set<String> orgCodes = exportOrgCodes(scopeContext, scopeType);
        List<LeadApprovalExportRow> rows = leadMapper
                .selectReviewedForExport(keyword, normalized, scopeType.getCode(), empId, orgCodes).stream()
                .map(this::toExportRow)
                .toList();
        try (ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            EasyExcel.write(out, LeadApprovalExportRow.class)
                    .sheet("已审批线索")
                    .doWrite(rows);
            return out.toByteArray();
        } catch (Exception ex) {
            throw new BizException(CustomerErrorCode.INTERNAL_ERROR.getCode(), "生成线索审批Excel失败");
        }
    }

    private Set<String> exportOrgCodes(DataScopeContext context, DataScopeType scopeType) {
        if (context == null) {
            return Set.of();
        }
        if (scopeType == DataScopeType.ORG && StringUtils.hasText(context.orgCode())) {
            return Set.of(context.orgCode());
        }
        if (scopeType == DataScopeType.ORG_SUBTREE && context.orgSubtreeCodes() != null) {
            return context.orgSubtreeCodes();
        }
        return Set.of();
    }

    private LeadApprovalExportRow toExportRow(CustLead lead) {
        LeadApprovalExportRow row = new LeadApprovalExportRow();
        row.setLeadNo(lead.getLeadNo());
        row.setLeadType(lead.getLeadType());
        row.setCustNo(lead.getCustNo());
        row.setCustName(lead.getCustName());
        row.setUnifiedCreditCode(lead.getUnifiedCreditCode());
        row.setContactPerson(lead.getContactPerson());
        row.setContactMobile(lead.getContactMobile());
        row.setIndustry(lead.getIndustry());
        row.setGroupType(lead.getGroupType());
        row.setGroupName(lead.getGroupName());
        row.setCustomerType(lead.getCustomerType());
        row.setKeystone(booleanText(lead.getIsKeystone()));
        row.setEnterpriseType(lead.getEnterpriseType());
        row.setAccountOpened(booleanText(lead.getIsAccountOpened()));
        row.setCustomerDesc(lead.getCustomerDesc());
        row.setCreditAmount(lead.getCreditAmount());
        row.setCreditExposureAmount(lead.getCreditExposureAmount());
        row.setLeadSource(lead.getLeadSource());
        row.setDistributionMode(lead.getDistributionMode());
        row.setMainManagerId(lead.getMainManagerId());
        row.setMainManagerOrgId(lead.getMainManagerOrgId());
        row.setTagIds(lead.getTagIds());
        row.setLeadStatus(lead.getLeadStatus());
        row.setSubmittedBy(lead.getSubmittedBy());
        row.setSubmittedTime(lead.getSubmittedTime());
        row.setReviewedBy(lead.getReviewedBy());
        row.setReviewedTime(lead.getReviewedTime());
        row.setRejectReason(lead.getRejectReason());
        row.setRemark(lead.getRemark());
        return row;
    }

    private String booleanText(Integer value) {
        return value == null ? null : (value == 1 ? "是" : "否");
    }
}
