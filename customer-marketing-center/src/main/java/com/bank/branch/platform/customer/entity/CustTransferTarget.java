package com.bank.branch.platform.customer.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/** 客户转交接收人及角色快照。 */
@Data
@TableName("CUST_TRANSFER_TARGET")
public class CustTransferTarget {
    @TableId(value = "id", type = IdType.INPUT)
    private String id;
    private String transferId;
    private String targetEmpId;
    private String targetOrgId;
    private String targetRole;
    private Integer sortNo;
    private LocalDateTime createdTime;
}
