package com.bank.branch.platform.performance.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 绩效任务执行日志 perf_run_task 贫血实体.
 *
 * <p>对齐 DDL：主键 varchar(32) String；只读日志表，V1.0 不支持写入，
 * 由 V1.1 计算引擎触发 INSERT / UPDATE（本版本仅暴露 5 个只读 Mapper 方法）.
 *
 * <p>DDL 字段映射：
 * <ul>
 *   <li>{@code id}（主键 varchar(32)）</li>
 *   <li>{@code task_type}（METRIC_TRIAL/METRIC_RUN/KPI_RUN/RECALC）</li>
 *   <li>{@code task_key}（关键键，如 metric_code；V1.0 亦作为业务唯一标识 "任务编号" 查询使用）</li>
 *   <li>{@code data_date} / {@code data_version}</li>
 *   <li>{@code params_json}（longtext，参数 JSON）</li>
 *   <li>{@code status}（RUNNING/SUCCESS/FAILED）</li>
 *   <li>{@code started_by}（发起人；普通用户数据范围过滤的基准）</li>
 *   <li>{@code start_time} / {@code end_time}</li>
 *   <li>{@code error_msg}（longtext）</li>
 *   <li>{@code result_preview_json}（longtext）</li>
 *   <li>{@code created_time}（insert 由 DB 默认值填充）</li>
 * </ul>
 *
 * <p>索引：idx_task_type / idx_status / idx_started_by / idx_created_time（DDL 已建）.
 */
@Data
@TableName("PERF_RUN_TASK")
public class PerfRunTask {

    /** 任务ID（varchar(32) 主键）. */
    @TableId(value = "id", type = IdType.INPUT)
    private String id;

    /** 任务类型：METRIC_TRIAL / METRIC_RUN / KPI_RUN / RECALC. */
    private String taskType;

    /** 触发来源：RECALC / SCHEDULED / MANUAL（V1.13+ 任务监控用）. */
    private String triggerType;

    /** 关键键（如 metric_code；V1.0 亦作为"任务编号"唯一标识使用，映射 DDL 的 task_key）. */
    private String taskKey;

    /** 数据日期. */
    private LocalDate dataDate;

    /** 数据版本（与 sys_control.active_version / next_version 对齐）. */
    private String dataVersion;

    /** 参数（JSON，longtext）. */
    private String paramsJson;

    /** 状态：RUNNING / SUCCESS / FAILED. */
    private String status;

    /** 发起人（普通用户数据范围过滤基准：started_by = currentUserId）. */
    private String startedBy;

    /** 开始时间. */
    private LocalDateTime startTime;

    /** 结束时间. */
    private LocalDateTime endTime;

    /** 错误信息（longtext）. */
    private String errorMsg;

    /** 结果预览（JSON，longtext）. */
    private String resultPreviewJson;

    /** 创建时间（insert 由数据库 CURRENT_TIMESTAMP 默认值填充）. */
    private LocalDateTime createdTime;
}
