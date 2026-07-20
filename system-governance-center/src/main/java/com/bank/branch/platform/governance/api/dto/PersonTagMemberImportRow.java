package com.bank.branch.platform.governance.api.dto;

import com.alibaba.excel.annotation.ExcelProperty;
import lombok.Data;

/**
 * 人员标签详情页成员导入 Excel 行模型（2 列，整标签全量覆盖）。
 * <p>姓名列仅供人工对照，入库以 PT_USER 为准，不校验一致性。</p>
 */
@Data
public class PersonTagMemberImportRow {

    /** 员工工号. */
    @ExcelProperty("工号")
    private String username;

    /** 员工姓名（仅对照，不入库）. */
    @ExcelProperty("姓名")
    private String displayName;
}
