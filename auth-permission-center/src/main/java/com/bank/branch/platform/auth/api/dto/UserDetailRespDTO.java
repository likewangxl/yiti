package com.bank.branch.platform.auth.api.dto;

import lombok.Data;
import java.time.LocalDateTime;

/** 用户详情响应 DTO（不含密码字段） */
@Data
public class UserDetailRespDTO {
    private String userId;
    private String username;
    private String userchnname;
    private String email;
    private String remark;
    private Integer isExpired;
    private Integer isLocked;
    private Integer isEnabled;
    private Integer passWrongCount;
    private LocalDateTime createTime;
    private String createAuthor;
    private LocalDateTime updateTime;
    private String updateAuthor;
    private LocalDateTime pwdUpdateTime;
}
