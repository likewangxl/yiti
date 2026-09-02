package com.bank.branch.platform.customer.entity.marketing;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.annotation.Version;
import lombok.Data;

import java.time.LocalDateTime;

/** 跨机构营销可配置校验规则，对应 {@code MARKETING_CROSS_ORG_RULE}。 */
@Data
@TableName("MARKETING_CROSS_ORG_RULE")
public class MarketingCrossOrgRule {

    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    private String ruleCode;
    private String ruleName;
    private Integer enabled;
    private String dataSource;
    private String failureMessage;
    private String extensionParams;
    private Integer sortNo;
    private String createdBy;
    private LocalDateTime createdTime;
    private String updatedBy;
    private LocalDateTime updatedTime;

    @Version
    private Integer lockVersion;
}
