package com.bank.branch.platform.customer.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.annotation.TableField;
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
@TableName("CUST_TAG_REL")
public class CustTagRel {

    /** 主键ID（UUID，32位去连字符），对应 id */
    @TableId(value = "id", type = IdType.INPUT)
    private String id;

    /** 客户ID（关联 CUSTOMER_MARKET_CUSTOMER.id），对应 cust_id */
    private String custId;

    /** 标签ID（关联 cust_tag.id），对应 tag_id */
    private String tagId;

    /** 打标人（员工工号），对应 created_by */
    private String createdBy;

    /** 打标时间，对应 created_time */
    private LocalDateTime createdTime;

    /** 关系是否当前有效。 */
    private Integer active;

    /** 关系生效时间。 */
    private LocalDateTime effectiveTime;

    /** 关系失效时间。 */
    private LocalDateTime expiredTime;

    /** 最近更新人。 */
    private String updatedBy;

    /** 最近更新时间。 */
    private LocalDateTime updatedTime;

    /** 详情展示字段，不落 CUST_TAG_REL。 */
    @TableField(exist = false)
    private String custNo;

    /** 详情展示字段，不落 CUST_TAG_REL。 */
    @TableField(exist = false)
    private String custName;

    /** 客户群展示用统一社会信用代码，来自 CUSTOMER_MARKET_CUSTOMER，不落 CUST_TAG_REL。 */
    @TableField(exist = false)
    private String unifiedCreditCode;
}
