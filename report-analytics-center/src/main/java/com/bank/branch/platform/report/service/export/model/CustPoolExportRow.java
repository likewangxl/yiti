package com.bank.branch.platform.report.service.export.model;

import com.alibaba.excel.annotation.ExcelProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 客户池汇总导出行（M5.2.3 占位）.
 *
 * <p>对齐 03 §C.4 列定义（orgId / orgName / 公海客户数 / 私海客户数 / 流转占比）。
 */
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class CustPoolExportRow {

    @ExcelProperty("机构ID")
    private String orgId;

    @ExcelProperty("机构名称")
    private String orgName;

    @ExcelProperty("公海客户数")
    private Integer publicPoolCount;

    @ExcelProperty("私海客户数")
    private Integer privatePoolCount;

    @ExcelProperty("流转占比")
    private String flowRate;
}
