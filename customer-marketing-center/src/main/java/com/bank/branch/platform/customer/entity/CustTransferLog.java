package com.bank.branch.platform.customer.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/** 客户转交操作主记录。 */
@Data
@TableName("CUST_TRANSFER_LOG")
public class CustTransferLog {
    @TableId(value = "id", type = IdType.INPUT)
    private String id;
    private String transferNo;
    private String custId;
    private String claimId;
    private String fromManagerId;
    private String fromOrgId;
    private String primaryToManagerId;
    private String primaryToOrgId;
    private Integer accountOpenedSnapshot;
    private String reason;
    private String status;
    private String operatorEmpId;
    private LocalDateTime completedTime;
    private String failureReason;
    private LocalDateTime createdTime;
    private LocalDateTime updatedTime;
}
