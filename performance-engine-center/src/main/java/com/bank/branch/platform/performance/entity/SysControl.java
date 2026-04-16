package com.bank.branch.platform.performance.entity;

import lombok.Data;

import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 数据版本控制表 sys_control 贫血实体.
 *
 * <p>对齐生产 DDL (v1.2): 7 字段, 无 created_by / updated_by / deleted / version,
 * 不遵循业务审计字段规范 —— 这是版本控制基础设施表.
 *
 * <p>唯一键: (scope_dim, latest_data_date)
 * <p>索引: (scope_dim, is_valid)
 */
@Data
public class SysControl {

    /** 控制ID (varchar(32) 主键). */
    private String id;

    /** 维度: EMP / ORG / CUST, 对应 scope_dim. */
    private String scopeDim;

    /** 最新数据日期, 对应 latest_data_date. */
    private LocalDate latestDataDate;

    /** 当前有效版本号, 对应 current_version. */
    private String currentVersion;

    /** 是否有效: 1 有效, 0 失效, 对应 is_valid. */
    private Integer isValid;

    /** 创建时间, 对应 created_time. */
    private LocalDateTime createdTime;

    /** 更新时间, 对应 updated_time. */
    private LocalDateTime updatedTime;
}
