package com.bank.branch.platform.governance.api.dto;

import com.alibaba.excel.annotation.ExcelProperty;
import lombok.Data;

/**
 * 业务标签「机构维度」全局导入 Excel 行模型（2 列，缺标签自动新建，追加语义）。
 * <p>机构编号为 EXT_ORG_INFO.DEPT_NO 口径。</p>
 */
@Data
public class PersonTagOrgImportRow {

    /** 标签名称（库中不存在时自动新建）. */
    @ExcelProperty("标签名称")
    private String tagName;

    /** 机构业务编号（EXT_ORG_INFO.DEPT_NO）. */
    @ExcelProperty("机构号")
    private String orgDeptNo;
}
