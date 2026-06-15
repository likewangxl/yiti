package com.bank.branch.platform.report.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 业绩分配调整申请 PERF_ALLOC_ADJUST_APPLY 贫血实体（只读报表用）.
 *
 * <p>平台自有的业绩调整申请（走 Flowable 审批），区别于外部 AMAS 遗留表。
 * 报表分析中心只读消费，列表按申请时间倒序展示。</p>
 */
@Data
@TableName("PERF_ALLOC_ADJUST_APPLY")
public class PerfAllocAdjustApply {

    /** 申请ID（主键）. */
    @TableId(value = "id", type = IdType.INPUT)
    private String id;

    /** 申请编号. */
    private String applyNo;

    /** 客户号. */
    private String custId;

    /** 客户名称. */
    private String custName;

    /** 客户类型：CORP 公司 / RETAIL 零售. */
    private String custType;

    /** 分配/调整方式：RULE 规则 / ACCOUNT 账号. */
    private String allocDim;

    /** 业务种类. */
    private String bizKind;

    /** 账号. */
    private String accountNo;

    /** 状态：DRAFT/IN_APPROVAL/APPROVED/REJECTED/WITHDRAWN. */
    private String status;

    /** 归属机构编码（数据范围按机构过滤用）. */
    private String ownerOrgId;

    /** 业务键（Flowable businessKey）. */
    private String businessKey;

    /** 备注. */
    private String remark;

    /** 申请人工号. */
    private String createdBy;

    /** 申请时间. */
    private LocalDateTime createdTime;
}
