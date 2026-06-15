package com.bank.branch.platform.report.dto.resp;

import lombok.Data;

/**
 * 业绩分配审批 列表行 / 详情主信息 视图（字段名与实体一致，便于 BeanUtils 拷贝）.
 */
@Data
public class AmasApprovalRowVO {

    /** 业绩调整编号. */
    private String perfAdjustNo;
    /** 申请人工号. */
    private String applyUsername;
    /** 申请人姓名. */
    private String applyFullname;
    /** 审批类型：1,公司业绩调整；2,零售业绩调整. */
    private String applyType;
    /** 申请时间. */
    private String applyTime;
    /** 客户号. */
    private String custId;
    /** 客户名称. */
    private String custName;
    /** 账号/借据号. */
    private String iouNo;
    /** 业务类型. */
    private String businessType;
    /** 调整理由. */
    private String adjustExplain;
    /** 账户余额. */
    private String acctBalance;
    /** 上月月均. */
    private String avgLastMonth;
    /** 年日均. */
    private String avgYear;
    /** 调整类型：1,账号调整；2,规则调整. */
    private String applyRule;
    /** 零售账户余额. */
    private String acctBalanceLs;
    /** 零售上月月均. */
    private String avgLastMonthLs;
    /** 零售年日均. */
    private String avgYearLs;
    /** 审批进度. */
    private String apprProgress;
    /** 审批状态：0,待审批；1,已通过；2,已拒绝. */
    private String apprStatus;
    /** 当前审批步骤. */
    private String currApprSeq;
    /** 当前审批人账号. */
    private String currApprUsername;
    /** 当前审批角色. */
    private String currApprRole;
}
