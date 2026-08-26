package com.bank.branch.platform.customer.entity.marketing;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.annotation.Version;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 营销线索提交快照。
 *
 * <p>每一次营销事项对应一行，故意不保留旧版的版本链字段。审批通过后由
 * 营销客户服务将快照装配到 {@code MARKETING_CUSTOMER_INFO}。</p>
 */
@Data
@TableName("MARKETING_LEAD_INFO")
public class MarketingLeadInfo {

    @TableId(value = "id", type = IdType.AUTO)
    private Long id;
    private String leadNo;
    private Long custId;
    private String leadType;
    private String customerMatchStatus;
    private Integer baseCustomerProfileVersion;
    private String custNoSnapshot;
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
    private Integer isAccountOpenedSnapshot;
    private Integer touchRestricted;
    private String customerDesc;
    private BigDecimal creditAmount;
    private BigDecimal creditExposureAmount;
    private String leadSource;
    private String distributionMode;
    private String poolStatus;
    private String mainManagerIdSnapshot;
    private String mainOrgIdSnapshot;
    private String entryEmpId;
    private String entryOrgId;
    private LocalDateTime entryTime;
    private String leadStatus;
    private String activeDedupKey;
    private String submittedBy;
    private LocalDateTime submittedTime;
    private String businessKey;
    private String processInstanceId;
    private Long importBatchId;
    private Integer batchRowNo;
    private Long tagImportDetailId;
    private String reviewedBy;
    private LocalDateTime reviewedTime;
    private String rejectReason;
    private String remark;
    private String recordStatus;
    private String createdBy;
    private LocalDateTime createdTime;
    private String updatedBy;
    private LocalDateTime updatedTime;

    @Version
    private Integer lockVersion;
}
