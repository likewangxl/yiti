package com.bank.branch.platform.governance.api.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

/** 登录首屏规则写入请求。 */
@Data
public class LoginScreenRuleSaveReqDTO {
    @NotBlank
    private String target;
    @NotNull
    private Integer priority;
    private String expectedVersion;
    @NotBlank
    @Size(max = 500)
    private String reason;
}
