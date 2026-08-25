package com.bank.branch.platform.customer.dto.req;

import com.alibaba.excel.annotation.ExcelProperty;
import lombok.Data;

/**
 * 客户标签关联 Excel 导入行。
 */
@Data
public class TagCustomerImportRow {

    @ExcelProperty("客户名称")
    private String custName;

    @ExcelProperty("纳税人识别号（统一社会信用代码）")
    private String unifiedCreditCode;

    @ExcelProperty("标签名称")
    private String tagName;

    @ExcelProperty("标签说明")
    private String tagDescription;
}
