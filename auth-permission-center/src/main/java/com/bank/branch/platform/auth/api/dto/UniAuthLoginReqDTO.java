package com.bank.branch.platform.auth.api.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

/**
 * 统一认证登录请求 DTO（开发期 mock 直登）。
 * <p>仅接收 userDomainName（AD 域账号 / 工号），不传密码 —
 * 由后端调 S120030044 统一认证查授权后直接建 session。
 * <p>生产环境应改为完整 UIAS 重定向链路（行内 Spring Security UIAS filter）。
 */
@Data
public class UniAuthLoginReqDTO {

    @NotBlank(message = "userDomainName 必填")
    private String userDomainName;
}
