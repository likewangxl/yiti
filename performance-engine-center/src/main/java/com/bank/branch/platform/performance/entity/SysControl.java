package com.bank.branch.platform.performance.entity;

import lombok.Data;

import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 数据版本控制表 sys_control 贫血实体.
 *
 * <p>V1.0.3 对齐: 新增 5 个字段（remark / updated_by / publish_source / publish_by / publish_time）。
 * <p>唯一键: (scope_dim, latest_data_date, current_version)（V1.0.3 扩展）
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

    // ===== V1.0.3 新增字段（Task B3）=====

    /** 切版备注, 对应 remark. */
    private String remark;

    /** 最后更新人, 对应 updated_by. */
    private String updatedBy;

    /** 发布来源: MANUAL / AUTO / ROLLBACK, 对应 publish_source. */
    private String publishSource;

    /** 发布人, 对应 publish_by. */
    private String publishBy;

    /** 发布时间, 对应 publish_time. */
    private LocalDateTime publishTime;
}
