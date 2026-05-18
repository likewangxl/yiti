package com.bank.branch.platform.auth.api.dto;

import lombok.Data;
import java.time.LocalDateTime;

/** 用户列表项响应 DTO（不含密码字段） */
@Data
public class UserListItemRespDTO {
    private String userId;
    private String username;
    private String userchnname;
    private String email;
    private String remark;
    private Integer isExpired;
    private Integer isLocked;
    private Integer isEnabled;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
}
