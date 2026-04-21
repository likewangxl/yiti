package com.bank.branch.platform.customer.controller;

import com.bank.branch.platform.common.aop.annotation.AuditLog;
import com.bank.branch.platform.common.security.annotation.BizAuth;
import com.bank.branch.platform.common.security.enums.BizAction;
import com.bank.branch.platform.common.security.enums.BizType;
import com.bank.branch.platform.customer.entity.CustMaster;
import com.bank.branch.platform.customer.service.CustomerService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.io.IOException;
import java.io.PrintWriter;
import java.util.List;

/**
 * 客户列表导出 REST 控制器（高危操作，独立 URL）。
 * <p>
 * 提供客户主档的 CSV 格式批量导出，单次最多 10000 行保护限制。
 * 高危操作，需要 EXPORT 权限，并配置 @AuditLog reasonRequired=true 强制留痕。
 * </p>
 */
@Slf4j
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/customers")
@Validated
@Tag(name = "客户主档管理")
public class CustomerExportController {

    /** 单次导出最大行数保护，防止大数据量导出拖垮数据库 */
    private static final int MAX_EXPORT_ROWS = 10000;

    private final CustomerService customerService;

    /**
     * 导出客户列表（高危操作，CSV 格式）。
     * <p>
     * 流式写出 CSV 至 HttpServletResponse，避免大对象驻留内存。
     * 单次最多导出 {@value MAX_EXPORT_ROWS} 行，超出部分截断。
     * 调用方须在请求头携带审计原因（由 @AuditLog reasonRequired=true 切面校验）。
     * </p>
     *
     * @param keyword  关键词（模糊匹配客户名/统一信用代码），可为 null
     * @param status   客户状态过滤（ACTIVE / INACTIVE），可为 null
     * @param response HttpServletResponse 用于流式写出
     * @throws IOException IO 异常（写出流时发生）
     */
    @GetMapping("/export")
    @BizAuth(bizType = BizType.CUSTOMER, action = BizAction.EXPORT)
    @AuditLog(action = "EXPORT_CUSTOMER_LIST", resourceType = "CUSTOMER", reasonRequired = true)
    @Operation(summary = "导出客户列表（CSV，高危操作）")
    public void exportCustomers(
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) String status,
            HttpServletResponse response) throws IOException {

        log.info("[CustomerExportController.exportCustomers] keyword={}, status={}", keyword, status);

        // 设置 CSV 响应头
        response.setContentType("text/csv; charset=UTF-8");
        response.setHeader("Content-Disposition",
                "attachment; filename=\"customers_" + System.currentTimeMillis() + ".csv\"");

        // 最多 MAX_EXPORT_ROWS 行保护，防止全量拖垮数据库
        List<CustMaster> list = customerService.listAllForExport(keyword, status, MAX_EXPORT_ROWS);
        log.info("[CustomerExportController.exportCustomers] 导出记录数={}", list.size());

        try (PrintWriter out = response.getWriter()) {
            // 写出 CSV 表头
            out.println("客户编号,客户名称,统一社会信用代码,状态,创建时间");
            // 逐行写出数据（流式，不积累内存）
            for (CustMaster c : list) {
                out.println(String.join(",",
                        quote(c.getCustNo()),
                        quote(c.getCustName()),
                        quote(c.getUnifiedCreditCode()),
                        quote(c.getStatus()),
                        quote(c.getCreatedTime() != null ? c.getCreatedTime().toString() : "")));
            }
        }
    }

    /**
     * CSV 字段安全引用：用双引号包裹，内部双引号转义为两个双引号（RFC 4180 规范）。
     *
     * @param s 原始字符串，null 时返回空字符串
     * @return 安全引用后的 CSV 字段
     */
    private static String quote(String s) {
        if (s == null) {
            return "";
        }
        // 内部双引号转义为两个双引号（RFC 4180）
        return "\"" + s.replace("\"", "\"\"") + "\"";
    }
}
