package com.bank.branch.platform.customer.entity.marketing;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.annotation.Version;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 营销客户唯一主档。
 *
 * <p>统一社会信用代码是一企一档的业务唯一键，id 采用数据库自增 Long 主键。
 * 该实体只映射营销域新表，不复用旧 CUSTOMER_MARKET_CUSTOMER 实体，避免新旧表误写。</p>
 */
@Data
@TableName("MARKETING_CUSTOMER_INFO")
public class MarketingCustomerInfo {

    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    private String custNo;
    private String custName;
    private String unifiedCreditCode;
    private String legalRepresentative;
    private BigDecimal registeredCapital;
    private String registeredAddress;
    private String businessAddress;
    private String businessScope;
    private String contactPerson;
    private String contactMobile;
    private String industry;
    private String groupType;
    private String groupName;
    private String customerType;
    private String enterpriseType;
    private Integer isKeystone;
    private String customerDesc;
    private Integer isAccountOpened;
    private BigDecimal creditAmount;
    private BigDecimal creditExposureAmount;
    private Integer touchRestricted;
    private Long currentLeadId;
    private LocalDateTime lastTouchTime;

    private String mainManagerId;
    private String mainOrgId;
    private String ownershipStatus;
    private String ownershipSource;
    private String ownershipMaintainMode;
    private LocalDate ownershipDataDate;
    private String ownershipUpdatedBy;
    private LocalDateTime ownershipUpdatedTime;
    private String ownershipManualReason;

    private Integer profileVersion;
    private String accountOpenedByEmpId;
    private LocalDateTime accountOpenedTime;
    private Long openingTouchTaskId;

    private String recordStatus;
    private String createdBy;
    private LocalDateTime createdTime;
    private String updatedBy;
    private LocalDateTime updatedTime;

    @Version
    private Integer lockVersion;
}
