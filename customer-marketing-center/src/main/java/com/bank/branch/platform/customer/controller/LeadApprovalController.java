package com.bank.branch.platform.customer.controller;

import com.bank.branch.platform.auth.api.CurrentUserApi;
import com.bank.branch.platform.auth.api.BizScopeApi;
import com.bank.branch.platform.auth.api.dto.DataScopeContext;
import com.bank.branch.platform.common.aop.annotation.AuditLog;
import com.bank.branch.platform.common.security.annotation.BizAuth;
import com.bank.branch.platform.common.security.enums.BizAction;
import com.bank.branch.platform.common.security.enums.BizType;
import com.bank.branch.platform.common.web.PageResult;
import com.bank.branch.platform.common.web.ResponseWrapper;
import com.bank.branch.platform.customer.dto.resp.LeadRespDTO;
import com.bank.branch.platform.customer.service.LeadApprovalService;
import com.bank.branch.platform.customer.service.LeadEntryService;
import com.bank.branch.platform.workflow.api.dto.TaskRespDTO;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.io.IOException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;

/** 线索审批页面后端接口；审批动作继续复用工作流中心统一接口。 */
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/lead-approvals")
@Tag(name = "线索审批")
public class LeadApprovalController {

    private final LeadApprovalService approvalService;
    private final LeadEntryService entryService;
    private final CurrentUserApi currentUserApi;
    private final BizScopeApi bizScopeApi;

    @GetMapping
    @BizAuth(bizType = BizType.LEAD, action = BizAction.LIST)
    @Operation(summary = "查询线索审批待办/已办")
    public ResponseWrapper<TaskRespDTO> list(
            @RequestParam(defaultValue = "PENDING") String tab,
            @RequestParam(required = false) String keyword,
            @RequestParam(defaultValue = "1") int pageNo,
            @RequestParam(defaultValue = "20") int pageSize) {
        PageResult<TaskRespDTO> result = approvalService.list(
                tab, keyword, pageNo, pageSize, currentUserApi.getCurrentEmpId());
        return ResponseWrapper.page(result);
    }

    @GetMapping("/{leadId}")
    @BizAuth(bizType = BizType.LEAD, action = BizAction.READ)
    @Operation(summary = "查询线索审批完整详情")
    public ResponseWrapper<LeadRespDTO> detail(@PathVariable String leadId) {
        return ResponseWrapper.success(entryService.getVisibleDetail(
                leadId, currentUserApi.getCurrentEmpId(), currentUserApi.isSystemAdmin()));
    }

    @GetMapping("/export")
    @BizAuth(bizType = BizType.LEAD, action = BizAction.EXPORT)
    @AuditLog(action = "EXPORT", resourceType = "LEAD_APPROVAL")
    @Operation(summary = "导出全部已处理线索审批记录")
    public void export(@RequestParam(required = false) String keyword,
                       @RequestParam(required = false) String result,
                       HttpServletResponse response) throws IOException {
        String empId = currentUserApi.getCurrentEmpId();
        DataScopeContext scopeContext = bizScopeApi.buildScopeContext(
                empId, BizType.LEAD, BizAction.EXPORT);
        byte[] bytes = approvalService.exportReviewed(keyword, result, scopeContext);
        String filename = URLEncoder.encode("线索审批记录.xlsx", StandardCharsets.UTF_8)
                .replace("+", "%20");
        response.setContentType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");
        response.setHeader("Content-Disposition", "attachment;filename*=UTF-8''" + filename);
        response.setContentLength(bytes.length);
        response.getOutputStream().write(bytes);
    }
}
