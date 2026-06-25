package com.bank.branch.platform.report.controller;

import com.bank.branch.platform.auth.api.BizScopeApi;
import com.bank.branch.platform.auth.api.CurrentUserApi;
import com.bank.branch.platform.auth.api.OrgApi;
import com.bank.branch.platform.auth.api.dto.DataScopeContext;
import com.bank.branch.platform.common.security.annotation.BizAuth;
import com.bank.branch.platform.common.security.enums.BizAction;
import com.bank.branch.platform.common.security.enums.BizType;
import com.bank.branch.platform.common.security.enums.DataScopeType;
import com.bank.branch.platform.common.web.PageResult;
import com.bank.branch.platform.common.web.ResponseWrapper;
import com.bank.branch.platform.governance.api.FileApi;
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
    private final FileApi fileApi;

    // 导入/禁用/启用/删除仍按角色判定（这是「能操作」权限，非数据范围）；行级数据范围已改读 DataScope。
    // 仅：自由报表操作人 可操作（管理员 / 资财部负责人不再具备这些操作按钮，仅保留查看）。
    private static final String ROLE_FREE_REPORT_OPERATOR = "R_2FAB45A1"; // 自由报表操作人

    private static final String STATUS_DISABLED = "DISABLED";
    private static final String STATUS_SUCCESS = "SUCCESS";

    /** 是否自由报表操作人：可导入 / 禁用 / 启用 / 查看禁用文件 */
    private boolean isOperator() {
        Set<String> rc = currentUserApi.getCurrentRoleCodes();
        return rc != null && rc.contains(ROLE_FREE_REPORT_OPERATOR);
    }

    /** 行级数据范围解析结果 */
    private static final class RowScope {
        String mode;            // ALL / ORG_SUBTREE / BRANCH_EMP / SELF
        String selfEmpNo;       // 当前用户工号(=username, 匹配报表 COL_1)
        String selfName;        // 当前用户姓名(=displayName, 匹配报表 COL_2)
        List<String> orgCodes;  // ORG_SUBTREE 模式下本机构子树编码
    }

    /**
     * 行级数据范围解析——按「权限配置 → 数据范围矩阵」的 REPORT DataScope 配置走，
     * 与动态查询同源（{@code bizScopeApi.buildScopeContext}），不再写死角色码。
     * <p>DataScope → mode：ALL→ALL；ORG_SUBTREE→本机构子树；ORG→仅本机构；其余(SELF/SELF_*等)→仅本人。
     * selfEmpNo/selfName 仍取当前用户工号/姓名，用于 SELF 模式匹配报表 COL_1/COL_2。</p>
     */
    private RowScope resolveRowScope() {
        var ctx = currentUserApi.getCurrentUserContext();
        String empId = currentUserApi.getCurrentEmpId();
        RowScope s = new RowScope();
        s.selfEmpNo = ctx != null ? ctx.username() : null;
        s.selfName = ctx != null ? ctx.displayName() : null;

        DataScopeContext scope = bizScopeApi.buildScopeContext(empId, BizType.REPORT, BizAction.LIST);
        DataScopeType type = scope != null ? scope.scopeType() : null;
        if (type == DataScopeType.ALL) {
            s.mode = "ALL";
        } else if (type == DataScopeType.ORG_SUBTREE) {
            s.mode = "ORG_SUBTREE";
            Set<String> codes = scope.orgSubtreeCodes();
            s.orgCodes = (codes != null && !codes.isEmpty()) ? List.copyOf(codes)
                    : (scope.orgCode() != null ? List.of(scope.orgCode()) : List.of("__none__"));
        } else if (type == DataScopeType.ORG) {
            // 仅本机构：用 ORG_SUBTREE 通道但只放本机构一个编码
            s.mode = "ORG_SUBTREE";
            s.orgCodes = scope.orgCode() != null ? List.of(scope.orgCode()) : List.of("__none__");
        } else {
            // SELF / SELF_CREATED / SELF_ASSIGNED / WORKFLOW_PARTICIPANT / null → 仅本人
            s.mode = "SELF";
        }
        return s;
    }

    @Operation(summary = "导入 Excel")
    @PostMapping("/import")
    @BizAuth(bizType = BizType.REPORT, action = BizAction.WRITE)
    public ResponseWrapper<Map<String, String>> importExcel(
            @RequestParam(value = "reportName", required = false) String reportName,
            @RequestParam("file") MultipartFile file) {
        String empId = currentUserApi.getCurrentEmpId();
        // 仅 系统管理员 / 资财部负责人 / 自由报表操作人 可导入
        if (!isOperator()) {
            throw new com.bank.branch.platform.common.web.exception.BizException("RPT-40302",
                    "仅系统管理员/资财部负责人/自由报表操作人可导入");
        }
        String empName = currentUserApi.getCurrentUserContext().displayName();
        if (reportName == null || reportName.trim().isEmpty()) {
            reportName = file.getOriginalFilename();
        }
        reportName = stripExcelExt(reportName.trim());
        String batchId = service.importExcel(reportName, file, empId, empName != null ? empName : empId);
        return ResponseWrapper.success(Map.of("batchId", batchId));
    }

    /** 去掉报表名后缀 .xlsx / .xlsm / .xls（大小写不敏感），让列表展示更干净 */
    private static String stripExcelExt(String name) {
        if (name == null) return null;
        String lower = name.toLowerCase();
        for (String ext : new String[]{".xlsx", ".xlsm", ".xls"}) {
            if (lower.endsWith(ext)) return name.substring(0, name.length() - ext.length());
        }
        return name;
    }

    @Operation(summary = "查询批次数据（分页+关键字搜索，按角色做行级数据隔离）")
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
        // 禁用文件：非操作人看不到任何数据
        if (STATUS_DISABLED.equals(service.getBatchStatus(batchId)) && !isOperator()) {
            return ResponseWrapper.success(PageResult.of(pageNo, pageSize, 0L, java.util.Collections.emptyList()));
        }
        // empNo/empName 优先；兼容旧 keyword 参数
        String searchKey = keyword;
        if ((empNo != null && !empNo.isBlank()) || (empName != null && !empName.isBlank())) {
            searchKey = null; // 用精确字段搜，不走 keyword
        }
        RowScope s = resolveRowScope();
        PageResult<Map<String, Object>> r = service.queryData(batchId, searchKey, empNo, empName,
                s.mode, s.selfEmpNo, s.selfName, s.orgCodes, pageNo, pageSize);
        return ResponseWrapper.success(r);
    }

    @Operation(summary = "获取批次列定义")
    @GetMapping("/columns")
    @BizAuth(bizType = BizType.REPORT, action = BizAction.READ)
    public ResponseWrapper<List<Map<String, String>>> getColumns(@RequestParam("batchId") String batchId) {
        if (STATUS_DISABLED.equals(service.getBatchStatus(batchId)) && !isOperator()) {
            return ResponseWrapper.success(java.util.Collections.emptyList());
        }
        return ResponseWrapper.success(service.getColumns(batchId));
    }

    @Operation(summary = "导入批次列表（文件级全员公开；禁用文件仅操作人可见）")
    @GetMapping("/batches")
    @BizAuth(bizType = BizType.REPORT, action = BizAction.LIST)
    public ResponseWrapper<List<RptFreeReportBatch>> listBatches(
            @RequestParam(value = "keyword", required = false) String keyword,
            @RequestParam(value = "dateFrom", required = false) @org.springframework.format.annotation.DateTimeFormat(iso = org.springframework.format.annotation.DateTimeFormat.ISO.DATE) java.time.LocalDate dateFrom,
            @RequestParam(value = "dateTo", required = false) @org.springframework.format.annotation.DateTimeFormat(iso = org.springframework.format.annotation.DateTimeFormat.ISO.DATE) java.time.LocalDate dateTo) {
        // 文件列表全员公开（支行及以上均可见全部文件）；行级隔离在 /data 做。
        // 禁用文件仅自由报表操作人可见，其余角色看不到。
        boolean includeDisabled = isOperator();
        return ResponseWrapper.success(service.listBatches(keyword, dateFrom, dateTo, includeDisabled));
    }

    @Operation(summary = "禁用自由报表（仅操作人）：禁用后非操作人列表里不可见、看不到数据")
    @PostMapping("/batches/{batchId}/disable")
    @BizAuth(bizType = BizType.REPORT, action = BizAction.WRITE)
    public ResponseWrapper<Void> disableBatch(@PathVariable String batchId) {
        if (!isOperator()) {
            throw new com.bank.branch.platform.common.web.exception.BizException("RPT-40302", "仅自由报表操作人员可禁用");
        }
        service.updateBatchStatus(batchId, STATUS_DISABLED);
        return ResponseWrapper.success(null);
    }

    @Operation(summary = "启用自由报表（仅操作人）")
    @PostMapping("/batches/{batchId}/enable")
    @BizAuth(bizType = BizType.REPORT, action = BizAction.WRITE)
    public ResponseWrapper<Void> enableBatch(@PathVariable String batchId) {
        if (!isOperator()) {
            throw new com.bank.branch.platform.common.web.exception.BizException("RPT-40302", "仅自由报表操作人员可启用");
        }
        service.updateBatchStatus(batchId, STATUS_SUCCESS);
        return ResponseWrapper.success(null);
    }

    @Operation(summary = "下载原始文件")
    @GetMapping("/batches/{batchId}/download")
    @BizAuth(bizType = BizType.REPORT, action = BizAction.READ)
    public ResponseWrapper<Map<String, String>> download(@PathVariable String batchId) {
        String url = service.getDownloadUrl(batchId);
        return ResponseWrapper.success(Map.of("url", url));
    }

    @Operation(summary = "下载文件（按角色行级过滤后生成 Excel，不泄露无权查看的数据行）")
    @GetMapping("/batches/{batchId}/download-file")
    @BizAuth(bizType = BizType.REPORT, action = BizAction.READ)
    public void downloadFile(@PathVariable String batchId, jakarta.servlet.http.HttpServletResponse resp) throws java.io.IOException {
        if (STATUS_DISABLED.equals(service.getBatchStatus(batchId)) && !isOperator()) {
            resp.sendError(jakarta.servlet.http.HttpServletResponse.SC_FORBIDDEN, "报表已禁用");
            return;
        }
        // 下载内容与在线查看一致：按当前用户角色做行级过滤后重新生成 Excel
        RowScope s = resolveRowScope();
        byte[] bytes = service.exportFilteredExcel(batchId, s.mode, s.selfEmpNo, s.selfName, s.orgCodes);
        String fileName = service.getBatchFileName(batchId);
        if (!fileName.toLowerCase().endsWith(".xlsx")) fileName = fileName + ".xlsx";
        resp.setContentType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");
        resp.setHeader("Content-Disposition",
            "attachment; filename=\"" + java.net.URLEncoder.encode(fileName, "UTF-8") + "\"");
        resp.getOutputStream().write(bytes);
        resp.flushBuffer();
    }

    @Operation(summary = "删除批次数据")
    @DeleteMapping("/batches/{batchId}")
    @BizAuth(bizType = BizType.REPORT, action = BizAction.DELETE)
    public ResponseWrapper<Void> deleteBatch(@PathVariable String batchId) {
        if (!isOperator()) {
            throw new com.bank.branch.platform.common.web.exception.BizException("RPT-40302",
                    "仅系统管理员/资财部负责人/自由报表操作人可删除");
        }
        service.deleteBatch(batchId);
        return ResponseWrapper.success(null);
    }
}
