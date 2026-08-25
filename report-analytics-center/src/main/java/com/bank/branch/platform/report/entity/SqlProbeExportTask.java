package com.bank.branch.platform.report.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * SQL_PROBE_EXPORT_TASK 实体 —— SQL 探查「异步下载」任务.
 *
 * <p>用户点「下载」→ 后台线程按请求上限跑 SQL → 每 10000 行生成一个 xlsx，超出后压缩为 zip，
 * 成品上传 OBS；当前 schema 兼容过渡阶段，FILE_CONTENT 保存 {@code OBS_FILE_ID:<fileId>} 小引用，
 * 旧任务仍可能保存完整 BLOB → 列表轮询状态/进度，成功后下载。</p>
 */
@Data
@TableName("SQL_PROBE_EXPORT_TASK")
public class SqlProbeExportTask {

    /** 任务 ID（UUID 去横线） */
    @TableId(value = "ID", type = IdType.INPUT)
    private String id;

    /** 发起人工号 */
    private String empId;

    /** 规范化后的 SQL */
    private String sqlText;

    /** 执行原因 */
    private String remark;

    /** 状态：RUNNING / SUCCESS / FAILED */
    private String status;

    /** 导出行数 */
    private Integer rowCount;

    /** 下载文件名 */
    private String fileName;

    /**
     * 过渡存储：新任务保存 {@code OBS_FILE_ID:<fileId>} 引用，旧任务保留完整 xlsx/zip BLOB。
     */
    private byte[] fileContent;

    /** 失败原因 */
    private String errorMsg;

    /** 创建时间 */
    private LocalDateTime createdTime;

    /** 完成时间 */
    private LocalDateTime finishedTime;
}
