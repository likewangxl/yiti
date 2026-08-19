package com.bank.branch.platform.performance.api.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.List;

/** 分配关系调整 单据详情（手机端详情页用：含分配明细 + 能力标志）。 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AllocAdjustDetailDTO {
    private String perfAdjustNo;
    private String applyNo;
    private String custId;
    private String custName;
    /** CORP / RETAIL */
    private String custType;
    /** ACCOUNT / RULE */
    private String allocDim;
    private String bizKind;
    private String accountNo;
    /** DRAFT/IN_APPROVAL/APPROVED/REJECTED/WITHDRAWN */
    private String status;
    /** 调整理由（apply.remark） */
    private String reason;
    private String createdBy;
    private String applyFullname;
    private LocalDateTime applyTime;
    /** 申请人本人且可撤回（status∈IN_APPROVAL/DRAFT）。 */
    private boolean canDelete;
    /** 当前用户的待办且 IN_APPROVAL。 */
    private boolean canApprove;
    /**
     * 当前所处审批节点中文名（IN_APPROVAL 取 Flowable 活动 userTask 名；
     * 终态显示「已完成/已拒绝/已撤回/草稿」）。
     */
    private String currentNode;
    /**
     * 当前活动节点 KEY（taskDefinitionKey，如 {@code biz_dept_review}/{@code finance_review}）。
     * 供经办审批端按节点决定「下一步审批」表单（终态 / 无活动节点时为 null）。
     */
    private String currentNodeKey;
    /**
     * 下一审批节点中文名（按 callPu 硬编码路由的确定链路静态推算；
     * 末节点显示「流程结束」，终态显示「无」）。
     */
    private String nextNode;
    /** 当前未审核活动节点可审批员工；已审核节点/终态为空。 */
    @Builder.Default
    private List<CurrentNodeApprover> currentNodeApprovers = List.of();
    private List<AllocItem> allocaters;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class CurrentNodeApprover {
        /** 员工工号（PT_USER.USERNAME）。 */
        private String employeeNo;
        /** 员工姓名。 */
        private String employeeName;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class AllocItem {
        private String empId;
        private String username;
        private String fullname;
        private String ratio;
        /** 1=原分配(itemKind=ORIGIN) / 2=调整后(其它)。 */
        private Integer isOriginal;
    }
}
