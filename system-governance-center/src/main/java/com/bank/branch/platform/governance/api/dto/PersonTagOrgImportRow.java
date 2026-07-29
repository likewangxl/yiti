package com.bank.branch.platform.governance.api.dto;

import com.alibaba.excel.annotation.ExcelProperty;
import lombok.Data;

/**
 * 业务标签「机构维度」全局导入 Excel 行模型（2 列，缺标签自动新建，追加语义）。
 * <p>Excel 按 EXT_ORG_INFO.ORG_NAME 输入机构名称，导入服务唯一匹配后仍以 DEPT_NO 落库。</p>
 */
@Data
public class PersonTagOrgImportRow {

    /** 标签名称（库中不存在时自动新建）. */
    @ExcelProperty("标签名称")
    private String tagName;

    /** 机构名称（EXT_ORG_INFO.ORG_NAME）. */
    @ExcelProperty("机构名称")
    private String orgName;
}
