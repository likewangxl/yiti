package com.bank.branch.platform.governance.api.dto;

import com.alibaba.excel.annotation.ExcelProperty;
import lombok.Data;

/**
 * 业务标签「机构维度」成员导入 Excel 行模型（1 列，按维度全量覆盖）。
 * <p>机构编号为 EXT_ORG_INFO.DEPT_NO 口径。</p>
 */
@Data
public class PersonTagOrgMemberImportRow {

    /** 机构业务编号（EXT_ORG_INFO.DEPT_NO）. */
    @ExcelProperty("机构号")
    private String orgDeptNo;
}
