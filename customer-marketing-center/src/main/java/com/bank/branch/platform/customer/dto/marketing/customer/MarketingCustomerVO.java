package com.bank.branch.platform.customer.dto.marketing.customer;

import com.bank.branch.platform.customer.entity.marketing.MarketingCustomerInfo;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

/** 营销客户页面输出，不直接暴露 MyBatis 实体。 */
@Data
public class MarketingCustomerVO {

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
    /** 当前有效营销标签；列表与详情均由 Service 批量回填。 */
    private List<Long> tagIds;
    private List<String> tagNames;
    private Long currentLeadId;
    private LocalDateTime lastTouchTime;
    private String mainManagerId;
    private String mainManagerName;
    private String mainOrgId;
    private String mainOrgName;
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
    private LocalDateTime createdTime;
    private String updatedBy;
    private LocalDateTime updatedTime;
    private Integer lockVersion;

    /** 从实体转换为页面 DTO；员工/机构名称由 Service 批量回填。 */
    public static MarketingCustomerVO fromEntity(MarketingCustomerInfo entity) {
        MarketingCustomerVO vo = new MarketingCustomerVO();
        vo.id = entity.getId();
        vo.custNo = entity.getCustNo();
        vo.custName = entity.getCustName();
        vo.unifiedCreditCode = entity.getUnifiedCreditCode();
        vo.legalRepresentative = entity.getLegalRepresentative();
        vo.registeredCapital = entity.getRegisteredCapital();
        vo.registeredAddress = entity.getRegisteredAddress();
        vo.businessAddress = entity.getBusinessAddress();
        vo.businessScope = entity.getBusinessScope();
        vo.contactPerson = entity.getContactPerson();
        vo.contactMobile = entity.getContactMobile();
        vo.industry = entity.getIndustry();
        vo.groupType = entity.getGroupType();
        vo.groupName = entity.getGroupName();
        vo.customerType = entity.getCustomerType();
        vo.enterpriseType = entity.getEnterpriseType();
        vo.isKeystone = entity.getIsKeystone();
        vo.customerDesc = entity.getCustomerDesc();
        vo.isAccountOpened = entity.getIsAccountOpened();
        vo.creditAmount = entity.getCreditAmount();
        vo.creditExposureAmount = entity.getCreditExposureAmount();
        vo.touchRestricted = entity.getTouchRestricted();
        vo.currentLeadId = entity.getCurrentLeadId();
        vo.lastTouchTime = entity.getLastTouchTime();
        vo.mainManagerId = entity.getMainManagerId();
        vo.mainOrgId = entity.getMainOrgId();
        vo.ownershipStatus = entity.getOwnershipStatus();
        vo.ownershipSource = entity.getOwnershipSource();
        vo.ownershipMaintainMode = entity.getOwnershipMaintainMode();
        vo.ownershipDataDate = entity.getOwnershipDataDate();
        vo.ownershipUpdatedBy = entity.getOwnershipUpdatedBy();
        vo.ownershipUpdatedTime = entity.getOwnershipUpdatedTime();
        vo.ownershipManualReason = entity.getOwnershipManualReason();
        vo.profileVersion = entity.getProfileVersion();
        vo.accountOpenedByEmpId = entity.getAccountOpenedByEmpId();
        vo.accountOpenedTime = entity.getAccountOpenedTime();
        vo.openingTouchTaskId = entity.getOpeningTouchTaskId();
        vo.recordStatus = entity.getRecordStatus();
        vo.createdTime = entity.getCreatedTime();
        vo.updatedBy = entity.getUpdatedBy();
        vo.updatedTime = entity.getUpdatedTime();
        vo.lockVersion = entity.getLockVersion();
        return vo;
    }
}
