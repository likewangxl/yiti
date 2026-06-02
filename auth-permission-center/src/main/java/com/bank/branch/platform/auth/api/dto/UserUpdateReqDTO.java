package com.bank.branch.platform.auth.api.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Size;
import lombok.Data;

/** 修改用户请求 DTO（不含密码字段） */
@Data
public class UserUpdateReqDTO {
    @Size(max = 64)
    private String username;

    @Size(max = 64)
    private String userchnname;

    @Email
    @Size(max = 128)
    private String email;

    @Size(max = 256)
    private String remark;

    /** 用户类型（字典 USER_TYPE：1-员工 / 2-虚拟员工）；不传表示不改. */
    @Size(max = 8)
    private String userType;

    /** 主机构编码（V1 单主机构语义），可选；不传表示不改 */
    @Size(max = 20)
    private String orgCode;
}
