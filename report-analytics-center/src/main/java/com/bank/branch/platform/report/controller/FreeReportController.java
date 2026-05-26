package com.bank.branch.platform.report.controller;

import com.bank.branch.platform.auth.api.BizScopeApi;
import com.bank.branch.platform.auth.api.CurrentUserApi;
import com.bank.branch.platform.auth.api.OrgApi;
import com.bank.branch.platform.common.security.annotation.BizAuth;
import com.bank.branch.platform.common.security.enums.BizAction;
import com.bank.branch.platform.common.security.enums.BizType;
import com.bank.branch.platform.common.web.PageResult;
import com.bank.branch.platform.common.web.ResponseWrapper;
import com.bank.branch.platform.report.entity.RptFreeReportBatch;
import com.bank.branch.platform.report.service.FreeReportService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.util.Map;
import java.util.Set;

@Slf4j
@Tag(name = "KPI/积分自由报表")
@RestController
@RequestMapping("/api/reports/free")
@RequiredArgsConstructor
public class FreeReportController {

    private final FreeReportService service;
    private final CurrentUserApi currentUserApi;
    private final BizScopeApi bizScopeApi;
    private final OrgApi orgApi;

    @Operation(summary = "导入 Excel")
    @PostMapping("/import")
    @BizAuth(bizType = BizType.REPORT, action = BizAction.WRITE)
    public ResponseWrapper<Map<String, String>> importExcel(
            @RequestParam("reportName") String reportName,
            @RequestParam("file") MultipartFile file) {
        String empId = currentUserApi.getCurrentEmpId();
        String empName = currentUserApi.getCurrentUserContext().displayName();
        String batchId = service.importExcel(reportName, file, empId, empName != null ? empName : empId);
        return ResponseWrapper.success(Map.of("batchId", batchId));
    }

    @Operation(summary = "查询批次数据（分页+姓名搜索+数据权限）")
    @GetMapping("/data")
    @BizAuth(bizType = BizType.REPORT, action = BizAction.LIST)
    public ResponseWrapper<PageResult<Map<String, Object>>> queryData(
            @RequestParam("batchId") String batchId,
            @RequestParam(value = "keyword", required = false) String keyword,
            @RequestParam(value = "pageNo", defaultValue = "1") int pageNo,
            @RequestParam(value = "pageSize", defaultValue = "20") int pageSize) {

        String empId = currentUserApi.getCurrentEmpId();
        // 数据权限：解析当前用户对 REPORT 的 DataScope
        String scopeEmpId = null;
        List<String> scopeOrgCodes = null;

        try {
            var scopeCtx = bizScopeApi.buildScopeContext(empId, BizType.REPORT, BizAction.LIST);
            var scope = scopeCtx != null ? scopeCtx.scopeType() : null;
            if (scope == null || scope == com.bank.branch.platform.common.security.enums.DataScopeType.SELF
                    || scope == com.bank.branch.platform.common.security.enums.DataScopeType.SELF_CREATED
                    || scope == com.bank.branch.platform.common.security.enums.DataScopeType.SELF_ASSIGNED) {
                scopeEmpId = empId;
            } else if (scope == com.bank.branch.platform.common.security.enums.DataScopeType.ORG_SUBTREE) {
                String orgCode = currentUserApi.getCurrentOrgCode();
                Set<String> codes = orgApi.getOrgSubtreeCodes(orgCode);
                scopeOrgCodes = codes != null ? List.copyOf(codes) : List.of(orgCode);
            }
            // ALL: 不限制
        } catch (Exception e) {
            log.warn("[FreeReport.queryData] DataScope 解析失败 empId={}, fallback SELF", empId, e);
            scopeEmpId = empId;
        }

        if (pageSize > 100) pageSize = 100;
        PageResult<Map<String, Object>> r = service.queryData(batchId, keyword, scopeEmpId, scopeOrgCodes, pageNo, pageSize);
        return ResponseWrapper.success(r);
    }

    @Operation(summary = "获取批次列定义")
    @GetMapping("/columns")
    @BizAuth(bizType = BizType.REPORT, action = BizAction.READ)
    public ResponseWrapper<List<Map<String, String>>> getColumns(@RequestParam("batchId") String batchId) {
        return ResponseWrapper.success(service.getColumns(batchId));
    }

    @Operation(summary = "导入批次列表")
    @GetMapping("/batches")
    @BizAuth(bizType = BizType.REPORT, action = BizAction.LIST)
    public ResponseWrapper<List<RptFreeReportBatch>> listBatches(
            @RequestParam(value = "dateFrom", required = false) @org.springframework.format.annotation.DateTimeFormat(iso = org.springframework.format.annotation.DateTimeFormat.ISO.DATE) java.time.LocalDate dateFrom,
            @RequestParam(value = "dateTo", required = false) @org.springframework.format.annotation.DateTimeFormat(iso = org.springframework.format.annotation.DateTimeFormat.ISO.DATE) java.time.LocalDate dateTo) {
        return ResponseWrapper.success(service.listBatches(dateFrom, dateTo));
    }

    @Operation(summary = "下载原始文件")
    @GetMapping("/batches/{batchId}/download")
    @BizAuth(bizType = BizType.REPORT, action = BizAction.READ)
    public ResponseWrapper<Map<String, String>> download(@PathVariable String batchId) {
        String url = service.getDownloadUrl(batchId);
        return ResponseWrapper.success(Map.of("url", url));
    }

    @Operation(summary = "删除批次数据")
    @DeleteMapping("/batches/{batchId}")
    @BizAuth(bizType = BizType.REPORT, action = BizAction.DELETE)
    public ResponseWrapper<Void> deleteBatch(@PathVariable String batchId) {
        service.deleteBatch(batchId);
        return ResponseWrapper.success(null);
    }
}
