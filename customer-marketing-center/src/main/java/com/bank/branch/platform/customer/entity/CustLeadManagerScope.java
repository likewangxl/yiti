package com.bank.branch.platform.customer.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/** 线索指定客户经理范围快照。 */
@Data
@TableName("CUST_LEAD_MANAGER_SCOPE")
public class CustLeadManagerScope {
    @TableId(value = "id", type = IdType.INPUT)
    private String id;
    private String leadId;
    private String managerEmpId;
    private String managerOrgId;
    private String assignmentType;
    private Integer isPrimary;
    private String createdBy;
    private LocalDateTime createdTime;
}
