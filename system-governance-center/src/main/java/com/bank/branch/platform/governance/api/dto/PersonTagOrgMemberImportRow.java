package com.bank.branch.platform.governance.api.dto;

import com.alibaba.excel.annotation.ExcelProperty;
import lombok.Data;

/**
 * 业务标签「机构维度」成员导入 Excel 行模型（1 列，按维度全量覆盖）。
 * <p>Excel 按 EXT_ORG_INFO.ORG_NAME 输入机构名称，导入服务唯一匹配后仍以 DEPT_NO 落库。</p>
 */
@Data
public class PersonTagOrgMemberImportRow {

    /** 机构名称（EXT_ORG_INFO.ORG_NAME）. */
    @ExcelProperty("机构名称")
    private String orgName;
}
