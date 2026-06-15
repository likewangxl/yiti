package com.bank.branch.platform.report.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

/**
 * 业绩调整审批表 AMAS_PERF_ADJUST_APPROVAL 贫血实体（只读报表用）.
 *
 * <p>外部 AMAS 系统写入、本平台报表分析中心只读消费的"外来表"。
 * 以 {@code PERF_ADJUST_NO}（业绩调整编号）为逻辑主键（DDL 无物理主键，标 INPUT 仅供
 * MyBatis-Plus selectById/条件查询使用）。时间字段在 DDL 中为 varchar(50)，
 * 按字符串倒序即时间倒序（要求来源时间为 yyyy-MM-dd HH:mm:ss 定长格式）。</p>
 */
@Data
@TableName("AMAS_PERF_ADJUST_APPROVAL")
public class AmasPerfAdjustApproval {

    /** 业绩调整编号（逻辑主键）. */
    @TableId(value = "PERF_ADJUST_NO", type = IdType.INPUT)
    private String perfAdjustNo;

    /** 申请人工号. */
    private String applyUsername;

    /** 申请人姓名. */
    private String applyFullname;

    /** 审批类型：1,公司业绩调整；2,零售业绩调整. */
    private String applyType;

    /** 申请时间（varchar，yyyy-MM-dd HH:mm:ss）. */
    private String applyTime;

    /** 客户号. */
    private String custId;

    /** 客户名称. */
    private String custName;

    /** 账号/借据号. */
    private String iouNo;

    /** 业务类型（多选逗号拼接：1,存款 2,贷款 3,中收 4,结构性...）. */
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
