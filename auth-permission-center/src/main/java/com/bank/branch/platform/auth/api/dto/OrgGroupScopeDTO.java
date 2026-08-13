package com.bank.branch.platform.auth.api.dto;

import lombok.Data;

import java.util.Set;

/**
 * 命名机构组运行时授权结果。
 * deniedReasonCode 只供内部审计/诊断使用，调用方不得向普通用户回显角色细节。
 */
@Data
public class OrgGroupScopeDTO {

    private String groupCode;
    private boolean authorized;
    private Set<String> memberOrgCodes = Set.of();
    private String deniedReasonCode;
    private Set<String> matchedRoleCodes = Set.of();
}
