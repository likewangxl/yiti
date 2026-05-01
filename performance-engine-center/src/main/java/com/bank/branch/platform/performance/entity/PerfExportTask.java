package com.bank.branch.platform.performance.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 绩效异步导出任务表 perf_export_task 贫血实体.
 *
 * <p>对齐 V1_2_1__perf_export_task.sql：主键 {@code id varchar(32)}，无业务唯一键；
 * 由 {@code operator_id} + {@code status} 索引支撑 "查本人任务" / "查待执行任务" 等场景。
 *
 * <p>状态机（由 Service 层控制，DDL 不加 CHECK）：
 * <pre>
 *   PENDING (DDL 默认值)
 *     └─ 开始执行 ─&gt; RUNNING
 *           ├─ 生成文件并上传 ─&gt; SUCCESS (file_key 回填)
 *           └─ 发生异常 ─&gt; FAILED (error_msg 回填)
 * </pre>
 *
 * <p>字段说明：
 * <ul>
 *   <li>{@code export_type}：KPI / METRIC / ALLOC / DETAIL 四种导出策略类型</li>
 *   <li>{@code params_json}：导出参数 JSON（由策略按需反序列化）</li>
 *   <li>{@code file_key}：成功时回填 MinIO object key，消费方用于预签名下载 URL</li>
 *   <li>{@code expire_at}：文件过期时间（默认 7 天，由 Service 层决定）</li>
 *   <li>{@code operator_id}：下载权限校验 ({@code EXPORT_TASK_OWNER_MISMATCH})</li>
 * </ul>
 */
@Data
@TableName("perf_export_task")
public class PerfExportTask {

    /** 任务 ID（varchar(32) 主键）. */
    @TableId(value = "id", type = IdType.INPUT)
    private String id;

    /** 导出类型：KPI / METRIC / ALLOC / DETAIL. */
    private String exportType;

    /** 导出参数 JSON. */
    private String paramsJson;

    /** 状态：PENDING / RUNNING / SUCCESS / FAILED. */
    private String status;

    /** MinIO object key（成功时回填）. */
    private String fileKey;

    /** 文件大小（字节）. */
    private Long fileSize;

    /** 导出行数. */
    private Integer rowCount;

    /** 文件过期时间. */
    private LocalDateTime expireAt;

    /** 操作人员工号. */
    private String operatorId;

    /** 失败原因. */
    private String errorMsg;

    /** 创建时间（DB CURRENT_TIMESTAMP 默认值）. */
    private LocalDateTime createdTime;

    /** 更新时间（DB ON UPDATE CURRENT_TIMESTAMP）. */
    private LocalDateTime updatedTime;
}
