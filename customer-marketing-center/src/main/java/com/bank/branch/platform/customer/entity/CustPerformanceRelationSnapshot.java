package com.bank.branch.platform.customer.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

/** 客户业绩归属快照，只用于在线条件校验。 */
@Data
@TableName("CUST_PERFORMANCE_RELATION_SNAPSHOT")
public class CustPerformanceRelationSnapshot {
    @TableId(value = "id", type = IdType.INPUT)
    private String id;
    private String custId;
    private String subjectType;
    private String subjectId;
    private String relatedEmpId;
    private String relatedOrgId;
    private BigDecimal ratio;
    private LocalDate effectiveDate;
    private LocalDate expiryDate;
    private String sourceSystem;
    private String sourceBatchId;
    private LocalDateTime refreshedAt;
    private String refreshStatus;
    private LocalDateTime createdTime;
}
