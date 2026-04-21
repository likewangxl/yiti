package com.bank.branch.platform.customer.controller;

import com.bank.branch.platform.common.aop.annotation.AuditLog;
import com.bank.branch.platform.common.security.annotation.BizAuth;
import com.bank.branch.platform.common.security.enums.BizAction;
import com.bank.branch.platform.common.security.enums.BizType;
import com.bank.branch.platform.common.web.PageResult;
import com.bank.branch.platform.common.web.ResponseWrapper;
import com.bank.branch.platform.customer.dto.resp.TouchReportVO;
import com.bank.branch.platform.customer.dto.resp.TouchStatisticVO;
import com.bank.branch.platform.customer.service.TouchReportService;
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
 * 触达报告 REST 控制器。
 * <p>
 * 提供触达报告的分页列表查询、状态统计和数据导出三个端点。
 * 报告类接口均为只读操作，使用 @BizAuth 做权限控制。
 * 导出接口为高危操作，额外配置 @AuditLog 记录审计日志。
 * </p>
 */
@Slf4j
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/touch-reports")
@Validated
@Tag(name = "触达报告管理")
public class TouchReportController {

    private final TouchReportService touchReportService;

    /**
     * 查询触达报告列表（分页）。
     * <p>
     * 支持按关键词（模糊匹配 task_no 或客户名称）、任务状态、机构代码过滤。
     * </p>
     *
     * @param keyword  关键词（可选，模糊匹配 task_no 或 cust_name）
     * @param status   任务状态（可选，PENDING/SUCCESS/CANCELLED）
     * @param orgId    机构代码（可选）
     * @param pageNo   页码，默认 1
     * @param pageSize 每页大小，默认 20
     * @return 分页的触达报告列表
     */
    @GetMapping
    @BizAuth(bizType = BizType.TOUCH_REPORT, action = BizAction.LIST)
    @Operation(summary = "查询触达报告列表")
    public ResponseWrapper<TouchReportVO> listPage(
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String orgId,
            @RequestParam(defaultValue = "1") int pageNo,
            @RequestParam(defaultValue = "20") int pageSize) {
        log.info("[TouchReportController.listPage] keyword={}, status={}, orgId={}, pageNo={}, pageSize={}",
                keyword, status, orgId, pageNo, pageSize);
        PageResult<TouchReportVO> result = touchReportService.listPage(keyword, status, orgId, pageNo, pageSize);
        return ResponseWrapper.page(result);
    }

    /**
     * 查询触达任务状态统计。
     * <p>
     * 按 task_status 分组统计各状态任务数量，供前端绘制状态分布图。
     * orgId 为可选参数，不传时统计全量数据。
     * </p>
     *
     * @param orgId 机构代码（可选，null 时统计全量）
     * @return 按状态分组的统计列表
     */
    @GetMapping("/statistics")
    @BizAuth(bizType = BizType.TOUCH_REPORT, action = BizAction.READ)
    @Operation(summary = "查询触达任务状态统计")
    public ResponseWrapper<List<TouchStatisticVO>> statistics(
            @RequestParam(required = false) String orgId) {
        log.info("[TouchReportController.statistics] orgId={}", orgId);
        List<TouchStatisticVO> result = touchReportService.statistic(orgId);
        return ResponseWrapper.success(result);
    }

    /**
     * 导出触达报告（高危操作，CSV 格式）。
     * <p>
     * 流式写出 CSV 至 HttpServletResponse，避免大对象驻留内存。
     * 单次最多导出 10000 行，超出部分截断。
     * 高危操作，需要 EXPORT 权限，@AuditLog reasonRequired=true 强制留痕。
     * </p>
     *
     * @param keyword  关键词（模糊匹配 task_no 或 cust_name），可为 null
     * @param status   任务状态过滤（PENDING/SUCCESS/CANCELLED），可为 null
     * @param orgId    机构代码过滤，可为 null
     * @param response HttpServletResponse 用于流式写出
     * @throws IOException IO 异常（写出流时发生）
     */
    @GetMapping("/export")
    @BizAuth(bizType = BizType.TOUCH_REPORT, action = BizAction.EXPORT)
    @AuditLog(action = "EXPORT_TOUCH_REPORT", resourceType = "TOUCH_REPORT", reasonRequired = true)
    @Operation(summary = "导出触达报告（CSV，高危操作）")
    public void export(
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String orgId,
            HttpServletResponse response) throws IOException {

        log.info("[TouchReportController.export] keyword={}, status={}, orgId={}", keyword, status, orgId);

        // 设置 CSV 响应头
        response.setContentType("text/csv; charset=UTF-8");
        response.setHeader("Content-Disposition",
                "attachment; filename=\"touch_reports_" + System.currentTimeMillis() + ".csv\"");

        // 最多 10000 行保护
        List<TouchReportVO> list = touchReportService.listAllForExport(keyword, status, orgId, 10000);
        log.info("[TouchReportController.export] 导出记录数={}", list.size());

        try (PrintWriter out = response.getWriter()) {
            // 写出 CSV 表头
            out.println("任务编号,任务类型,任务状态,SLA状态,客户名称,执行人工号,机构代码,计划完成时间,成功完成时间,日志条数");
            // 逐行写出数据（流式，不积累内存）
            for (TouchReportVO r : list) {
                out.println(String.join(",",
                        quote(r.getTaskNo()),
                        quote(r.getTaskType()),
                        quote(r.getTaskStatus()),
                        quote(r.getSlaStatus()),
                        quote(r.getCustName()),
                        quote(r.getAssigneeEmpId()),
                        quote(r.getOrgId()),
                        quote(r.getPlanFinishTime() != null ? r.getPlanFinishTime().toString() : ""),
                        quote(r.getSuccessTime() != null ? r.getSuccessTime().toString() : ""),
                        quote(r.getLogCount() != null ? r.getLogCount().toString() : "0")));
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
        return "\"" + s.replace("\"", "\"\"") + "\"";
    }
}
