package com.bank.branch.platform.customer.entity.marketing;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 营销客户业绩关系只读快照，对应
 * {@code MARKETING_CUSTOMER_PERFORMANCE_REL_SNAPSHOT}。
 *
 * <p>它只用于跨机构营销条件校验，不直接授予客户查询、触达或资产立项权限。</p>
 */
@Data
@TableName("MARKETING_CUSTOMER_PERFORMANCE_REL_SNAPSHOT")
public class MarketingCustomerPerformanceRelSnapshot {

    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    private Long custId;
    private String subjectType;
    private String subjectId;
    private String relatedEmpId;
    private String relatedOrgId;
    private String relationType;
    private BigDecimal ratio;
    private LocalDate effectiveDate;
    private LocalDate expiryDate;
    private String sourceSystem;
    private String sourceBatchId;
    private LocalDateTime refreshedAt;
    private String refreshStatus;
    private LocalDateTime createdTime;
}
