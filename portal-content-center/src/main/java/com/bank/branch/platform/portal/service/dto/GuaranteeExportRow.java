package com.bank.branch.platform.portal.service.dto;

import com.alibaba.excel.annotation.ExcelProperty;
import com.alibaba.excel.annotation.write.style.ColumnWidth;
import lombok.Data;

/**
 * 担保信息导出行（EasyExcel）。列顺序与担保查询页面数据列一致。
 */
@Data
@ColumnWidth(18)
public class GuaranteeExportRow {

    @ExcelProperty("客户名称")
    private String clientName;

    @ExcelProperty("业务额度（万元）")
    private String amountManage;

    @ExcelProperty("剩余额度（万元）")
    private String usableExposureSum;

    @ExcelProperty("融资额度（万元）")
    private String exposureAmount;

    @ExcelProperty("授信到期日")
    private String lastExpire;

    @ExcelProperty("经办人")
    private String operator;

    @ExcelProperty("数据变动日期")
    private String createTime;

    @ExcelProperty("变更日期")
    private String updateTime;
}
