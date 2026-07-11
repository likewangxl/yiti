package com.bank.branch.platform.performance.eval.dto;

import com.alibaba.excel.annotation.ExcelProperty;
import lombok.Data;

/**
 * 待处理任务（评价任务）导入 Excel 行模型（11 列，按业务约定顺序）。
 */
@Data
public class EvalAssignImportRow {
    @ExcelProperty("被打分员工编号")
    private String beEvalUserId;
    @ExcelProperty("被打分员工姓名")
    private String beEvalUserName;
    @ExcelProperty("被打分员工部门")
    private String beEvalDept;
    @ExcelProperty("分组部门")
    private String groupDept;
    @ExcelProperty("被打分员工标签")
    private String beEvalTag;
    @ExcelProperty("打分员工编号")
    private String evalUserId;
    @ExcelProperty("打分员工姓名")
    private String evalUserName;
    @ExcelProperty("打分员工标签")
    private String evalUserTag;
    @ExcelProperty("打分员工部门")
    private String evalUserDept;
    @ExcelProperty("权重标签")
    private String weightTag;
    @ExcelProperty("评价类型")
    private String scoreTypeText;
}
