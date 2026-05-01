package com.bank.branch.platform.report.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * rpt_export_task 实体 —— 报表异步导出任务.
 *
 * <p>贫血模型，字段完全对齐 {@code V1_0_0__rpt_init.sql} §4
 * （与 performance-engine-center.perf_export_task 同构）.
 *
 * <p>业务约束：
 * <ul>
 *   <li>状态机：PENDING → RUNNING → SUCCESS / FAILED</li>
 *   <li>fileKey 保存 MinIO object key（成功时回填）</li>
 *   <li>expireAt 文件过期时间（消费方判定是否可下载）</li>
 *   <li>operatorId 归属字段——下载时用于 EXPORT_TASK_OWNER_MISMATCH 校验</li>
 *   <li>paramsJson 保存导出参数 JSON（不同 exportType 策略按需反序列化）</li>
 * </ul>
 */
@Data
@TableName("RPT_EXPORT_TASK")
public class RptExportTask {

    /** 导出任务 ID */
    @TableId(value = "id", type = IdType.INPUT)
    private String id;

    /** 类型：DYNAMIC_QUERY / FIXED_REPORT / SQL_PROBE 等 */
    private String exportType;

    /** 导出参数 JSON */
    private String paramsJson;

    /** 状态：PENDING / RUNNING / SUCCESS / FAILED */
    private String status;

    /** MinIO object key */
    private String fileKey;

    /** 文件大小（字节） */
    private Long fileSize;

    /** 导出行数 */
    private Integer rowCount;

    /** 文件过期时间 */
    private LocalDateTime expireAt;

    /** 操作人员工号（归属字段） */
    private String operatorId;

    /** 失败原因 */
    private String errorMsg;

    /** 创建时间 */
    private LocalDateTime createdTime;

    /** 更新时间 */
    private LocalDateTime updatedTime;
}
