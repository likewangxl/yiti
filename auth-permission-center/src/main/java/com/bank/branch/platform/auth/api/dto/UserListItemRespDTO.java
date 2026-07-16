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
    /** 部门名称（EXT_USER_ORG ⋈ EXT_ORG_INFO 的 ORG_NAME，多机构以「、」连接；无归属为 null） */
    private String deptName;
    /** 用户类型（字典 USER_TYPE：1-员工 / 2-虚拟员工）. */
    private String userType;
    private Integer isExpired;
    private Integer isLocked;
    private Integer isEnabled;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
}
