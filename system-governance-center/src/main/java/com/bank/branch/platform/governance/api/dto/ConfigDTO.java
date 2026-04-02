package com.bank.branch.platform.governance.api.dto;

import lombok.Data;

/**
 * 系统配置传输对象
 */
@Data
public class ConfigDTO {

    /** 配置ID */
    private String id;

    /** 配置键 */
    private String configKey;

    /** 配置值 */
    private String configValue;

    /** 值类型 STRING/JSON/NUMBER/BOOL */
    private String valueType;

    /** 状态 ACTIVE/DISABLED */
    private String status;

    /** 备注 */
    private String remark;

    /** 创建时间（ISO 8601） */
    private String createdTime;

    /** 更新时间（ISO 8601） */
    private String updatedTime;
}
