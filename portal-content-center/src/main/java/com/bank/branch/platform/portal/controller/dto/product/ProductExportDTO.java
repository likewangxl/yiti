package com.bank.branch.platform.portal.controller.dto.product;

import com.alibaba.excel.annotation.ExcelProperty;
import com.alibaba.excel.annotation.write.style.ColumnWidth;
import lombok.Data;

/**
 * 产品导出 DTO（EasyExcel 导出专用，16 列）
 */
@Data
public class ProductExportDTO {

    @ExcelProperty("序号")
    @ColumnWidth(8)
    private Integer rowNo;

    @ExcelProperty("产品代码")
    @ColumnWidth(18)
    private String productCode;

    @ExcelProperty("产品名称")
    @ColumnWidth(25)
    private String productName;

    @ExcelProperty("产品类别")
    @ColumnWidth(15)
    private String productCategory;

    @ExcelProperty("产品类别描述")
    @ColumnWidth(15)
    private String productCategoryDesc;

    @ExcelProperty("产品描述")
    @ColumnWidth(30)
    private String description;

    @ExcelProperty("是否支持中场")
    @ColumnWidth(15)
    private String supportForSupportRequestDesc;

    @ExcelProperty("维护部门编码")
    @ColumnWidth(18)
    private String productDeptOrgCode;

    @ExcelProperty("维护部门名称")
    @ColumnWidth(20)
    private String productDeptOrgName;

    @ExcelProperty("附件对象ID")
    @ColumnWidth(20)
    private String fileObjectId;

    @ExcelProperty("负责人")
    @ColumnWidth(20)
    private String responsibleEmpNames;

    @ExcelProperty("状态")
    @ColumnWidth(10)
    private String status;

    @ExcelProperty("创建人")
    @ColumnWidth(12)
    private String createdBy;

    @ExcelProperty("创建时间")
    @ColumnWidth(22)
    private String createdTime;

    @ExcelProperty("更新人")
    @ColumnWidth(12)
    private String updatedBy;

    @ExcelProperty("更新时间")
    @ColumnWidth(22)
    private String updatedTime;
}
