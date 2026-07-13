package com.bank.branch.platform.workflow.controller;

import com.bank.branch.platform.common.security.annotation.BizAuth;
import com.bank.branch.platform.common.security.enums.BizAction;
import com.bank.branch.platform.common.security.enums.BizType;
import com.bank.branch.platform.common.web.ResponseWrapper;
import com.bank.branch.platform.workflow.api.dto.ProcessMonitorItemDTO;
import com.bank.branch.platform.workflow.service.ProcessMonitorService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 审批流监控控制器
 * <p>
 * 供秘书岗/行长按机构数据范围监控进行中与已完成的审批流实例列表。
 * 数据范围由 {@code ProcessMonitorService} 内部基于 {@code DataScopeContext} 过滤（Fail-Close）。
 * </p>
 */
@Slf4j
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/workflow/monitor")
@Tag(name = "审批流监控", description = "秘书岗/行长按机构监控进行中与已完成审批流")
public class ProcessMonitorController {

    private final ProcessMonitorService processMonitorService;

    /**
     * 分页查询审批流监控列表。
     *
     * @param status    流程状态（可空，精确匹配）
     * @param bizType   业务类型（可空，精确匹配）
     * @param keyword   标题关键字（可空，模糊匹配）
     * @param startedBy 发起人工号（可空，精确匹配）
     * @param pageNo    页码，默认1
     * @param pageSize  每页大小，默认20
     * @return 分页结果
     */
    @GetMapping("/processes")
    @Operation(summary = "审批流监控列表")
    @BizAuth(bizType = BizType.WORKFLOW_MONITOR, action = BizAction.LIST)
    public ResponseWrapper<ProcessMonitorItemDTO> list(
            @RequestParam(value = "status", required = false) String status,
            @RequestParam(value = "bizType", required = false) String bizType,
            @RequestParam(value = "keyword", required = false) String keyword,
            @RequestParam(value = "startedBy", required = false) String startedBy,
            @RequestParam(value = "pageNo", defaultValue = "1") int pageNo,
            @RequestParam(value = "pageSize", defaultValue = "20") int pageSize) {
        log.debug("[ProcessMonitorController.list] status={}, bizType={}", status, bizType);
        return ResponseWrapper.page(processMonitorService.query(status, bizType, keyword, startedBy, pageNo, pageSize));
    }
}
