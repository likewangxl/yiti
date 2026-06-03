package com.bank.branch.platform.performance.controller.dto;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * 「我的待审批 - 业绩调整」单条响应 DTO（扁平结构）。
 * <p>业务字段来自 PERF_ALLOC_ADJUST_APPLY，workflow 字段来自 TaskRespDTO，
 * merge 到一层兼容前端 todo tab 现有列模板 (row.title / row.startUserName / row.taskCreateTime ...)。</p>
 */
@Data
public class AdjustTodoRespDTO {

    // ===== 业务字段（PERF_ALLOC_ADJUST_APPLY） =====
    private String id;
    private String applyNo;
    private String custId;
    /** 客户编号（PERF_ALLOC_ADJUST_APPLY.cust_no），前端「客户」列主显字段（cust_id 现网恒为 NULL） */
    private String custNo;
    /** 客户名称（PERF_ALLOC_ADJUST_APPLY.cust_name），前端「客户」列副显字段 */
    private String custName;
    private String custType;
    private String allocDim;
    private String bizKind;
    private String ownerOrgId;
    private String createdBy;
    private LocalDateTime createdTime;
    private String businessKey;
    private String status;

    // ===== workflow 字段（来自 TaskRespDTO） =====
    private String taskId;
    private String nodeKey;
    private String taskName;
    private String title;
    private Boolean claimable;
    private String startUser;
    private String startUserName;
    private String startOrgId;
    private String startOrgName;
    private LocalDateTime startTime;
    private LocalDateTime taskCreateTime;
    private String slaStatus;
}
