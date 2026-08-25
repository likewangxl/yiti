package com.bank.branch.platform.customer.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/** 线索标签名称快照，保证审批时看到提交时的标签。 */
@Data
@TableName("CUST_LEAD_TAG_REL")
public class CustLeadTagRel {
    @TableId(value = "id", type = IdType.INPUT)
    private String id;
    private String leadId;
    private String tagId;
    private String tagNameSnapshot;
    private String createdBy;
    private LocalDateTime createdTime;
}
