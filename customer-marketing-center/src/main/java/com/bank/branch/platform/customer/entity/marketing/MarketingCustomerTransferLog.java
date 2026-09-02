package com.bank.branch.platform.customer.entity.marketing;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/** 营销客户主办权转交/取消主办的主记录。 */
@Data
@TableName("MARKETING_CUSTOMER_TRANSFER_LOG")
public class MarketingCustomerTransferLog {

    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    private String transferNo;
    private Long custId;
    private Long claimId;
    private String transferAction;
    private String fromManagerId;
    private String fromOrgId;
    private String primaryToManagerId;
    private String primaryToOrgId;
    private Integer accountOpenedSnapshot;
    private String transferSource;
    private String reason;
    private String status;
    private String operatorEmpId;
    private LocalDateTime completedTime;
    private String failureReason;
    private LocalDateTime createdTime;
    private LocalDateTime updatedTime;
}
