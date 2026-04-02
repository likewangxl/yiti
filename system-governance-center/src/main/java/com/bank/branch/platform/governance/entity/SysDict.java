package com.bank.branch.platform.governance.entity;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * 字典实体，对应 sys_dict 表。
 * <p>
 * 字典表采用 dict_type + dict_code 唯一约束，同一类型下编码不可重复。
 * status 字段使用字符串枚举：ACTIVE-启用，DISABLED-禁用。
 * </p>
 */
@Data
public class SysDict {

    /** 字典ID（UUID主键），对应 id */
    private String id;

    /** 字典类型，对应 dict_type */
    private String dictType;

    /** 字典编码，对应 dict_code */
    private String dictCode;

    /** 字典标签（显示名称），对应 dict_label */
    private String dictLabel;

    /** 字典值（实际存储值），对应 dict_value */
    private String dictValue;

    /** 排序号，对应 sort_order */
    private Integer sortOrder;

    /** 状态：ACTIVE-启用, DISABLED-禁用，对应 status */
    private String status;

    /** 备注，对应 remark */
    private String remark;

    /** 创建人，对应 created_by */
    private String createdBy;

    /** 创建时间，对应 created_time */
    private LocalDateTime createdTime;

    /** 更新人，对应 updated_by */
    private String updatedBy;

    /** 更新时间，对应 updated_time */
    private LocalDateTime updatedTime;
}
