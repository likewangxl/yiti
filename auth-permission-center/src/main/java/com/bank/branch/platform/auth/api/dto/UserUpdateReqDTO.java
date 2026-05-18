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
}
