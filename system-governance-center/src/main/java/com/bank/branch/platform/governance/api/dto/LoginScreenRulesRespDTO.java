package com.bank.branch.platform.governance.api.dto;

import lombok.Data;
import java.util.List;

/** 当前用户可用的登录首屏规则。 */
@Data
public class LoginScreenRulesRespDTO {
    private List<LoginScreenRuleDTO> rules;
    public LoginScreenRulesRespDTO(List<LoginScreenRuleDTO> rules) { this.rules = rules; }
}
