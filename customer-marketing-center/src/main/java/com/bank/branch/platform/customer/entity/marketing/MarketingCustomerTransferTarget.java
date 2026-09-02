package com.bank.branch.platform.customer.entity.marketing;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/** 营销客户转交接收人快照。当前页面一、二只产生一条 PRIMARY。 */
@Data
@TableName("MARKETING_CUSTOMER_TRANSFER_TARGET")
public class MarketingCustomerTransferTarget {

    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    private Long transferId;
    private String targetEmpId;
    private String targetOrgId;
    private String targetRole;
    private Integer sortNo;
    private String createdBy;
    private LocalDateTime createdTime;
}
