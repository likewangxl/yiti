package com.bank.branch.platform.auth.facade;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.bank.branch.platform.auth.api.UserApi;
import com.bank.branch.platform.auth.api.dto.OrgDTO;
import com.bank.branch.platform.auth.api.dto.UserDTO;
import com.bank.branch.platform.auth.entity.ExtOrgInfo;
import com.bank.branch.platform.auth.entity.ExtUserOrg;
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
        // 单次批量 IN 查询取用户，不逐人调用 getUserByEmpId（避免 N+1，动态指标查询「员工维度」等
        // 大批量场景每人 3 次查询会被放大成数百上千次）
        List<PtUser> users = userMapper.selectByUserIds(empIds);
        if (users == null || users.isEmpty()) {
            return new ArrayList<>();
        }
        Map<String, UserDTO> dtoByUserId = new HashMap<>();
        for (UserDTO dto : buildUserDtos(users)) {
            dtoByUserId.put(dto.getEmpId(), dto);
        }
        // 按入参 empIds 顺序输出，保持与旧的逐人实现相同的返回顺序，兼容既有调用方
        List<UserDTO> results = new ArrayList<>();
        for (String empId : empIds) {
            UserDTO dto = dtoByUserId.get(empId);
            if (dto != null) {
                results.add(dto);
            }
        }
        return results;
    }

    /**
     * 按一批 {@link PtUser} 批量装配 {@link UserDTO}（含主机构编码/名称），字段映射口径
     * 与单用户版 {@link #getUserByEmpId(String)} 逐一一致，供 {@link #getUserByEmpIds(List)}
     * 和 {@link #getUsersByUsernames(List)} 复用，避免各自逐人回查机构造成 N+1。
     *
     * @param users 已批量查得的用户实体列表（非空）
     * @return 装配好的 UserDTO 列表，顺序与入参 users 一致
     */
    private List<UserDTO> buildUserDtos(List<PtUser> users) {
        List<String> userIds = new ArrayList<>();
        for (PtUser u : users) {
            if (u.getUserId() != null) {
                userIds.add(u.getUserId());
            }
        }

        // 批量查主机构关联（每人至多一条），同一 userId 只取首条命中，等价于单用户版 LIMIT 1 语义
        Map<String, String> orgCodeByUserId = new HashMap<>();
        if (!userIds.isEmpty()) {
            List<ExtUserOrg> userOrgs = userOrgMapper.selectByUserIds(userIds);
            if (userOrgs != null) {
                for (ExtUserOrg uo : userOrgs) {
                    orgCodeByUserId.putIfAbsent(uo.getUserId(), uo.getOrgCode());
                }
            }
        }

        // 去重机构编码后批量查机构名称，避免同机构重复查询
        Set<String> orgCodes = new HashSet<>(orgCodeByUserId.values());
        Map<String, String> orgNameByOrgCode = new HashMap<>();
        if (!orgCodes.isEmpty()) {
            List<ExtOrgInfo> orgInfos = orgMapper.selectByOrgCodes(orgCodes);
            if (orgInfos != null) {
                for (ExtOrgInfo o : orgInfos) {
                    orgNameByOrgCode.put(o.getOrgCode(), o.getOrgName());
                }
            }
        }

        List<UserDTO> results = new ArrayList<>();
        for (PtUser u : users) {
            UserDTO dto = new UserDTO();
            dto.setEmpId(u.getUserId());
            dto.setUsername(u.getUsername());
            dto.setDisplayName(u.getUserchnname());
            dto.setUserType(u.getUserType());
            // ISENABLED 反向语义：0=启用
            dto.setEnabled(u.getIsEnabled() != null && u.getIsEnabled() == 0);

            String orgCode = orgCodeByUserId.get(u.getUserId());
            if (orgCode != null) {
                dto.setMainOrgCode(orgCode);
                dto.setMainOrgName(orgNameByOrgCode.get(orgCode));
            }
            results.add(dto);
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
        // 单次批量 IN 查询取用户，不逐人调用 getUserByEmpId（避免 N+1，理由同 getUserByEmpIds）
        List<PtUser> users = userMapper.selectByUsernames(usernames);
        if (users == null || users.isEmpty()) {
            return new ArrayList<>();
        }
        return buildUserDtos(users);
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
    public Map<String, String> mapEmpIdsToUsername(List<String> empIds) {
        Map<String, String> idToName = new HashMap<>();
        if (empIds == null || empIds.isEmpty()) {
            return idToName;
        }
        // 去重 + 分片（每片 1000，规避超大 IN 列表与预编译占位符上限），与 mapUsernamesToEmpId 同款范式。
        // 仅取 selectByUserIds 返回的 USER_ID/USERNAME 两列，不回调 getUserByEmpId，避免逐人 N+1。
        List<String> distinct = new ArrayList<>(new java.util.LinkedHashSet<>(empIds));
        final int chunk = 1000;
        for (int from = 0; from < distinct.size(); from += chunk) {
            int to = Math.min(from + chunk, distinct.size());
            List<PtUser> users = userMapper.selectByUserIds(distinct.subList(from, to));
            if (users != null) {
                for (PtUser u : users) {
                    if (u.getUserId() != null) {
                        idToName.put(u.getUserId(), u.getUsername());
                    }
                }
            }
        }
        return idToName;
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
        List<UserDTO> items = buildUserDtos(rows);
        return PageResult.of(p, s, total, items);
    }

    @Override
    public List<UserDTO> findUsersByUsernameAndDisplayName(String username, String displayName) {
        String normalizedUsername = normalizeText(username);
        String normalizedDisplayName = normalizeText(displayName);
        if (normalizedUsername == null && normalizedDisplayName == null) {
            return Collections.emptyList();
        }

        LambdaQueryWrapper<PtUser> wrapper = new LambdaQueryWrapper<PtUser>()
                .like(normalizedUsername != null, PtUser::getUsername, normalizedUsername)
                .like(normalizedDisplayName != null, PtUser::getUserchnname, normalizedDisplayName)
                .orderByAsc(PtUser::getUserId);
        List<PtUser> rows = userMapper.selectList(wrapper);
        if (rows == null || rows.isEmpty()) {
            return Collections.emptyList();
        }
        List<UserDTO> result = new ArrayList<>(rows.size());
        for (PtUser row : rows) {
            UserDTO dto = new UserDTO();
            dto.setEmpId(row.getUserId());
            dto.setUsername(row.getUsername());
            dto.setDisplayName(row.getUserchnname());
            result.add(dto);
        }
        return result;
    }

    /** 将查询文本去除首尾空白，空白串按未传处理。 */
    private String normalizeText(String value) {
        return value == null || value.isBlank() ? null : value.trim();
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
