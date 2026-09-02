package com.bank.branch.platform.redengine.api.dto;

import com.alibaba.excel.annotation.ExcelProperty;
import lombok.Data;

import java.time.LocalDateTime;

/** 任务 ZIP 内 Excel 的一行；字段只保留任务导出所需的业务快照。 */
@Data
public class ReTaskExportRowDTO {

    @ExcelProperty("任务名称")
    private String taskTitle;

    @ExcelProperty("任务发布时间")
    private LocalDateTime publishedAt;

    @ExcelProperty("任务结束时间")
    private LocalDateTime taskEndAt;

    @ExcelProperty("党支部")
    private String branchName;

    @ExcelProperty("提交人")
    private String submitterId;

    @ExcelProperty("提交时间")
    private LocalDateTime submittedAt;

    @ExcelProperty("填报内容")
    private String content;

    @ExcelProperty("明细项")
    private String itemCode;
}
