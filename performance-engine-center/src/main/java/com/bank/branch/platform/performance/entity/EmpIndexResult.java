package com.bank.branch.platform.performance.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 员工指标结果宽表 emp_index_result 贫血实体.
 *
 * <p>对齐 V1_0_0__performance_ddl.sql §10：主键 {@code id bigint AUTO_INCREMENT}，
 * 维度键 {@code emp_id varchar(50)}，200 个 {@code val_1 .. val_200 decimal(20,4)} 值槽。
 *
 * <p>唯一键：{@code uk_subject_date_ver (emp_id, data_date, version)}
 * <p>索引：{@code idx_date_ver (data_date, version)}
 *
 * <p><strong>设计说明</strong>：
 * <ul>
 *   <li>此 Entity 仅表达"一行"在行维度（emp_id + data_date + version）上的存在性，
 *       <em>不</em>把 200 个 slot 全部展开为 Java 字段（避免构造器/getter/setter 爆炸）；</li>
 *   <li>{@code Mapper.insertSlotValue} / {@code selectSlotValue} 按 slot 单列读写，
 *       XML 通过 {@code val_${slot}} 动态拼接列名，slot ∈ [1,200] 由 Service 层强制校验；</li>
 *   <li>若后续需要"一次读取一行 200 slot" 用于 debug，可用 P1.4 的 SlotMapTypeHandler。</li>
 * </ul>
 */
@Data
@TableName("EMP_INDEX_RESULT")
public class EmpIndexResult {

    /** 主键（bigint AUTO_INCREMENT）. */
    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    /** 数据日期. */
    private LocalDate dataDate;

    /** 数据版本（与 sys_control.active_version 对齐）. */
    private String version;

    /** 员工工号（与 uk_subject_date_ver 组合）. */
    private String empId;

    /** 创建时间（DB CURRENT_TIMESTAMP 默认值填充）. */
    private LocalDateTime createdTime;
}
