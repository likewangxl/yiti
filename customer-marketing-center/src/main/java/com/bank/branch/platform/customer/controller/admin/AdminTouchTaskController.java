package com.bank.branch.platform.customer.controller.admin;

import com.bank.branch.platform.common.aop.annotation.AuditLog;
import com.bank.branch.platform.common.security.annotation.BizAuth;
import com.bank.branch.platform.common.security.enums.BizAction;
import com.bank.branch.platform.common.security.enums.BizType;
import com.bank.branch.platform.common.web.PageResult;
import com.bank.branch.platform.common.web.ResponseWrapper;
import com.bank.branch.platform.customer.api.TouchTaskQueryApi;
import com.bank.branch.platform.customer.api.dto.TouchTaskSummaryDTO;
import com.bank.branch.platform.customer.dto.req.AdminBatchAssignReqDTO;
import com.bank.branch.platform.customer.entity.TouchTask;
import com.bank.branch.platform.customer.service.TouchTaskService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.io.IOException;
import java.io.PrintWriter;
import java.util.List;

/**
 * 管理后台触达任务控制器。
 * <p>
 * 提供跨机构的全局触达任务查询、导出和批量分配能力，
 * 仅限管理员操作，所有高危操作需要独立审计日志。
 * </p>
 */
@Slf4j
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/admin/touch-tasks")
@Validated
@Tag(name = "管理后台 - 触达任务")
public class AdminTouchTaskController {

    private final TouchTaskService touchTaskService;
    private final TouchTaskQueryApi touchTaskQueryApi;

    /**
     * 管理后台全局触达任务列表（不按机构过滤）。
     * <p>
     * 支持关键词（模糊搜索 task_no）、状态、执行人工号、机构 ID 过滤，
     * 分页返回结果，默认 pageSize=20，最大 100。
     * </p>
     *
     * @param keyword       关键词（搜索 task_no），可为 null
     * @param status        任务状态过滤，可为 null
     * @param assigneeEmpId 执行人工号过滤，可为 null
     * @param orgId         机构 ID 过滤，可为 null
     * @param pageNo        页码，默认 1
     * @param pageSize      每页大小，默认 20
     * @return 分页触达任务列表
     */
    @GetMapping
    @BizAuth(bizType = BizType.TOUCH_TASK, action = BizAction.LIST)
    @AuditLog(action = "LIST_ADMIN_TOUCH_TASKS", resourceType = "TOUCH_TASK")
    @Operation(summary = "管理后台全局触达任务列表")
    public ResponseWrapper<TouchTask> listAll(
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String assigneeEmpId,
            @RequestParam(required = false) String orgId,
            @RequestParam(defaultValue = "1") int pageNo,
            @RequestParam(defaultValue = "20") int pageSize) {
        log.info("[AdminTouchTaskController.listAll] keyword={}, status={}, assigneeEmpId={}, orgId={}, pageNo={}, pageSize={}",
                keyword, status, assigneeEmpId, orgId, pageNo, pageSize);
        PageResult<TouchTask> result = touchTaskService.listPageAdmin(keyword, status, assigneeEmpId, orgId, pageNo, pageSize);
        return ResponseWrapper.page(result);
    }

    /**
     * 管理后台机构触达汇总统计。
     * <p>
     * 按机构维度返回触达任务在指定时间范围内的状态计数（PENDING/IN_PROGRESS/SUCCESS/CANCELLED）、
     * SLA 预警计数和平均完成时长。startDate/endDate 缺省时由 {@link TouchTaskQueryApi#getOrgTouchSummary}
     * 自行决定默认范围（通常近 30 天）。
     * </p>
     *
     * @param orgCode   机构代码（必填）
     * @param startDate 开始日期 yyyy-MM-dd（可选）
     * @param endDate   结束日期 yyyy-MM-dd（可选）
     * @return 机构触达汇总 DTO
     */
    @GetMapping("/summary")
    @BizAuth(bizType = BizType.TOUCH_TASK, action = BizAction.LIST)
    @Operation(summary = "管理后台机构触达汇总")
    public ResponseWrapper<TouchTaskSummaryDTO> summary(
            @RequestParam String orgCode,
            @RequestParam(required = false) String startDate,
            @RequestParam(required = false) String endDate) {
        log.info("[AdminTouchTaskController.summary] orgCode={}, startDate={}, endDate={}", orgCode, startDate, endDate);
        TouchTaskSummaryDTO summary = touchTaskQueryApi.getOrgTouchSummary(orgCode, startDate, endDate);
        return ResponseWrapper.success(summary);
    }

    /**
     * 管理后台触达任务导出（高危操作）。
     * <p>
     * 导出 CSV 格式，最多导出 10000 条记录，需要审计日志记录。
     * Content-Type: text/csv; charset=UTF-8。
     * </p>
     *
     * @param keyword  关键词过滤，可为 null
     * @param status   任务状态过滤，可为 null
     * @param orgId    机构 ID 过滤，可为 null
     * @param response HTTP 响应对象
     * @throws IOException 写入响应流异常
     */
    @GetMapping("/export")
    @BizAuth(bizType = BizType.TOUCH_TASK, action = BizAction.READ)
    @AuditLog(action = "EXPORT_ADMIN_TOUCH_TASKS", resourceType = "TOUCH_TASK", reasonRequired = true)
    @Operation(summary = "管理后台触达任务导出（高危）")
    public void exportAll(
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String orgId,
            HttpServletResponse response) throws IOException {
        log.info("[AdminTouchTaskController.exportAll] keyword={}, status={}, orgId={}", keyword, status, orgId);

        response.setContentType("text/csv; charset=UTF-8");
        response.setHeader("Content-Disposition",
                "attachment; filename=\"admin_touch_tasks_" + System.currentTimeMillis() + ".csv\"");

        List<TouchTask> list = touchTaskService.listAllForAdminExport(keyword, status, orgId, 10000);

        try (PrintWriter out = response.getWriter()) {
            // BOM 头保证 Excel 打开 UTF-8 中文不乱码
            out.write('\uFEFF');
            out.println("任务编号,客户ID,机构ID,执行人,任务类型,状态,创建时间");
            for (TouchTask t : list) {
                out.println(
                        quote(t.getTaskNo()) + "," +
                        quote(t.getCustId()) + "," +
                        quote(t.getOrgId()) + "," +
                        quote(t.getAssigneeEmpId()) + "," +
                        quote(t.getTaskType()) + "," +
                        quote(t.getTaskStatus()) + "," +
                        quote(t.getCreatedTime() == null ? "" : t.getCreatedTime().toString())
                );
            }
        }

        log.info("[AdminTouchTaskController.exportAll] exported {} records", list.size());
    }

    /**
     * 管理后台批量分配触达任务（高危操作）。
     * <p>
     * 仅允许对 PENDING 或 IN_PROGRESS 状态的任务进行重分配，
     * 终态（SUCCESS/CANCELLED）任务会被自动跳过，不会报错。
     * 返回实际更新的任务数量。
     * </p>
     *
     * @param req 批量分配请求 DTO（taskIds + newAssigneeEmpId）
     * @return 实际更新的任务数量
     */
    @PostMapping("/batch-assign")
    @BizAuth(bizType = BizType.TOUCH_TASK, action = BizAction.WRITE)
    @AuditLog(action = "BATCH_ASSIGN_TOUCH_TASKS", resourceType = "TOUCH_TASK", reasonRequired = true)
    @Operation(summary = "管理后台批量分配触达任务（高危）")
    public ResponseWrapper<Integer> batchAssign(@Valid @RequestBody AdminBatchAssignReqDTO req) {
        log.info("[AdminTouchTaskController.batchAssign] taskCount={}, newAssigneeEmpId={}",
                req.getTaskIds().size(), req.getNewAssigneeEmpId());
        int updated = touchTaskService.batchAssign(req.getTaskIds(), req.getNewAssigneeEmpId());
        log.info("[AdminTouchTaskController.batchAssign] updated={}", updated);
        return ResponseWrapper.success(updated);
    }

    /**
     * CSV 字段安全引用（防止字段内含逗号或引号导致格式错乱）。
     *
     * @param s 原始字段值
     * @return 用双引号包裹并转义内部双引号的字符串
     */
    private static String quote(String s) {
        if (s == null) return "\"\"";
        return "\"" + s.replace("\"", "\"\"") + "\"";
    }
}
