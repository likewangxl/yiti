package com.bank.branch.platform.report.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * SQL_PROBE_EXPORT_TASK 实体 —— SQL 探查「异步下载」任务.
 *
 * <p>用户点「下载」→ 后台线程跑 SQL（无行数限制，SXSSF 流式）→ 生成 xlsx 存 FILE_CONTENT →
 * 列表轮询状态/进度，成功后凭 FILE_CONTENT 下载。</p>
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

    /** 生成的 xlsx 文件字节 */
    private byte[] fileContent;

    /** 失败原因 */
    private String errorMsg;

    /** 创建时间 */
    private LocalDateTime createdTime;

    /** 完成时间 */
    private LocalDateTime finishedTime;
}
