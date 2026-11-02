package com.bank.branch.platform.governance.api.dto;

import lombok.Data;

/** 登录首屏规则。仅决定展示偏好，不授予大屏访问权限。 */
@Data
public class LoginScreenRuleDTO {
    private String roleId;
    private String target;
    private Integer priority;
    private String version;
    /** 管理端是否拥有写入该规则的完整资源权限；运行时固定为 false。 */
    private boolean editable;
}
