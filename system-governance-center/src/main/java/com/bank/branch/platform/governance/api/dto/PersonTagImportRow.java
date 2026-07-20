package com.bank.branch.platform.governance.api.dto;

import com.alibaba.excel.annotation.ExcelProperty;
import lombok.Data;

/**
 * 人员标签全局导入 Excel 行模型（3 列）。
 * <p>姓名列仅供人工对照，入库以 PT_USER 为准，不校验一致性。</p>
 */
@Data
public class PersonTagImportRow {

    /** 标签名称（库中不存在时自动新建）. */
    @ExcelProperty("标签名称")
    private String tagName;

    /** 员工工号. */
    @ExcelProperty("工号")
    private String username;

    /** 员工姓名（仅对照，不入库）. */
    @ExcelProperty("姓名")
    private String displayName;
}
