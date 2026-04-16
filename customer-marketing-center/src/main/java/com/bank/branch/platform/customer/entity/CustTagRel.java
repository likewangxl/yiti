package com.bank.branch.platform.customer.entity;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * 客户-标签关联实体，对应 cust_tag_rel 表。
 * <p>
 * 记录客户与标签的多对多关联关系，防重复打标由唯一索引 uk_cust_tag(cust_id, tag_id) 保证。
 * 该表无 deleted 字段，通过物理删除管理关联记录。
 * </p>
 */
@Data
public class CustTagRel {

    /** 主键ID（UUID，32位去连字符），对应 id */
    private String id;

    /** 客户ID（关联 cust_master.id），对应 cust_id */
    private String custId;

    /** 标签ID（关联 cust_tag.id），对应 tag_id */
    private String tagId;

    /** 打标人（员工工号），对应 created_by */
    private String createdBy;

    /** 打标时间，对应 created_time */
    private LocalDateTime createdTime;
}
