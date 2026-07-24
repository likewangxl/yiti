package com.bank.branch.platform.governance.api.dto;

import com.alibaba.excel.annotation.ExcelProperty;
import lombok.Data;

/**
 * 业务标签「员工维度」全局导入 Excel 行模型（2 列，缺标签自动新建，追加语义）。
 * <p>按需求员工维度只需工号，不再有姓名列。</p>
 */
@Data
public class PersonTagImportRow {

    /** 标签名称（库中不存在时自动新建）. */
    @ExcelProperty("标签名称")
    private String tagName;

    /** 员工工号. */
    @ExcelProperty("工号")
    private String username;
}
