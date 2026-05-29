package com.bank.branch.platform.performance.eval.dto;

import com.alibaba.excel.annotation.ExcelProperty;
import lombok.Data;

/** 人员评价角色导入 Excel 行模型（3 列，填角色名称）。 */
@Data
public class EvalUserTagImportRow {
    @ExcelProperty("工号")
    private String empId;
    @ExcelProperty("被评价角色")
    private String beEvalRoleName;
    @ExcelProperty("评价角色")
    private String evalRoleNames;
    @ExcelProperty("是否参与评价")
    private String evalEnabledText;
}
