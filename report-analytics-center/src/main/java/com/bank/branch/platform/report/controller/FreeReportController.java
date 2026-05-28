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

    private static final String ROLE_FREE_REPORT_OPERATOR = "R_2FAB45A1";

    @Operation(summary = "导入 Excel")
    @PostMapping("/import")
    @BizAuth(bizType = BizType.REPORT, action = BizAction.WRITE)
    public ResponseWrapper<Map<String, String>> importExcel(
            @RequestParam(value = "reportName", required = false) String reportName,
            @RequestParam("file") MultipartFile file) {
        String empId = currentUserApi.getCurrentEmpId();
        // 只有"自由报表操作人员"角色或 SYS_ADMIN 可以导入
        Set<String> roleCodes = currentUserApi.getCurrentRoleCodes();
        boolean isAdmin = roleCodes != null && roleCodes.contains("SYS_ADMIN");
        boolean isOperator = roleCodes != null && roleCodes.contains(ROLE_FREE_REPORT_OPERATOR);
        if (!isAdmin && !isOperator) {
            throw new com.bank.branch.platform.common.web.exception.BizException("RPT-40302", "仅自由报表操作人员可导入");
        }
        String empName = currentUserApi.getCurrentUserContext().displayName();
        if (reportName == null || reportName.trim().isEmpty()) {
            reportName = file.getOriginalFilename();
        }
        String batchId = service.importExcel(reportName.trim(), file, empId, empName != null ? empName : empId);
        return ResponseWrapper.success(Map.of("batchId", batchId));
    }

    @Operation(summary = "查询批次数据（分页+关键字搜索）",
               description = "权限在 batch 级别控制（listBatches 已过滤），能看到 batch 就能看全部数据行")
    @GetMapping("/data")
    @BizAuth(bizType = BizType.REPORT, action = BizAction.LIST)
    public ResponseWrapper<PageResult<Map<String, Object>>> queryData(
            @RequestParam("batchId") String batchId,
            @RequestParam(value = "keyword", required = false) String keyword,
            @RequestParam(value = "empNo", required = false) String empNo,
            @RequestParam(value = "empName", required = false) String empName,
            @RequestParam(value = "pageNo", defaultValue = "1") int pageNo,
            @RequestParam(value = "pageSize", defaultValue = "20") int pageSize) {
        if (pageSize > 100) pageSize = 100;
        // empNo/empName 优先；兼容旧 keyword 参数
        String searchKey = keyword;
        if ((empNo != null && !empNo.isBlank()) || (empName != null && !empName.isBlank())) {
            searchKey = null; // 用精确字段搜，不走 keyword
        }
        PageResult<Map<String, Object>> r = service.queryData(batchId, searchKey, empNo, empName, null, null, pageNo, pageSize);
        return ResponseWrapper.success(r);
    }

    @Operation(summary = "获取批次列定义")
    @GetMapping("/columns")
    @BizAuth(bizType = BizType.REPORT, action = BizAction.READ)
    public ResponseWrapper<List<Map<String, String>>> getColumns(@RequestParam("batchId") String batchId) {
        return ResponseWrapper.success(service.getColumns(batchId));
    }

    @Operation(summary = "导入批次列表（按数据范围过滤）")
    @GetMapping("/batches")
    @BizAuth(bizType = BizType.REPORT, action = BizAction.LIST)
    public ResponseWrapper<List<RptFreeReportBatch>> listBatches(
            @RequestParam(value = "keyword", required = false) String keyword,
            @RequestParam(value = "dateFrom", required = false) @org.springframework.format.annotation.DateTimeFormat(iso = org.springframework.format.annotation.DateTimeFormat.ISO.DATE) java.time.LocalDate dateFrom,
            @RequestParam(value = "dateTo", required = false) @org.springframework.format.annotation.DateTimeFormat(iso = org.springframework.format.annotation.DateTimeFormat.ISO.DATE) java.time.LocalDate dateTo) {
        String empId = currentUserApi.getCurrentEmpId();
        // 数据范围：SELF 只看自己的，ORG_SUBTREE 看本机构，ALL 看所有
        String scopeEmpId = null;
        List<String> scopeOrgCodes = null;
        try {
            var scopeCtx = bizScopeApi.buildScopeContext(empId, BizType.REPORT, BizAction.LIST);
            var scope = scopeCtx != null ? scopeCtx.scopeType() : null;
            if (scope == null || scope == com.bank.branch.platform.common.security.enums.DataScopeType.SELF
                    || scope == com.bank.branch.platform.common.security.enums.DataScopeType.SELF_CREATED) {
                scopeEmpId = empId;
            } else if (scope == com.bank.branch.platform.common.security.enums.DataScopeType.ORG
                    || scope == com.bank.branch.platform.common.security.enums.DataScopeType.ORG_SUBTREE) {
                String orgCode = currentUserApi.getCurrentOrgCode();
                Set<String> codes = orgApi.getOrgSubtreeCodes(orgCode);
                scopeOrgCodes = codes != null ? List.copyOf(codes) : List.of(orgCode);
            }
            // ALL: 不限制（支行领导 / SYS_ADMIN）
        } catch (Exception e) {
            log.warn("[FreeReport.listBatches] DataScope 解析失败 empId={}, fallback SELF", empId, e);
            scopeEmpId = empId;
        }
        return ResponseWrapper.success(service.listBatches(keyword, dateFrom, dateTo, scopeEmpId, scopeOrgCodes));
    }

    @Operation(summary = "下载原始文件")
    @GetMapping("/batches/{batchId}/download")
    @BizAuth(bizType = BizType.REPORT, action = BizAction.READ)
    public ResponseWrapper<Map<String, String>> download(@PathVariable String batchId) {
        String url = service.getDownloadUrl(batchId);
        return ResponseWrapper.success(Map.of("url", url));
    }

    @Operation(summary = "直接下载文件流（前端用，避免 MinIO 跨域）")
    @GetMapping("/batches/{batchId}/download-file")
    @BizAuth(bizType = BizType.REPORT, action = BizAction.READ)
    public void downloadFile(@PathVariable String batchId, jakarta.servlet.http.HttpServletResponse resp) throws java.io.IOException {
        String url = service.getDownloadUrl(batchId);
        // 从 MinIO presigned URL 拿文件流，写到 response
        try (java.io.InputStream in = new java.net.URL(url).openStream()) {
            String fileName = service.getBatchFileName(batchId);
            resp.setContentType("application/octet-stream");
            resp.setHeader("Content-Disposition",
                "attachment; filename=\"" + java.net.URLEncoder.encode(fileName, "UTF-8") + "\"");
            in.transferTo(resp.getOutputStream());
            resp.flushBuffer();
        }
    }

    @Operation(summary = "删除批次数据")
    @DeleteMapping("/batches/{batchId}")
    @BizAuth(bizType = BizType.REPORT, action = BizAction.DELETE)
    public ResponseWrapper<Void> deleteBatch(@PathVariable String batchId) {
        service.deleteBatch(batchId);
        return ResponseWrapper.success(null);
    }
}
