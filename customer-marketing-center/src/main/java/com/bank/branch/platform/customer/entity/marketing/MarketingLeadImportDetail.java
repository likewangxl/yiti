package com.bank.branch.platform.customer.entity.marketing;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/** 线索导入原始行及其校验、处理结果。 */
@Data
@TableName("MARKETING_LEAD_IMPORT_DETAIL")
public class MarketingLeadImportDetail {

    @TableId(value = "id", type = IdType.AUTO)
    private Long id;
    private Long batchId;
    private Integer rowNo;
    private String custNo;
    private String custName;
    private String unifiedCreditCode;
    private String contactPerson;
    private String contactMobile;
    private String registeredAddress;
    private String businessAddress;
    private String industry;
    private String customerType;
    private BigDecimal creditAmount;
    private BigDecimal creditExposureAmount;
    private String rawRowJson;
    private String customerMatchStatus;
    private String requestedManagerId;
    private String requestedManagerOrgId;
    private String validationStatus;
    private String warningCode;
    private String warningMessage;
    private String errorCode;
    private String errorMessage;
    private Long matchedCustomerId;
    private Long matchedLeadId;
    private String matchedEntryEmpId;
    private String matchedEntryOrgId;
    private LocalDateTime matchedEntryTime;
    private String handlingStatus;
    private Long generatedLeadId;
    private LocalDateTime createdTime;
}
