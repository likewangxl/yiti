package com.bank.branch.platform.customer.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/** 跨机构营销申请校验规则配置。 */
@Data
@TableName("CROSS_ORG_MARKETING_RULE")
public class CrossOrgMarketingRule {
    @TableId(value = "id", type = IdType.INPUT)
    private String id;
    private String ruleCode;
    private String ruleName;
    private Integer enabled;
    private String dataSource;
    private String failureMessage;
    private String extensionParams;
    private Integer sortNo;
    private String updatedBy;
    private LocalDateTime updatedTime;
}
