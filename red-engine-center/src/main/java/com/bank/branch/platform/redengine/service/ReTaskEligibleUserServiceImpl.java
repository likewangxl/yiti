package com.bank.branch.platform.redengine.service;

import com.bank.branch.platform.auth.api.CurrentUserApi;
import com.bank.branch.platform.auth.api.UserApi;
import com.bank.branch.platform.auth.api.dto.UserDTO;
import com.bank.branch.platform.common.web.PageResult;
import com.bank.branch.platform.common.web.exception.BizException;
import com.bank.branch.platform.redengine.api.dto.ReTaskEligibleUserDTO;
import com.bank.branch.platform.redengine.api.dto.ReTaskEligibleUserPageQueryDTO;
import com.bank.branch.platform.redengine.entity.RePartyOrg;
import com.bank.branch.platform.redengine.entity.ReUserPartyMap;
import com.bank.branch.platform.redengine.mapper.RePartyOrgMapper;
import com.bank.branch.platform.redengine.mapper.ReUserPartyMapMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * 任务指定员工候选项查询实现。
 *
 * <p>员工主体和启用状态只能由 auth 的公开 {@link UserApi} 提供；红色引擎只读取自己的
 * {@code RE_USER_PARTY_MAP}/{@code RE_PARTY_ORG} 关系，既不依赖 auth 私有 Mapper，也不把
 * 管理端全局用户查询权限扩给组织审核员。查询先完成组织映射和启用状态交集，再分页，确保
 * total 与 records 口径一致。</p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ReTaskEligibleUserServiceImpl implements ReTaskEligibleUserService {

    private static final String ORG_REVIEWER_ROLE = "R_RE_ORGREV";
    private static final String SYSTEM_ADMIN_ROLE = "SYS_ADMIN";
    private static final int DEFAULT_PAGE_SIZE = 20;
    private static final int MAX_PAGE_SIZE = 100;

    private final ReUserPartyMapMapper userPartyMapMapper;
    private final RePartyOrgMapper partyOrgMapper;
    private final CurrentUserApi currentUserApi;
    private final UserApi userApi;

    /**
     * 查询任务指定员工候选项。
     *
     * <p>候选范围以红色引擎用户党组织映射为准，并与 auth 返回的已启用用户做交集。
     * 由于 auth 公开 UserApi 当前不提供“按组织且按启用状态分页”的组合查询，本实现将
     * 红色引擎映射结果批量补齐后在内存中完成过滤和分页；这样不会越权直查 auth 私有表，
     * 同时避免 disabled 用户、无党支部映射用户造成错误 total。</p>
     */
    @Override
    @Transactional(readOnly = true)
    public PageResult<ReTaskEligibleUserDTO> page(ReTaskEligibleUserPageQueryDTO query, String operatorId) {
        requireManagementAccess(operatorId);

        ReTaskEligibleUserPageQueryDTO normalized = query == null
                ? new ReTaskEligibleUserPageQueryDTO() : query;
        int pageNo = normalizePageNo(normalized.getPageNo());
        int pageSize = normalizePageSize(normalized.getPageSize());
        String keyword = normalizeText(normalized.getKeyword());

        List<ReUserPartyMap> mappings = loadMappings();
        if (mappings == null || mappings.isEmpty()) {
            return PageResult.of(pageNo, pageSize, 0L, List.of());
        }

        Map<Long, RePartyOrg> orgById = loadOrganizations();
        Map<String, Long> branchByEmployee = resolveMappedBranches(mappings, orgById);
        if (branchByEmployee.isEmpty()) {
            return PageResult.of(pageNo, pageSize, 0L, List.of());
        }

        if (normalized.getBranchId() != null) {
            branchByEmployee.entrySet().removeIf(entry -> !normalized.getBranchId().equals(entry.getValue()));
        }
        if (branchByEmployee.isEmpty()) {
            return PageResult.of(pageNo, pageSize, 0L, List.of());
        }

        List<String> employeeIds = branchByEmployee.keySet().stream().sorted().toList();
        List<UserDTO> users = loadEnabledUsers(employeeIds);
        Map<String, UserDTO> userById = users.stream()
                .filter(Objects::nonNull)
                .filter(user -> hasText(user.getEmpId()))
                .collect(Collectors.toMap(UserDTO::getEmpId, Function.identity(), (first, ignored) -> first,
                        LinkedHashMap::new));

        List<ReTaskEligibleUserDTO> candidates = new ArrayList<>();
        for (Map.Entry<String, Long> entry : branchByEmployee.entrySet()) {
            UserDTO user = userById.get(entry.getKey());
            if (user == null || !Boolean.TRUE.equals(user.getEnabled())) {
                continue;
            }
            if (keyword != null && !matches(user, keyword)) {
                continue;
            }
            RePartyOrg branch = orgById.get(entry.getValue());
            ReTaskEligibleUserDTO dto = new ReTaskEligibleUserDTO();
            dto.setEmployeeId(user.getEmpId());
            dto.setUsername(user.getUsername());
            dto.setDisplayName(user.getDisplayName());
            dto.setBranchId(entry.getValue());
            dto.setBranchName(branch == null ? null : branch.getOrgName());
            candidates.add(dto);
        }

        candidates.sort(Comparator
                .comparing(ReTaskEligibleUserDTO::getDisplayName,
                        Comparator.nullsLast(String.CASE_INSENSITIVE_ORDER))
                .thenComparing(ReTaskEligibleUserDTO::getUsername,
                        Comparator.nullsLast(String.CASE_INSENSITIVE_ORDER))
                .thenComparing(ReTaskEligibleUserDTO::getEmployeeId,
                        Comparator.nullsLast(String.CASE_INSENSITIVE_ORDER)));

        long total = candidates.size();
        long offset = ((long) pageNo - 1L) * pageSize;
        if (offset >= total) {
            return PageResult.of(pageNo, pageSize, total, List.of());
        }
        int from = Math.toIntExact(offset);
        int to = (int) Math.min(total, offset + pageSize);
        return PageResult.of(pageNo, pageSize, total, candidates.subList(from, to));
    }

    /** 管理端候选项同任务管理入口使用相同的服务层角色守卫。 */
    private void requireManagementAccess(String operatorId) {
        if (!hasText(operatorId)) {
            throw new BizException("RE-40301", "当前用户上下文缺失");
        }
        String currentEmpId = currentUserApi.getCurrentEmpId();
        if (!hasText(currentEmpId) || !operatorId.trim().equals(currentEmpId.trim())) {
            throw new BizException("RE-40301", "当前用户与操作人不匹配");
        }
        Set<String> roleCodes = currentUserApi.getCurrentRoleCodes();
        boolean permitted = currentUserApi.isSystemAdmin() || (roleCodes != null && roleCodes.stream()
                .filter(Objects::nonNull)
                .map(String::trim)
                .anyMatch(role -> SYSTEM_ADMIN_ROLE.equalsIgnoreCase(role)
                        || ORG_REVIEWER_ROLE.equalsIgnoreCase(role)));
        if (!permitted) {
            throw new BizException("RE-40302", "无权查询任务可选员工");
        }
    }

    /** 一次性读取本域组织，避免按员工映射逐条查询父级组织。 */
    private Map<Long, RePartyOrg> loadOrganizations() {
        try {
            List<RePartyOrg> organizations = partyOrgMapper.selectList(null);
            if (organizations == null) {
                throw new BizException("RE-50014", "任务可选员工查询失败");
            }
            return organizations.stream()
                    .filter(Objects::nonNull)
                    .filter(org -> org.getId() != null)
                    .collect(Collectors.toMap(RePartyOrg::getId, Function.identity(), (first, ignored) -> first,
                            HashMap::new));
        } catch (BizException ex) {
            throw ex;
        } catch (RuntimeException ex) {
            log.warn("任务可选员工查询读取红色引擎组织失败", ex);
            throw new BizException("RE-50014", "任务可选员工查询失败", ex);
        }
    }

    /** 读取本域用户党组织映射；数据访问异常时拒绝候选查询而不放宽范围。 */
    private List<ReUserPartyMap> loadMappings() {
        try {
            List<ReUserPartyMap> mappings = userPartyMapMapper.selectList(null);
            if (mappings == null) {
                throw new BizException("RE-50014", "任务可选员工查询失败");
            }
            return mappings;
        } catch (BizException ex) {
            throw ex;
        } catch (RuntimeException ex) {
            log.warn("任务可选员工查询读取用户党组织映射失败", ex);
            throw new BizException("RE-50014", "任务可选员工查询失败", ex);
        }
    }

    /**
     * 将用户党组织映射解析为支部；环路、缺失组织和非支部根节点均 fail-close，避免候选越权。
     */
    private Map<String, Long> resolveMappedBranches(List<ReUserPartyMap> mappings,
                                                     Map<Long, RePartyOrg> orgById) {
        Map<String, Long> result = new HashMap<>();
        for (ReUserPartyMap mapping : mappings) {
            if (mapping == null || !hasText(mapping.getUserId()) || mapping.getPartyOrgId() == null) {
                continue;
            }
            Long branchId = resolveBranchId(mapping.getPartyOrgId(), orgById);
            if (branchId != null) {
                result.putIfAbsent(mapping.getUserId().trim(), branchId);
            }
        }
        return result;
    }

    /** 从映射节点向上解析 orgLevel=2 的党支部，并拒绝组织树环路。 */
    private Long resolveBranchId(Long partyOrgId, Map<Long, RePartyOrg> orgById) {
        Set<Long> visited = new HashSet<>();
        Long current = partyOrgId;
        while (current != null && visited.add(current)) {
            RePartyOrg org = orgById.get(current);
            if (org == null) {
                return null;
            }
            if (Integer.valueOf(2).equals(org.getOrgLevel())) {
                return org.getId();
            }
            current = org.getParentId();
        }
        return null;
    }

    /** 公开 UserApi 异常或返回 null 均不应被当成空候选放行。 */
    private List<UserDTO> loadEnabledUsers(List<String> employeeIds) {
        try {
            List<UserDTO> users = userApi.getUserByEmpIds(employeeIds);
            if (users == null) {
                throw new BizException("RE-50014", "任务可选员工查询失败");
            }
            return users;
        } catch (BizException ex) {
            throw ex;
        } catch (RuntimeException ex) {
            log.warn("任务可选员工查询调用 auth UserApi 失败", ex);
            throw new BizException("RE-50014", "任务可选员工查询失败", ex);
        }
    }

    private boolean matches(UserDTO user, String keyword) {
        return contains(user.getEmpId(), keyword)
                || contains(user.getUsername(), keyword)
                || contains(user.getDisplayName(), keyword);
    }

    private boolean contains(String value, String keyword) {
        return value != null && value.toLowerCase(Locale.ROOT)
                .contains(keyword.toLowerCase(Locale.ROOT));
    }

    private int normalizePageNo(long value) {
        return value < 1 ? 1 : (int) Math.min(Integer.MAX_VALUE, value);
    }

    private int normalizePageSize(long value) {
        return value < 1 ? DEFAULT_PAGE_SIZE : (int) Math.min(MAX_PAGE_SIZE, value);
    }

    private String normalizeText(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    private boolean hasText(String value) {
        return value != null && !value.isBlank();
    }
}
