package com.bank.branch.platform.performance.eval.dto;

import com.alibaba.excel.annotation.ExcelProperty;
import lombok.Data;

/** 人员标签列表导出 Excel 行模型，列序与页面列表一致。 */
@Data
public class EvalUserRoleExportRow {
    @ExcelProperty("姓名")
    private String userName;
    @ExcelProperty("工号")
    private String empId;
    @ExcelProperty("部门")
    private String orgName;
    @ExcelProperty("岗位")
    private String position;
    @ExcelProperty("角色")
    private String roleNames;
    @ExcelProperty("被评价人角色")
    private String beEvalRole;
    @ExcelProperty("评价人角色")
    private String evalRoles;
    @ExcelProperty("是否参与评价")
    private String evalEnabled;
}
