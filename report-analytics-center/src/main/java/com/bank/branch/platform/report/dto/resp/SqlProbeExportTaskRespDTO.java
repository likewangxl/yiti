package com.bank.branch.platform.report.dto.resp;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * SQL 探查「异步下载」任务响应（任务列表轮询用）.
 *
 * <p>{@code sqlText} 列表场景按前 200 字截断。{@code status}=SUCCESS 时前端方可点击下载。</p>
 */
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class SqlProbeExportTaskRespDTO {

    /** 任务 ID */
    private String id;

    /** SQL 语句（列表场景截断 200 字） */
    private String sqlText;

    /** 执行原因 */
    private String remark;

    /** 状态：RUNNING / SUCCESS / FAILED */
    private String status;

    /** 导出行数（成功后填充） */
    private Integer rowCount;

    /** 下载文件名（成功后填充） */
    private String fileName;

    /** 失败原因 */
    private String errorMsg;

    /** 创建时间 */
    private LocalDateTime createdTime;

    /** 完成时间 */
    private LocalDateTime finishedTime;
}
