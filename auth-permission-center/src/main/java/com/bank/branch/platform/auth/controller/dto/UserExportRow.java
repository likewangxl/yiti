package com.bank.branch.platform.auth.controller.dto;

import com.alibaba.excel.annotation.ExcelProperty;
import com.alibaba.excel.annotation.write.style.ColumnWidth;
import com.alibaba.excel.annotation.write.style.HeadStyle;
import com.alibaba.excel.enums.poi.FillPatternTypeEnum;
import lombok.Data;

/**
 * 用户导出 Excel 行模型（用户管理「导出」按钮）。
 *
 * <p>工号取 PT_USER.USERNAME（与通讯录口径一致，USERNAME=工号）。
 * 「绑定角色」为该用户在 PT_USER_ROLE 上绑定的全部角色中文名，以「、」拼接。
 * 状态字段做正向语义转换：ISENABLED 0=启用/1=停用，ISLOCKED 1=锁定/0=正常，避免导出反语义数字。</p>
 */
@Data
@ColumnWidth(20)
@HeadStyle(fillPatternType = FillPatternTypeEnum.NO_FILL)
public class UserExportRow {

    @ExcelProperty("工号")
    private String username;

    @ExcelProperty("姓名")
    private String userchnname;

    @ExcelProperty("用户类型")
    private String userType;

    @ExcelProperty("状态")
    private String status;

    @ExcelProperty("是否锁定")
    private String locked;

    @ExcelProperty("绑定角色")
    @ColumnWidth(40)
    private String roles;

    @ExcelProperty("邮箱")
    @ColumnWidth(28)
    private String email;

    @ExcelProperty("备注")
    @ColumnWidth(30)
    private String remark;

    @ExcelProperty("创建时间")
    private String createTime;
}
