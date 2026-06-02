package com.bank.branch.platform.auth.api.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

/** 新增用户请求 DTO */
@Data
public class UserCreateReqDTO {
    /** 用户ID，可选；不传时后端自动生成 U_XXXXXXXX */
    @Size(max = 32)
    private String userId;

    /** 登录名 */
    @NotBlank(message = "登录名不能为空")
    @Size(max = 64)
    private String username;

    /** 中文姓名 */
    @NotBlank(message = "中文姓名不能为空")
    @Size(max = 64)
    private String userchnname;

    /** 邮箱 */
    @Email
    @Size(max = 128)
    private String email;

    /** 初始密码明文（service 层 BCrypt 后存库） */
    @NotBlank(message = "初始密码不能为空")
    @Size(min = 6, max = 64)
    private String initialPassword;

    /** 备注 */
    @Size(max = 256)
    private String remark;

    /** 用户类型（字典 USER_TYPE：1-员工 / 2-虚拟员工）. */
    @Size(max = 8)
    private String userType;

    /** 主机构编码（V1 单主机构语义），可选；前端按当前选中机构带入 */
    @Size(max = 20)
    private String orgCode;
}
