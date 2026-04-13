package com.bank.branch.platform.portal.service.dto;

import com.alibaba.excel.annotation.ExcelProperty;
import com.alibaba.excel.annotation.write.style.ColumnWidth;
import lombok.Data;

/**
 * 产品导出行 — EasyExcel POJO (D.7)
 * <p>10 列，严格按 03 §H.7.1 定义</p>
 */
@Data
public class ProductExportRow {

    @ExcelProperty(value = "产品编码", index = 0)
    @ColumnWidth(20)
    private String productCode;

    @ExcelProperty(value = "产品名称", index = 1)
    @ColumnWidth(30)
    private String productName;

    @ExcelProperty(value = "产品类别", index = 2)
    @ColumnWidth(20)
    private String productCategoryDesc;

    @ExcelProperty(value = "产品说明", index = 3)
    @ColumnWidth(50)
    private String description;

    @ExcelProperty(value = "是否支持中场支持", index = 4)
    @ColumnWidth(15)
    private String supportForSupportRequest;

    @ExcelProperty(value = "维护组织", index = 5)
    @ColumnWidth(25)
    private String productDeptOrgName;

    @ExcelProperty(value = "负责人姓名列表", index = 6)
    @ColumnWidth(30)
    private String responsibleEmpNames;

    @ExcelProperty(value = "负责人手机号列表", index = 7)
    @ColumnWidth(30)
    private String responsibleEmpMobiles;

    @ExcelProperty(value = "状态", index = 8)
    @ColumnWidth(10)
    private String statusDesc;

    @ExcelProperty(value = "更新时间", index = 9)
    @ColumnWidth(20)
    private String updatedTime;
}
