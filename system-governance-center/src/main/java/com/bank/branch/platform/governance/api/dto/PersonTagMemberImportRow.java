package com.bank.branch.platform.governance.api.dto;

import com.alibaba.excel.annotation.ExcelProperty;
import lombok.Data;

/**
 * 业务标签「员工维度」成员导入 Excel 行模型（1 列，按维度全量覆盖）。
 * <p>按需求员工维度只需工号，不再有姓名列。</p>
 */
@Data
public class PersonTagMemberImportRow {

    /** 员工工号. */
    @ExcelProperty("工号")
    private String username;
}
