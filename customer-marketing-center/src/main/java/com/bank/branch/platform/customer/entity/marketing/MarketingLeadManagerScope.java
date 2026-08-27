package com.bank.branch.platform.customer.entity.marketing;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/** 营销线索接收人快照，对应 MARKETING_LEAD_MANAGER_SCOPE。 */
@Data
@TableName("MARKETING_LEAD_MANAGER_SCOPE")
public class MarketingLeadManagerScope {

    @TableId(value = "id", type = IdType.AUTO)
    private Long id;
    private Long leadId;
    private String managerEmpId;
    private String managerOrgId;
    private String assignmentType;
    private Integer isPrimary;
    private String createdBy;
    private LocalDateTime createdTime;
}
