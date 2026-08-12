package com.bank.branch.platform.auth.api.dto;

import lombok.Data;

import java.util.Set;

/** 屏级角色白名单与机构组绑定关系校验结果。 */
@Data
public class OrgGroupRoleCheckDTO {

    private String groupCode;
    private Set<String> validRoleCodes = Set.of();
    private Set<String> unboundRoleCodes = Set.of();
    private Set<String> invalidRoleCodes = Set.of();
    private boolean satisfiable;
}
