package com.bank.branch.platform.customer.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 客户认领关系实体，对应 cust_claim 表。
 * <p>
 * 记录机构与客户的认领关系，支持多机构认领同一客户（多对多）。
 * 唯一索引 uk_cust_org(cust_id, org_id) 防止同一机构重复认领。
 * claim_status 枚举：CLAIMED-已认领/CANCELLED-已取消。
 * 注意：该表无 created_by/updated_by 字段，也无 deleted 字段。
 * </p>
 */
@Data
@TableName("CUST_CLAIM")
public class CustClaim {

    /** 主键ID（UUID，32位去连字符），对应 id */
    @TableId(value = "id", type = IdType.INPUT)
    private String id;

    /** 客户ID（关联 cust_master.id），对应 cust_id */
    private String custId;

    /** 认领机构代码，对应 org_id */
    private String orgId;

    /** 认领人（员工工号），对应 claimed_by */
    private String claimedBy;

    /** 维护人（员工工号，可转交），对应 maintainer_emp_id */
    private String maintainerEmpId;

    /** 认领状态：CLAIMED-已认领/CANCELLED-已取消，对应 claim_status */
    private String claimStatus;

    /** 认领时间，对应 claim_time */
    private LocalDateTime claimTime;

    /** 取消时间，对应 cancel_time */
    private LocalDateTime cancelTime;

    /** 取消原因，对应 cancel_reason */
    private String cancelReason;

    /** 创建时间，对应 created_time */
    private LocalDateTime createdTime;

    /** 最后更新时间，对应 updated_time */
    private LocalDateTime updatedTime;
}
