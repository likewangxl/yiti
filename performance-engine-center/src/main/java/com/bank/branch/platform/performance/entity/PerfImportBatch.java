package com.bank.branch.platform.performance.entity;

import lombok.Data;

import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 绩效导入批次表 perf_import_batch 贫血实体.
 *
 * <p>对齐 V1_0_0__performance_ddl.sql §8：主键 {@code id varchar(32)}，
 * 业务唯一键 {@code uk_batch_no(batch_no)}。
 *
 * <p>状态机（由 Service 层控制，DDL 不加 CHECK）：
 * <pre>
 *   CREATED (DDL 默认值)
 *     └─ 文件解析成功 ─&gt; RUNNING (V1.1 新增中间态)
 *           ├─ 导入完成 ─&gt; SUCCESS
 *           └─ 发生异常 ─&gt; FAILED
 * </pre>
 *
 * <p>字段说明：
 * <ul>
 *   <li>{@code import_type}：INDEX_RESULT / KPI_RESULT / TARGET（5 §6）</li>
 *   <li>{@code dim}：EMP / ORG / CUST</li>
 *   <li>{@code as_of_date}：KPI 导入基准日（可空，TARGET 类型无此字段）</li>
 *   <li>{@code file_md5}：用于幂等（同 MD5 可拒绝二次导入，由 Service 层校验）</li>
 *   <li>{@code remark}：导入结果摘要（成功/错误细节，常存 JSON 字符串）</li>
 *   <li>{@code error_file_object_id}：错误明细文件 ID（MinIO/对象存储键）</li>
 * </ul>
 */
@Data
public class PerfImportBatch {

    /** 批次 ID（varchar(32) 主键）. */
    private String id;

    /** 批次号（唯一，供外部追踪；通常形如 IMP20260422001）. */
    private String batchNo;

    /** 导入类型：INDEX_RESULT / KPI_RESULT / TARGET. */
    private String importType;

    /** 维度：EMP / ORG / CUST. */
    private String dim;

    /** KPI 导入基准日（可空）. */
    private LocalDate asOfDate;

    /** 文件名. */
    private String fileName;

    /** 文件 MD5（幂等校验）. */
    private String fileMd5;

    /** 状态：CREATED / RUNNING / SUCCESS / FAILED（语义由 Service 层保证）. */
    private String status;

    /** 总行数. */
    private Integer totalRows;

    /** 成功行数. */
    private Integer successRows;

    /** 失败行数. */
    private Integer errorRows;

    /** 错误明细文件 ID. */
    private String errorFileObjectId;

    /** 备注（导入结果摘要 JSON 等）. */
    private String remark;

    /** 创建人. */
    private String createdBy;

    /** 创建时间（DB CURRENT_TIMESTAMP 默认值）. */
    private LocalDateTime createdTime;

    /** 更新时间（DB ON UPDATE CURRENT_TIMESTAMP）. */
    private LocalDateTime updatedTime;
}
