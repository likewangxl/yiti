package com.bank.branch.platform.customer.entity.marketing;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.annotation.Version;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 营销客户认领关系，对应 {@code MARKETING_CUSTOMER_CLAIM}。
 *
 * <p>认领的业务幂等维度是 sourceLeadId + claimedBy，而不是客户主档 ID + 员工。
 * 这样同一客户在不同营销线索中可以形成独立的认领事实。</p>
 */
@Data
@TableName("MARKETING_CUSTOMER_CLAIM")
public class MarketingCustomerClaim {

    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    private Long custId;
    private Long sourceLeadId;
    private String orgId;
    private String claimedBy;
    private String maintainerEmpId;
    private String claimStatus;
    private LocalDateTime claimTime;
    private LocalDateTime cancelTime;
    private String cancelReason;
    private String createdBy;
    private LocalDateTime createdTime;
    private String updatedBy;
    private LocalDateTime updatedTime;

    @Version
    private Integer lockVersion;
}
