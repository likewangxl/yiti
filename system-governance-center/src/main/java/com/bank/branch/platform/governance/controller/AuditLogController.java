package com.bank.branch.platform.governance.controller;

import com.bank.branch.platform.common.security.annotation.BizAuth;
import com.bank.branch.platform.common.security.enums.BizAction;
import com.bank.branch.platform.common.security.enums.BizType;
import com.bank.branch.platform.common.web.PageResult;
import com.bank.branch.platform.common.web.ResponseWrapper;
import com.bank.branch.platform.governance.api.dto.AuditLogDTO;
import com.bank.branch.platform.governance.api.dto.AuditLogQueryReqDTO;
import com.bank.branch.platform.governance.service.AuditLogService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 审计日志控制器
 * 提供审计日志的分页查询接口
 */
@Slf4j
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/admin/sys/audit-logs")
@Tag(name = "审计日志", description = "操作审计日志查询")
public class AuditLogController {

    private final AuditLogService auditLogService;

    /**
     * 分页查询审计日志
     *
     * @param empId     操作人工号，可为 null
     * @param bizType   业务类型，可为 null
     * @param bizAction 业务动作，可为 null
     * @param startTime 开始时间（yyyy-MM-dd），可为 null
     * @param endTime   结束时间（yyyy-MM-dd），可为 null
     * @param keyword   模糊搜索关键词，可为 null
     * @param pageNo    页码，默认 1
     * @param pageSize  每页条数，默认 20
     * @return 分页结果
     */
    @GetMapping
    @Operation(summary = "分页查询审计日志")
    @BizAuth(bizType = BizType.SYS_CONFIG, action = BizAction.READ)
    public ResponseWrapper<AuditLogDTO> queryLogs(
            @RequestParam(value = "empId", required = false) String empId,
            @RequestParam(value = "bizType", required = false) String bizType,
            @RequestParam(value = "bizAction", required = false) String bizAction,
            @RequestParam(value = "startTime", required = false) String startTime,
            @RequestParam(value = "endTime", required = false) String endTime,
            @RequestParam(value = "keyword", required = false) String keyword,
            @RequestParam(value = "pageNo", defaultValue = "1") int pageNo,
            @RequestParam(value = "pageSize", defaultValue = "20") int pageSize) {
        log.debug("[AuditLogController.queryLogs] empId={}, bizType={}, pageNo={}, pageSize={}",
                empId, bizType, pageNo, pageSize);

        AuditLogQueryReqDTO query = new AuditLogQueryReqDTO();
        query.setEmpId(empId);
        query.setBizType(bizType);
        query.setBizAction(bizAction);
        query.setStartTime(startTime);
        query.setEndTime(endTime);
        query.setKeyword(keyword);

        PageResult<AuditLogDTO> result = auditLogService.queryLogs(query, pageNo, pageSize);
        return ResponseWrapper.page(result);
    }

    /**
     * 查询单条审计日志详情（D.2）。
     *
     * @param id 审计日志ID
     * @return 审计日志详情
     */
    @GetMapping("/{id}")
    @Operation(summary = "查询审计日志详情")
    @BizAuth(bizType = BizType.SYS_CONFIG, action = BizAction.READ)
    public ResponseWrapper<AuditLogDTO> getById(@PathVariable("id") String id) {
        log.debug("[AuditLogController.getById] id={}", id);
        AuditLogDTO dto = auditLogService.getById(id);
        return ResponseWrapper.success(dto);
    }

    /**
     * 导出审计日志为Excel文件（D.3）。
     *
     * @param empId     操作人工号，可为 null
     * @param bizType   业务类型，可为 null
     * @param bizAction 业务动作，可为 null
     * @param startTime 开始时间（yyyy-MM-dd），可为 null
     * @param endTime   结束时间（yyyy-MM-dd），可为 null
     * @param keyword   模糊搜索关键词，可为 null
     * @param response  HTTP响应
     */
    @PostMapping("/export")
    @Operation(summary = "导出审计日志")
    @BizAuth(bizType = BizType.SYS_CONFIG, action = BizAction.EXPORT)
    public void exportLogs(
            @RequestParam(value = "empId", required = false) String empId,
            @RequestParam(value = "bizType", required = false) String bizType,
            @RequestParam(value = "bizAction", required = false) String bizAction,
            @RequestParam(value = "startTime", required = false) String startTime,
            @RequestParam(value = "endTime", required = false) String endTime,
            @RequestParam(value = "keyword", required = false) String keyword,
            HttpServletResponse response) {
        log.info("[AuditLogController.exportLogs] empId={}, bizType={}", empId, bizType);

        AuditLogQueryReqDTO query = new AuditLogQueryReqDTO();
        query.setEmpId(empId);
        query.setBizType(bizType);
        query.setBizAction(bizAction);
        query.setStartTime(startTime);
        query.setEndTime(endTime);
        query.setKeyword(keyword);

        auditLogService.exportLogs(query, response);
    }
}
