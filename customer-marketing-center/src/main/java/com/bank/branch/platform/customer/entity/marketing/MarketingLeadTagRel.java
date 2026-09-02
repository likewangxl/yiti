package com.bank.branch.platform.customer.entity.marketing;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/** 营销线索标签快照，对应 MARKETING_LEAD_TAG_REL。 */
@Data
@TableName("MARKETING_LEAD_TAG_REL")
public class MarketingLeadTagRel {

    @TableId(value = "id", type = IdType.AUTO)
    private Long id;
    private Long leadId;
    private Long tagId;
    private String tagNameSnapshot;
    private String tagSource;
    private String createdBy;
    private LocalDateTime createdTime;
}
