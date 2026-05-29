package com.bank.branch.platform.performance.controller.dto;

import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;
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

    /** 客户 ID（cust_master 内部主键）. */
    private String custId;

    /** 客户编号（cust_master.cust_no，按 custId 反查回填；客户已删/查不到时为 null）. */
    private String custNo;

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

    /** 申请人主机构名称（按 createdBy 反查 EXT_USER_ORG + EXT_ORG_INFO）；查不到为 null. */
    private String createdByOrgName;

    /** 申请时间. */
    private LocalDateTime createdTime;

    /** 最近更新人. */
    private String updatedBy;

    /** 最近更新时间. */
    private LocalDateTime updatedTime;

    /** 调整后明细（仅 getById 返回，list 视图为空列表以减压）. */
    private List<Item> items;

    /**
     * 明细项.
     */
    @Data
    public static class Item {

        /** 明细 ID. */
        private String id;

        /** 员工工号. */
        private String empId;

        /** 员工登录名（PT_USER.USERNAME；解析不到时回退为工号）. */
        private String username;

        /** 员工中文姓名（PT_USER.USERCHNNAME）. */
        private String empChnName;

        /** 分配比例. */
        private BigDecimal ratio;

        /** 说明. */
        private String remark;

        /** 创建时间. */
        private LocalDateTime createdTime;
    }
}
