package com.bank.branch.platform.auth.facade;

import com.bank.branch.platform.auth.api.UserApi;
import com.bank.branch.platform.auth.api.dto.OrgDTO;
import com.bank.branch.platform.auth.api.dto.UserDTO;
import com.bank.branch.platform.auth.entity.ExtOrgInfo;
import com.bank.branch.platform.auth.entity.PtRole;
import com.bank.branch.platform.auth.entity.PtUser;
import com.bank.branch.platform.auth.mapper.OrgMapper;
import com.bank.branch.platform.auth.mapper.UserMapper;
import com.bank.branch.platform.auth.mapper.UserOrgMapper;
import com.bank.branch.platform.auth.mapper.UserRoleMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * 用户信息 Facade 实现
 * <p>
 * 实现 UserApi，提供用户信息查询能力。
 * </p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class UserFacade implements UserApi {

    private final UserMapper userMapper;
    private final UserOrgMapper userOrgMapper;
    private final OrgMapper orgMapper;
    private final UserRoleMapper userRoleMapper;

    @Override
    public UserDTO getUserByEmpId(String empId) {
        if (empId == null || empId.isEmpty()) {
            return null;
        }
        PtUser user = userMapper.selectByUserId(empId);
        if (user == null) {
            return null;
        }

        UserDTO dto = new UserDTO();
        dto.setEmpId(user.getUserId());
        dto.setUsername(user.getUsername());
        dto.setDisplayName(user.getUserchnname());

        // 获取主机构信息
        var userOrg = userOrgMapper.selectByUserId(empId);
        if (userOrg != null) {
            dto.setMainOrgCode(userOrg.getOrgCode());
            ExtOrgInfo orgInfo = orgMapper.selectByOrgCode(userOrg.getOrgCode());
            if (orgInfo != null) {
                dto.setMainOrgName(orgInfo.getOrgName());
            }
        }

        return dto;
    }

    @Override
    public String getUserName(String empId) {
        PtUser user = userMapper.selectByUserId(empId);
        return user != null ? user.getUserchnname() : null;
    }

    @Override
    public List<UserDTO> getUserByEmpIds(List<String> empIds) {
        if (empIds == null || empIds.isEmpty()) {
            return new ArrayList<>();
        }
        List<UserDTO> results = new ArrayList<>();
        for (String empId : empIds) {
            UserDTO dto = getUserByEmpId(empId);
            if (dto != null) {
                results.add(dto);
            }
        }
        return results;
    }

    @Override
    public Set<String> getUserRoleCodes(String empId) {
        if (empId == null || empId.isEmpty()) {
            return Collections.emptySet();
        }
        List<PtRole> roles = userRoleMapper.selectRolesByUserId(empId);
        if (roles == null || roles.isEmpty()) {
            return Collections.emptySet();
        }
        Set<String> codes = new HashSet<>(roles.size());
        for (PtRole r : roles) {
            if (r.getRoleCode() != null) {
                codes.add(r.getRoleCode());
            }
        }
        return codes;
    }

    @Override
    public List<String> getEmpIdsByRoleCode(String roleCode) {
        if (roleCode == null || roleCode.isEmpty()) {
            return new ArrayList<>();
        }
        List<String> empIds = userRoleMapper.selectEmpIdsByRoleCode(roleCode);
        return empIds != null ? empIds : new ArrayList<>();
    }
}
