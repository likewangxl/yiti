package com.bank.branch.platform.performance.controller.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;

/**
 * 分配关系调整申请响应 DTO (V1.2 Q2.4).
 *
 * <p>由 Controller/Facade 层将 Entity 装配成 DTO 后返回，避免直接暴露 Entity.
 */
@Data
public class AllocAdjustRespDTO {

    /** 申请 ID. */
    private String id;

    /** 申请编号. */
    private String applyNo;

    /** 客户编号（PERF_ALLOC_ADJUST_APPLY.cust_id，即用户输入的客户编号）. */
    private String custId;

    /** 客户名称（提交时快照；历史行回退反查，查不到为 null）. */
    private String custName;

    /** 当前余额（提交时快照）. */
    private BigDecimal currBal;

    /** 月均余额（提交时快照）. */
    @JsonProperty("mAvgBal")
    private BigDecimal mAvgBal;

    /** 季日均余额（提交时快照）. */
    @JsonProperty("qAvgBal")
    private BigDecimal qAvgBal;

    /** 年日均余额（提交时快照）. */
    @JsonProperty("yAvgBal")
    private BigDecimal yAvgBal;

    /** 贷款-当前余额（提交时快照，MC_005）. */
    @JsonProperty("loanCurrBal")
    private BigDecimal loanCurrBal;

    /** 贷款-较上日余额（提交时快照，MC_006）. */
    @JsonProperty("loanMAvgBal")
    private BigDecimal loanMAvgBal;

    /** 贷款-年均余额（提交时快照，MC_007）. */
    @JsonProperty("loanQAvgBal")
    private BigDecimal loanQAvgBal;

    /** 贷款-较上年均余额（提交时快照，MC_008）. */
    @JsonProperty("loanYAvgBal")
    private BigDecimal loanYAvgBal;

    /** 客户类型：CORP / RETAIL. */
    private String custType;

    /** 分配维度. */
    private String allocDim;

    /** 业务种类. */
    private String bizKind;

    /** 账号（可空）. */
    private String accountNo;

    /** 状态 DRAFT/IN_APPROVAL/APPROVED/REJECTED. */
    private String status;

    /** 流程业务键. */
    private String businessKey;

    /** 流程实例 ID. */
    private String processInstanceId;

    /** 归属机构. */
    private String ownerOrgId;

    /** 备注 / 申请原因. */
    private String remark;

    /** 申请人 empId. */
    private String createdBy;

    /** 申请人姓名（按 createdBy 反查 PT_USER）；用户已删时为 null. */
    private String createdByName;

    /** 申请人工号（PT_USER.username，展示用）；查不到为 null. */
    private String createdByUsername;

    /** 申请人主机构名称（按 createdBy 反查 EXT_USER_ORG + EXT_ORG_INFO）；查不到为 null. */
    private String createdByOrgName;

    /** 申请时间. */
    private LocalDateTime createdTime;

    /** 最近更新人. */
    private String updatedBy;

    /** 最近更新时间. */
    private LocalDateTime updatedTime;

    /**
     * 当前未审核活动节点可审批员工；节点已审核、流程终态或无活动任务时为空。
     */
    private List<CurrentNodeApprover> currentNodeApprovers = Collections.emptyList();

    /** 调整后明细（仅 getById 返回，list 视图为空列表以减压）. */
    private List<Item> items;

    /** 当前节点可审批员工展示项。 */
    @Data
    public static class CurrentNodeApprover {
        /** 员工工号（PT_USER.USERNAME）。 */
        private String employeeNo;
        /** 员工姓名。 */
        private String employeeName;
    }

    /**
     * 明细项.
     */
    @Data
    public static class Item {

        /** 明细 ID. */
        private String id;

        /** 明细类型：NEW=新分配 / ORIGIN=原业绩分配. */
        private String itemKind;

        /** 账号（原业绩分配选填）. */
        private String acctNo;

        /** 员工工号. */
        private String empId;

        /** 员工登录名（PT_USER.USERNAME；解析不到时回退为工号）. */
        private String username;

        /** 员工中文姓名（PT_USER.USERCHNNAME）. */
        private String empChnName;

        /** 所属部门号（快照）. */
        private String orgCode;

        /** 所属部门名称（快照）. */
        private String orgName;

        /** 分配比例. */
        private BigDecimal ratio;

        /** 说明. */
        private String remark;

        /** 创建时间. */
        private LocalDateTime createdTime;
    }
}
