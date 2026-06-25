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

import com.bank.branch.platform.auth.api.dto.RoleSimpleDTO;
import com.bank.branch.platform.auth.api.dto.UserRoleItemDTO;
import com.bank.branch.platform.common.web.PageResult;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
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
        dto.setUserType(user.getUserType());
        // ISENABLED 反向语义：0=启用
        dto.setEnabled(user.getIsEnabled() != null && user.getIsEnabled() == 0);

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
    public Set<String> getCandidateGroupKeys(String empId) {
        if (empId == null || empId.isEmpty()) {
            return Collections.emptySet();
        }
        // 口径对齐 AuthService.login：ROLE:{roleCode} / USER:{empId} / ORG:{mainOrgCode}
        // 三种前缀对齐 CandidateResolverService.resolveCandidates 的输出，否则 BPMN 配
        // USER/ORG 类型候选时该员工匹配不到 Flowable 候选组。
        Set<String> keys = new HashSet<>();
        for (String roleCode : getUserRoleCodes(empId)) {
            keys.add("ROLE:" + roleCode);
        }
        keys.add("USER:" + empId);
        var userOrg = userOrgMapper.selectByUserId(empId);
        if (userOrg != null && userOrg.getOrgCode() != null) {
            keys.add("ORG:" + userOrg.getOrgCode());
        }
        return keys;
    }

    @Override
    public List<String> getEmpIdsByRoleCode(String roleCode) {
        if (roleCode == null || roleCode.isEmpty()) {
            return new ArrayList<>();
        }
        List<String> empIds = userRoleMapper.selectEmpIdsByRoleCode(roleCode);
        return empIds != null ? empIds : new ArrayList<>();
    }

    @Override
    public List<String> getEmpIdsByRoleCodeAndOrg(String roleCode, String orgCode) {
        if (roleCode == null || roleCode.isEmpty() || orgCode == null || orgCode.isEmpty()) {
            return new ArrayList<>();
        }
        List<String> empIds = userRoleMapper.selectEmpIdsByRoleCodeAndOrg(roleCode, orgCode);
        return empIds != null ? empIds : new ArrayList<>();
    }

    @Override
    public List<String> getEmpIdsByOrg(String orgCode) {
        if (orgCode == null || orgCode.isEmpty()) {
            return new ArrayList<>();
        }
        List<String> empIds = userOrgMapper.selectEmpIdsByOrgCode(orgCode);
        return empIds != null ? empIds : new ArrayList<>();
    }

    @Override
    public List<UserDTO> getUsersByUsernames(List<String> usernames) {
        if (usernames == null || usernames.isEmpty()) {
            return new ArrayList<>();
        }
        List<PtUser> users = userMapper.selectByUsernames(usernames);
        if (users == null) return new ArrayList<>();
        List<UserDTO> results = new ArrayList<>();
        for (PtUser user : users) {
            UserDTO dto = getUserByEmpId(user.getUserId());
            if (dto != null) {
                dto.setUsername(user.getUsername());
                results.add(dto);
            }
        }
        return results;
    }

    /** 单次/分片 IN 查询过滤存在的用户名，不走逐人 getUserByEmpId（避免大批量 N+1）. */
    @Override
    public List<String> filterExistingUsernames(List<String> usernames) {
        if (usernames == null || usernames.isEmpty()) {
            return new ArrayList<>();
        }
        // 去重 + 分片（每片 1000，规避超大 IN 列表与预编译占位符上限）
        List<String> distinct = new ArrayList<>(new java.util.LinkedHashSet<>(usernames));
        java.util.Set<String> existing = new java.util.LinkedHashSet<>();
        final int chunk = 1000;
        for (int from = 0; from < distinct.size(); from += chunk) {
            int to = Math.min(from + chunk, distinct.size());
            List<PtUser> users = userMapper.selectByUsernames(distinct.subList(from, to));
            if (users != null) {
                for (PtUser u : users) {
                    if (u.getUsername() != null) {
                        existing.add(u.getUsername());
                    }
                }
            }
        }
        return new ArrayList<>(existing);
    }

    @Override
    public Map<String, String> mapUsernamesToEmpId(List<String> usernames) {
        Map<String, String> nameToUserId = new HashMap<>();
        if (usernames == null || usernames.isEmpty()) {
            return nameToUserId;
        }
        // 去重 + 分片（每片 1000，规避超大 IN 列表与预编译占位符上限），与 filterExistingUsernames 同款范式。
        // 仅取 selectByUsernames 返回的 USERNAME/USER_ID 两列，不回调 getUserByEmpId，避免逐人 N+1。
        List<String> distinct = new ArrayList<>(new java.util.LinkedHashSet<>(usernames));
        final int chunk = 1000;
        for (int from = 0; from < distinct.size(); from += chunk) {
            int to = Math.min(from + chunk, distinct.size());
            List<PtUser> users = userMapper.selectByUsernames(distinct.subList(from, to));
            if (users != null) {
                for (PtUser u : users) {
                    if (u.getUsername() != null) {
                        nameToUserId.put(u.getUsername(), u.getUserId());
                    }
                }
            }
        }
        return nameToUserId;
    }

    @Override
    public PageResult<UserDTO> pageUsers(String keyword, int pageNo, int pageSize) {
        // 入参归一：页码最小 1，页大小区间 [1, 100]，默认 20
        int p = pageNo < 1 ? 1 : pageNo;
        int s = pageSize < 1 ? 20 : Math.min(pageSize, 100);
        int offset = (p - 1) * s;
        // 空关键词不过滤
        String kw = (keyword == null || keyword.isBlank()) ? null : keyword.trim();
        List<PtUser> rows = userMapper.selectByKeywordPaged(kw, offset, s);
        long total = userMapper.countByKeyword(kw);
        List<UserDTO> items = new ArrayList<>(rows.size());
        for (PtUser u : rows) {
            UserDTO dto = new UserDTO();
            dto.setEmpId(u.getUserId());
            dto.setUsername(u.getUsername());
            dto.setDisplayName(u.getUserchnname());
            items.add(dto);
        }
        return PageResult.of(p, s, total, items);
    }

    @Override
    public Map<String, List<RoleSimpleDTO>> getRolesByUserIds(List<String> userIds) {
        if (userIds == null || userIds.isEmpty()) {
            return new HashMap<>();
        }
        List<UserRoleItemDTO> rows = userRoleMapper.selectRolesByUserIds(userIds);
        Map<String, List<RoleSimpleDTO>> map = new HashMap<>();
        if (rows == null) return map;
        for (UserRoleItemDTO r : rows) {
            RoleSimpleDTO dto = new RoleSimpleDTO();
            dto.setRoleId(r.getRoleId());
            dto.setRoleCode(r.getRoleCode());
            dto.setRoleChName(r.getRoleChName());
            // 按 userId 分组，computeIfAbsent 保证列表存在
            map.computeIfAbsent(r.getUserId(), k -> new ArrayList<>()).add(dto);
        }
        return map;
    }
}
