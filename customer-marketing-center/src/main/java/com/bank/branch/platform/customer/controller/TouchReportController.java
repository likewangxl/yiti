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
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

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
     * 导出触达报告（高危操作）。
     * <p>
     * 触达报告导出操作需审计，由 @AuditLog 切面记录操作日志。
     * TODO: 当前为简化实现，返回 success 占位；后续集成 EasyExcel 实现真实导出逻辑。
     * 完整实现需：1. 构建导出数据列表；2. 设置 HttpServletResponse Content-Disposition；
     * 3. 通过 EasyExcel.write() 写出到 OutputStream。
     * </p>
     *
     * @return 操作结果（简化占位，正式实现时改为流式下载）
     */
    @GetMapping("/export")
    @BizAuth(bizType = BizType.TOUCH_REPORT, action = BizAction.EXPORT)
    @AuditLog(action = "EXPORT", resourceType = "TOUCH_REPORT")
    @Operation(summary = "导出触达报告")
    public ResponseWrapper<Void> export() {
        log.info("[TouchReportController.export] export touch reports");
        // TODO: 集成 EasyExcel 实现真实的流式导出
        // 1. 调用 touchReportService.listPage 获取全量数据（分批分页）
        // 2. 设置 response.setContentType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet")
        // 3. 设置 response.setHeader("Content-Disposition", "attachment;filename=touch-reports.xlsx")
        // 4. EasyExcel.write(response.getOutputStream(), TouchReportVO.class).sheet("触达报告").doWrite(records)
        return ResponseWrapper.success();
    }
}
