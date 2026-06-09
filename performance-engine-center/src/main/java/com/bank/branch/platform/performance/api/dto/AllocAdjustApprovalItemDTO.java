package com.bank.branch.platform.performance.api.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * 「分配关系调整」审批列表单条 DTO（对外渠道视角，已补齐姓名/客户名）。
 * <p>由 {@link com.bank.branch.platform.performance.api.PerfApprovalQueryApi} 返回，
 * 数据合并自项目内「我的待审批」+「本人已审批」两个列表（权限逻辑完全一致），
 * 供手机端等外部渠道按员工号拉取。</p>
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AllocAdjustApprovalItemDTO {

    /** 业绩调整审批编号（= 申请主键 id），手机端 perfAdjustNo */
    private String perfAdjustNo;

    /** 申请编号（业务可读编号 apply_no，备用） */
    private String applyNo;

    /** 客户ID */
    private String custId;

    /** 客户名称（CustomerQueryApi 补齐，查不到为 null） */
    private String custName;

    /** 申请人员工号（created_by） */
    private String createdBy;

    /** 申请人姓名（通讯录 AddressBookApi 补齐，查不到为 null） */
    private String applyFullname;

    /** 申请时间（created_time） */
    private LocalDateTime applyTime;

    /** 申请单状态：DRAFT/IN_APPROVAL/APPROVED/REJECTED */
    private String status;

    /** 渠道分类：TODO=待本人审批，DONE=本人已审批/已驳回 */
    private String category;
}
