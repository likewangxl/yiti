package com.bank.branch.platform.auth.facade;

import com.bank.branch.platform.auth.api.OrgGroupApi;
import com.bank.branch.platform.auth.api.dto.OrgGroupDTO;
import com.bank.branch.platform.auth.api.dto.OrgGroupRoleCheckDTO;
import com.bank.branch.platform.auth.api.dto.OrgGroupScopeDTO;
import com.bank.branch.platform.auth.api.dto.OrgProfileDTO;
import com.bank.branch.platform.auth.service.OrgGroupService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.Collection;
import java.util.Map;
import java.util.Set;

/**
 * 机构组对外 Facade。
 *
 * <p>Facade 只做模块契约转发，跨模块调用方不需要感知内部 Entity、Mapper 或校验实现。</p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class OrgGroupFacade implements OrgGroupApi {

    private final OrgGroupService orgGroupService;

    /** {@inheritDoc} */
    @Override
    public OrgGroupDTO getGroup(String groupCode) {
        return orgGroupService.getGroup(groupCode);
    }

    /** {@inheritDoc} */
    @Override
    public Set<String> listActiveMemberCodes(String groupCode) {
        return orgGroupService.listActiveMemberCodes(groupCode);
    }

    /** {@inheritDoc} */
    @Override
    public OrgGroupScopeDTO resolveAuthorizedScope(String empId, String groupCode,
                                                   Collection<String> allowedRoleCodes) {
        return orgGroupService.resolveAuthorizedScope(empId, groupCode, allowedRoleCodes);
    }

    /** {@inheritDoc} */
    @Override
    public OrgGroupRoleCheckDTO checkRoleBindings(String groupCode, Collection<String> roleCodes) {
        return orgGroupService.checkRoleBindings(groupCode, roleCodes);
    }

    /** {@inheritDoc} */
    @Override
    public Map<String, OrgProfileDTO> getActiveProfiles(Collection<String> orgCodes) {
        return orgGroupService.getActiveProfiles(orgCodes);
    }
}
