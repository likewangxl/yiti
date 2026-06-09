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
    /** 用户类型（字典 USER_TYPE：1-员工 / 2-虚拟员工）. */
    private String userType;
    private Integer isExpired;
    private Integer isLocked;
    private Integer isEnabled;
    private Integer passWrongCount;
    private LocalDateTime createTime;
    private String createAuthor;
    private LocalDateTime updateTime;
    private String updateAuthor;
    private LocalDateTime pwdUpdateTime;
    /** 主机构编码（V1 单主机构），编辑用户时反显 */
    private String orgCode;
}
