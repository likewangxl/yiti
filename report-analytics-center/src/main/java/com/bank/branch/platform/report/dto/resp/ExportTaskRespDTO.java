package com.bank.branch.platform.report.dto.resp;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * 异步导出任务响应 DTO（A.3 / E 章 / J 章导出系列）.
 *
 * <p>用法分层：
 * <ul>
 *   <li>M1.3 占位提交：仅 taskId + status=PENDING（其余字段 null，由 @JsonInclude 过滤）</li>
 *   <li>M5.3 status 查询：补 exportType / rowCount / errorMsg / createdTime</li>
 * </ul>
 */
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class ExportTaskRespDTO {

    /** 任务 ID */
    private String taskId;

    /** 状态：PENDING/RUNNING/SUCCESS/FAILED/CANCELLED */
    private String status;

    /** 导出类型（M5.3 status 查询返回） */
    private String exportType;

    /** 实际导出行数（M5.3 status 查询，SUCCESS 时返回） */
    private Integer rowCount;

    /** 失败原因（M5.3 status 查询，FAILED 时返回） */
    private String errorMsg;

    /** 创建时间 */
    private LocalDateTime createdTime;
}
