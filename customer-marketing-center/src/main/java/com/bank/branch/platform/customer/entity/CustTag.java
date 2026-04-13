package com.bank.branch.platform.customer.entity;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * 客户标签实体，对应 cust_tag 表。
 * <p>
 * 标签编码 tag_code 全局唯一，业务代码通过 tag_code 引用标签。
 * status 字段枚举：ACTIVE-启用，DISABLED-停用。
 * deleted 字段实现逻辑删除：0-未删除，1-已删除。
 * </p>
 */
@Data
public class CustTag {

    /** 主键ID（UUID，32位去连字符），对应 id */
    private String id;

    /** 标签名称（唯一），对应 tag_name */
    private String tagName;

    /** 标签编码（唯一，业务使用），对应 tag_code */
    private String tagCode;

    /** 标签分类（如：价值类/行业类/风险类），对应 tag_category */
    private String tagCategory;

    /** 标签优先级（数字越大优先级越高，用于排序），对应 tag_priority */
    private Integer tagPriority;

    /** 标签描述，对应 description */
    private String description;

    /** 状态：ACTIVE-启用/DISABLED-停用，对应 status */
    private String status;

    /** 创建人（员工工号），对应 created_by */
    private String createdBy;

    /** 创建时间，对应 created_time */
    private LocalDateTime createdTime;

    /** 最后更新人，对应 updated_by */
    private String updatedBy;

    /** 最后更新时间，对应 updated_time */
    private LocalDateTime updatedTime;

    /** 逻辑删除：0-未删除/1-已删除，对应 deleted */
    private Integer deleted;
}
