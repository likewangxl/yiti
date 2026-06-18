package com.bank.branch.platform.portal.controller.dto.addrbook;

import com.alibaba.excel.annotation.ExcelProperty;
import lombok.Data;

/**
 * 通讯录导出 Excel 行模型（列顺序与导入模板对齐，便于导出后修改再导入）。
 */
@Data
public class AddrbookExportRow {

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

    @ExcelProperty("更新时间")
    private String updatedTime;

    @ExcelProperty("状态")
    private String status;
}
