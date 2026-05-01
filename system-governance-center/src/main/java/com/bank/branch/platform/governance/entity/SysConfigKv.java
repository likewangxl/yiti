package com.bank.branch.platform.governance.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 系统配置KV实体，对应 sys_config_kv 表。
 * <p>
 * 配置表采用 config_key 唯一约束，value_type 指定值的解析方式。
 * status 字段使用字符串枚举：ACTIVE-启用，DISABLED-禁用。
 * </p>
 */
@Data
@TableName("sys_config_kv")
public class SysConfigKv {

    /** 配置ID（UUID主键），对应 id */
    @TableId(value = "id", type = IdType.INPUT)
    private String id;

    /** 配置键（唯一），对应 config_key */
    private String configKey;

    /** 配置值，对应 config_value（longtext） */
    private String configValue;

    /** 值类型：STRING/JSON/NUMBER/BOOL，对应 value_type */
    private String valueType;

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
