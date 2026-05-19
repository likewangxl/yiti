package com.bank.branch.platform.performance.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 机构指标结果宽表 org_index_result 贫血实体.
 *
 * <p>对齐 V1_0_0__performance_ddl.sql §11：主键 {@code id bigint AUTO_INCREMENT}，
 * 维度键 {@code org_code varchar(50)}，400 个 {@code val_1 .. val_400 decimal(20,4)} 值槽。
 *
 * <p>唯一键：{@code uk_subject_date_ver (org_code, data_date, version)}
 * <p>索引：{@code idx_date_ver (data_date, version)}
 *
 * <p>设计理念参见 {@link EmpIndexResult}：不展开 200 个 val_* 字段，通过 Mapper 的
 * {@code insertSlotValue} / {@code selectSlotValue} 按 slot 单列读写。
 */
@Data
@TableName("ORG_INDEX_RESULT")
public class OrgIndexResult {

    /** 主键（bigint AUTO_INCREMENT）. */
    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    /** 数据日期. */
    private LocalDate dataDate;

    /** 数据版本. */
    private String version;

    /** 机构编码（与 uk_subject_date_ver 组合）. */
    private String orgCode;

    /** 创建时间（DB CURRENT_TIMESTAMP 默认值填充）. */
    private LocalDateTime createdTime;

    /** 最近更新时间（V1.12 指标结果导入新增；DB ON UPDATE CURRENT_TIMESTAMP）. */
    private LocalDateTime updatedTime;
}
