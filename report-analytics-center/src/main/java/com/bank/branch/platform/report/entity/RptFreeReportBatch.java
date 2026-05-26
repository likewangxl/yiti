package com.bank.branch.platform.report.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("RPT_FREE_REPORT_BATCH")
public class RptFreeReportBatch {
    @TableId(type = IdType.INPUT)
    private String id;
    private String reportName;
    private String fileName;
    private String fileObjectKey;
    private String uploaderEmpId;
    private String uploaderName;
    private LocalDateTime importTime;
    private Integer rowCount;
    private String colDefs;
    private String status;
    private LocalDateTime createdTime;
    private LocalDateTime updatedTime;
}
