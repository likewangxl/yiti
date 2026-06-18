package com.bank.branch.platform.portal.controller.dto.addrbook;

import com.alibaba.excel.annotation.ExcelProperty;
import com.alibaba.excel.annotation.write.style.ColumnWidth;
import com.alibaba.excel.annotation.write.style.HeadStyle;
import com.alibaba.excel.enums.poi.FillPatternTypeEnum;
import lombok.Data;

/**
 * 通讯录导入 Excel 行模型（按模板列顺序）。
 *
 * <p>工号关联 PT_USER（须存在且启用）；机构名称反查 EXT_ORG_INFO（重名歧义报错）。
 * 姓名列仅供导入人核对，入库以 PT_USER 为准。负责产品不在导入范围。</p>
 *
 * <p>模板样式：列宽加大（24）、表头无填充色（fillPatternType=0=NO_FILL）。</p>
 */
@Data
@ColumnWidth(24)
@HeadStyle(fillPatternType = FillPatternTypeEnum.NO_FILL)
public class AddrbookImportRow {

    @ExcelProperty("工号")
    private String empId;

    @ExcelProperty("姓名")
    private String empName;

    @ExcelProperty("机构名称")
    private String orgName;

    @ExcelProperty("岗位")
    private String position;

    @ExcelProperty("手机")
    private String mobile;

    @ExcelProperty("邮箱")
    private String email;

    @ExcelProperty("自我描述")
    private String selfDesc;
}
