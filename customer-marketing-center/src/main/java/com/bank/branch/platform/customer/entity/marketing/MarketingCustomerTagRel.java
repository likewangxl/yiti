package com.bank.branch.platform.customer.entity.marketing;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/** 营销客户与正式标签的当前/历史关系，对应 MARKETING_CUSTOMER_TAG_REL。 */
@Data
@TableName("MARKETING_CUSTOMER_TAG_REL")
public class MarketingCustomerTagRel {

    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    private Long custId;
    private Long tagId;
    private Integer active;
    private String sourceType;
    private String sourceRefId;
    private LocalDateTime effectiveTime;
    private LocalDateTime expiredTime;
    private String createdBy;
    private LocalDateTime createdTime;
    private String updatedBy;
    private LocalDateTime updatedTime;

    /** 客户群展示字段，由查询 MARKETING_CUSTOMER_INFO 回填。 */
    @TableField(exist = false)
    private String custNo;
    @TableField(exist = false)
    private String custName;
    @TableField(exist = false)
    private String unifiedCreditCode;
    @TableField(exist = false)
    private String mainManagerId;
    @TableField(exist = false)
    private String mainOrgId;
    @TableField(exist = false)
    private String accountOpened;
}
