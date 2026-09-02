package com.bank.branch.platform.customer.entity.marketing;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/** 营销客户标签导入逐客户明细，对应 MARKETING_CUSTOMER_TAG_IMPORT_DETAIL。 */
@Data
@TableName("MARKETING_CUSTOMER_TAG_IMPORT_DETAIL")
public class MarketingCustomerTagImportDetail {

    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    private Long batchId;
    private Integer rowNo;
    private String custName;
    private String unifiedCreditCode;
    private String contactPerson;
    private String contactMobile;
    private String registeredAddress;
    private String businessAddress;
    private String rawRowJson;
    private String customerChangeType;
    private Long matchedCustomerId;
    private Long generatedLeadId;
    private String validationStatus;
    private String errorCode;
    private String errorMessage;
    private String approvalStatus;
    private String reviewedBy;
    private LocalDateTime reviewedTime;
    private String rejectReason;
    private Integer loadedFlag;
    private Long loadedRelId;
    private LocalDateTime loadedTime;
    private LocalDateTime createdTime;
}
