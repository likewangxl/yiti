package com.bank.branch.platform.report.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("RPT_FREE_REPORT_ROW")
public class RptFreeReportRow {
    @TableId(type = IdType.AUTO)
    private Long id;
    private String batchId;
    private String empId;
    private String orgCode;
    @com.baomidou.mybatisplus.annotation.TableField("COL_1")
    private String col1;
    @com.baomidou.mybatisplus.annotation.TableField("COL_2")
    private String col2;
    private String dataJson;
    private LocalDateTime createdTime;
}
