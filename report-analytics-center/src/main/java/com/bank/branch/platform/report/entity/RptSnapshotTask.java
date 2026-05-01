package com.bank.branch.platform.report.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * rpt_snapshot_task 实体 —— 快照任务配置（V1 预留）.
 *
 * <p>贫血模型，字段完全对齐 {@code V1_0_0__rpt_init.sql} §3.
 *
 * <p>V1 状态：仅建表不启用；V2 扩展——当日活 &gt; 1000 或仪表盘并发 &gt; 500 QPS 时引入本地快照加速.
 */
@Data
@TableName("rpt_snapshot_task")
public class RptSnapshotTask {

    /** 任务 ID */
    @TableId(value = "id", type = IdType.INPUT)
    private String id;

    /** 任务名称 */
    private String taskName;

    /** 快照类型（DAILY / MONTHLY，V2 扩展） */
    private String snapshotType;

    /** Cron 表达式 */
    private String cronExpr;

    /** 状态：ACTIVE / DISABLED */
    private String status;

    /** 最近执行时间 */
    private LocalDateTime lastRunTime;

    /** 下次执行时间 */
    private LocalDateTime nextRunTime;

    /** 创建时间 */
    private LocalDateTime createdTime;

    /** 更新时间 */
    private LocalDateTime updatedTime;
}
