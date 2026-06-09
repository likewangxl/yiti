package com.bank.branch.platform.auth.service;

import com.bank.branch.platform.auth.api.dto.OrgCreateReqDTO;
import com.bank.branch.platform.auth.api.dto.OrgDTO;
import com.bank.branch.platform.auth.api.dto.OrgTreeNodeDTO;
import com.bank.branch.platform.auth.api.dto.OrgUpdateReqDTO;
import com.bank.branch.platform.auth.api.dto.OrgUserDTO;
import com.bank.branch.platform.auth.api.dto.RoleSimpleDTO;
import com.bank.branch.platform.auth.api.dto.UserRoleItemDTO;
import com.bank.branch.platform.auth.entity.ExtOrgInfo;
import com.bank.branch.platform.auth.entity.ExtUserOrg;
import com.bank.branch.platform.auth.entity.PtUser;
import com.bank.branch.platform.auth.enums.AuthErrorCode;
import com.bank.branch.platform.auth.mapper.OrgMapper;
import com.bank.branch.platform.auth.mapper.UserMapper;
import com.bank.branch.platform.auth.mapper.UserOrgMapper;
import com.bank.branch.platform.auth.mapper.UserRoleMapper;
import com.bank.branch.platform.common.web.PageResult;
import com.bank.branch.platform.common.web.exception.BizException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 组织机构服务
 * 提供机构查询、子树遍历等功能
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class OrgService {

    private final OrgMapper orgMapper;
    private final UserOrgMapper userOrgMapper;
    private final UserMapper userMapper;
    private final UserRoleMapper userRoleMapper;

    /** 新增机构。orgCode 不能与已有重复；orgLevel 不传时按父级 + 1 自动计算。 */
    @org.springframework.transaction.annotation.Transactional
    public OrgDTO createOrg(OrgCreateReqDTO req) {
        // 机构编码：前端不传时后端自增（现有最大数字编码 +1）；传了则沿用并校验重复
        String orgCode = req.getOrgCode();
        if (orgCode == null || orgCode.isBlank()) {
            Long maxCode = orgMapper.selectMaxNumericOrgCode();
            orgCode = String.valueOf((maxCode == null ? 0L : maxCode) + 1);
        } else if (orgMapper.selectByOrgCode(orgCode) != null) {
            throw new BizException("AUTH-40902", "机构编码已存在");
        }
        Integer level = req.getOrgLevel();
        // 未传 orgLevel 时按父级 + 1 计算；无父级则 level=1
        if (level == null) {
            if (req.getPId() != null && !req.getPId().isBlank()) {
                ExtOrgInfo parent = orgMapper.selectByOrgCode(req.getPId());
                level = parent != null && parent.getOrgLevel() != null ? parent.getOrgLevel() + 1 : 2;
            } else {
                level = 1;
            }
        }
        ExtOrgInfo e = new ExtOrgInfo();
        e.setOrgCode(orgCode);
        e.setOrgName(req.getOrgName());
        e.setDeptNo(req.getDeptNo());
        e.setOrgLevel(level);
        e.setPId(req.getPId() == null ? "" : req.getPId());
        e.setOrganState(0);
        e.setCreateTime(java.time.LocalDateTime.now());
        orgMapper.insert(e);
        log.info("[OrgService.createOrg] orgCode={} pId={} level={} deptNo={}", e.getOrgCode(), e.getPId(), level, e.getDeptNo());
        return getOrg(e.getOrgCode());
    }

    /** 更新机构名称（不支持改 orgCode / pId / level，避免破坏树结构） */
    @org.springframework.transaction.annotation.Transactional
    public OrgDTO updateOrg(String orgCode, OrgUpdateReqDTO req) {
        ExtOrgInfo e = orgMapper.selectByOrgCode(orgCode);
        if (e == null) throw new BizException("AUTH-40404", "机构不存在");
        if (req.getOrgName() != null && !req.getOrgName().isBlank()) e.setOrgName(req.getOrgName());
        // 状态变更：禁用(1)前校验机构下无用户，否则拒绝；启用(0)无需校验
        if (req.getOrganState() != null) {
            if (req.getOrganState() == 1) {
                long userCnt = userOrgMapper.countUsersByOrgCode(orgCode, null);
                if (userCnt > 0) {
                    throw new BizException("AUTH-40303", "该机构下还有 " + userCnt + " 个用户，请先迁移用户再禁用");
                }
            }
            e.setOrganState(req.getOrganState());
        }
        orgMapper.updateById(e);
        log.info("[OrgService.updateOrg] orgCode={} newName={} organState={}", orgCode, e.getOrgName(), e.getOrganState());
        return getOrg(orgCode);
    }

    /** 删除机构。前置校验：无下级机构 + 无用户绑定。否则抛业务错。 */
    @org.springframework.transaction.annotation.Transactional
    public void deleteOrg(String orgCode) {
        ExtOrgInfo e = orgMapper.selectByOrgCode(orgCode);
        if (e == null) throw new BizException("AUTH-40404", "机构不存在");
        // 1) 下级机构存在 → 拒绝
        List<ExtOrgInfo> children = orgMapper.selectChildren(orgCode);
        if (children != null && !children.isEmpty()) {
            throw new BizException("AUTH-40303", "该机构下还有 " + children.size() + " 个子机构，请先删除子机构");
        }
        // 2) 有用户归属 → 拒绝
        long userCnt = userOrgMapper.countUsersByOrgCode(orgCode, null);
        if (userCnt > 0) {
            throw new BizException("AUTH-40303", "该机构下还有 " + userCnt + " 个用户，请先迁移用户");
        }
        orgMapper.deleteById(orgCode);
        log.info("[OrgService.deleteOrg] orgCode={} 已删除", orgCode);
    }

    /**
     * 根据机构编码查询机构信息
     *
     * @param orgCode 机构编码
     * @return 机构DTO，不存在时返回 null
     */
    public OrgDTO getOrg(String orgCode) {
        ExtOrgInfo org = orgMapper.selectByOrgCode(orgCode);
        if (org == null) {
            return null;
        }
        return toDto(org);
    }

    /**
     * 获取机构子树（含自身），递归遍历子机构
     *
     * @param orgCode 根机构编码
     * @return 子树中所有机构的DTO列表（含自身）
     */
    public List<OrgDTO> getOrgSubtree(String orgCode) {
        ExtOrgInfo root = orgMapper.selectByOrgCode(orgCode);
        if (root == null) return List.of();
        List<OrgDTO> result = new ArrayList<>();
        collectSubtree(root, result);
        return result;
    }

    /**
     * 获取机构子树编码集合（含自身），高频调用
     * 调用方应通过 PermissionCacheService 缓存结果
     *
     * @param orgCode 根机构编码
     * @return 子树中所有机构编码的Set（含自身）
     */
    public Set<String> getOrgSubtreeCodes(String orgCode) {
        ExtOrgInfo root = orgMapper.selectByOrgCode(orgCode);
        if (root == null) return Set.of();
        Set<String> codes = new LinkedHashSet<>();
        collectSubtreeCodes(root, codes);
        return codes;
    }

    /**
     * 获取用户主机构信息（V1：单用户单主机构）
     *
     * @param empId 用户工号
     * @return 用户主机构DTO
     * @throws BizException AUTH-40403 用户无机构记录
     */
    public OrgDTO getUserMainOrg(String empId) {
        ExtUserOrg userOrg = userOrgMapper.selectByUserId(empId);
        if (userOrg == null) {
            throw new BizException(AuthErrorCode.USER_NOT_FOUND.getCode(),
                AuthErrorCode.USER_NOT_FOUND.getMessage());
        }
        return getOrg(userOrg.getOrgCode());
    }

    /**
     * 模糊搜索机构（实时查库，不缓存）
     *
     * @param keyword 搜索关键字
     * @param limit   最大返回条数
     * @return 匹配的机构列表
     */
    public List<OrgDTO> searchOrgs(String keyword, int limit) {
        return orgMapper.searchByKeyword(keyword, limit)
            .stream().map(this::toDto).collect(Collectors.toList());
    }

    /**
     * 根据机构编码查询该机构下的所有用户（G.2）
     *
     * @param orgCode 机构编码
     * @return 机构下的用户列表
     */
    public List<OrgUserDTO> getOrgUsers(String orgCode) {
        log.debug("[OrgService.getOrgUsers] orgCode={}", orgCode);
        List<PtUser> users = userMapper.selectByOrgCode(orgCode);
        return buildOrgUserDtoList(users);
    }

    /**
     * 分页查询机构下的用户（G.2）
     *
     * @param orgCode 机构编码
     * @param keyword 关键字（工号/姓名）
     * @param pageNo 页码
     * @param pageSize 每页条数
     * @return 分页结果
     */
    public PageResult<OrgUserDTO> getOrgUsers(String orgCode, String keyword, int pageNo, int pageSize) {
        log.debug("[OrgService.getOrgUsers] orgCode={}, keyword={}, pageNo={}, pageSize={}", orgCode, keyword, pageNo, pageSize);
        int offset = (pageNo - 1) * pageSize;

        List<PtUser> users = userMapper.selectOrgUsersByPage(orgCode, keyword, offset, pageSize);
        long total = userMapper.countOrgUsers(orgCode, keyword);

        List<OrgUserDTO> records = buildOrgUserDtoList(users);
        return PageResult.of(pageNo, pageSize, total, records);
    }

    /**
     * 获取完整组织树（树形结构，含层级关系）
     *
     * @return 根节点列表
     */
    public List<ExtOrgInfo> getAllOrgsFlat() {
        return orgMapper.selectAll();
    }

    // ── 私有方法 ──────────────────────────────────────────────────

    /** 递归收集子树节点（从实体出发，避免重复查根节点） */
    private void collectSubtree(ExtOrgInfo org, List<OrgDTO> result) {
        result.add(toDto(org));
        List<ExtOrgInfo> children = orgMapper.selectChildren(org.getOrgCode());
        for (ExtOrgInfo child : children) {
            collectSubtree(child, result);
        }
    }

    /** 递归收集子树编码（从实体出发，避免重复查根节点） */
    private void collectSubtreeCodes(ExtOrgInfo org, Set<String> codes) {
        codes.add(org.getOrgCode());
        List<ExtOrgInfo> children = orgMapper.selectChildren(org.getOrgCode());
        for (ExtOrgInfo child : children) {
            collectSubtreeCodes(child, codes);
        }
    }

    /** 将实体转换为DTO */
    private OrgDTO toDto(ExtOrgInfo org) {
        OrgDTO dto = new OrgDTO();
        dto.setOrgCode(org.getOrgCode());
        dto.setOrgName(org.getOrgName());
        dto.setDeptNo(org.getDeptNo());
        dto.setOrgLevel(org.getOrgLevel());
        dto.setParentOrgCode(org.getPId());
        dto.setOrganState(org.getOrganState());
        return dto;
    }

    /**
     * 将 PtUser 列表转换为 OrgUserDTO 列表，并批量补充角色信息。
     *
     * @param users 用户实体列表
     * @return OrgUserDTO 列表（含 roles）
     */
    private List<OrgUserDTO> buildOrgUserDtoList(List<PtUser> users) {
        Map<String, List<RoleSimpleDTO>> rolesMap = loadRolesMap(users);
        return users.stream().map(u -> {
            OrgUserDTO dto = new OrgUserDTO();
            dto.setEmpId(u.getUserId());
            dto.setUsername(u.getUsername());
            dto.setDisplayName(u.getUserchnname());
            dto.setEmail(u.getEmail());
            dto.setRemark(u.getRemark());
            dto.setIsEnabled(u.getIsEnabled());
            dto.setIsLocked(u.getIsLocked());
            dto.setCreateTime(u.getCreateTime());
            dto.setRoles(rolesMap.getOrDefault(u.getUserId(), List.of()));
            return dto;
        }).collect(Collectors.toList());
    }

    /**
     * 批量查询用户角色并按 userId 分组。
     *
     * @param users 用户实体列表
     * @return userId → 角色列表的映射
     */
    private Map<String, List<RoleSimpleDTO>> loadRolesMap(List<PtUser> users) {
        if (users.isEmpty()) {
            return Map.of();
        }
        List<String> userIds = users.stream().map(PtUser::getUserId).collect(Collectors.toList());
        List<UserRoleItemDTO> rolesList = userRoleMapper.selectRolesByUserIds(userIds);
        return rolesList.stream().collect(Collectors.groupingBy(
                UserRoleItemDTO::getUserId,
                Collectors.mapping(r -> {
                    RoleSimpleDTO rd = new RoleSimpleDTO();
                    rd.setRoleId(r.getRoleId());
                    rd.setRoleCode(r.getRoleCode());
                    rd.setRoleChName(r.getRoleChName());
                    return rd;
                }, Collectors.toList())
        ));
    }

    /**
     * 获取完整的组织机构树（从数据库一次性加载所有机构，在内存中构建层级）
     *
     * @return 顶层机构节点列表（每个节点包含完整子树）
     */
    public List<OrgTreeNodeDTO> getOrgTree() {
        List<ExtOrgInfo> all = orgMapper.selectAll();
        // 按 orgCode 分组，方便快速查找
        Map<String, OrgTreeNodeDTO> nodeMap = all.stream()
            .collect(Collectors.toMap(ExtOrgInfo::getOrgCode, this::toTreeNode));
        List<OrgTreeNodeDTO> roots = new ArrayList<>();
        for (ExtOrgInfo org : all) {
            OrgTreeNodeDTO node = nodeMap.get(org.getOrgCode());
            if (org.getPId() == null || !nodeMap.containsKey(org.getPId())) {
                // 没有父节点或父节点不在当前数据集中 → 顶层节点
                roots.add(node);
            } else {
                OrgTreeNodeDTO parent = nodeMap.get(org.getPId());
                if (parent.getChildren() == null) {
                    parent.setChildren(new ArrayList<>());
                }
                parent.getChildren().add(node);
            }
        }
        return roots;
    }

    /**
     * 获取机构子树（树形结构）
     * 加载全量机构后在内存中构建指定根节点的子树
     *
     * @param orgCode 根机构编码
     * @return 根节点列表（通常只有1个元素：当前用户主机构为根的子树）
     */
    public List<OrgTreeNodeDTO> getOrgSubtreeAsTree(String orgCode) {
        ExtOrgInfo root = orgMapper.selectByOrgCode(orgCode);
        if (root == null) return List.of();
        // 获取子树编码集合
        Set<String> subtreeCodes = getOrgSubtreeCodes(orgCode);
        // 加载全量机构后在内存中过滤并构建树
        List<ExtOrgInfo> all = orgMapper.selectAll();
        Map<String, OrgTreeNodeDTO> nodeMap = all.stream()
            .filter(o -> subtreeCodes.contains(o.getOrgCode()))
            .collect(Collectors.toMap(ExtOrgInfo::getOrgCode, this::toTreeNode));
        // 根节点应只有当前 orgCode 对应的节点
        List<OrgTreeNodeDTO> roots = new ArrayList<>();
        for (ExtOrgInfo org : all) {
            if (!subtreeCodes.contains(org.getOrgCode())) continue;
            OrgTreeNodeDTO node = nodeMap.get(org.getOrgCode());
            if (org.getPId() == null || !subtreeCodes.contains(org.getPId())) {
                // 没有父节点或父节点不在子树中 → 子树根节点
                roots.add(node);
            } else {
                OrgTreeNodeDTO parent = nodeMap.get(org.getPId());
                if (parent.getChildren() == null) {
                    parent.setChildren(new ArrayList<>());
                }
                parent.getChildren().add(node);
            }
        }
        return roots;
    }

    /** 将 ExtOrgInfo 实体转换为 OrgTreeNodeDTO（不含 children） */
    private OrgTreeNodeDTO toTreeNode(ExtOrgInfo org) {
        OrgTreeNodeDTO node = new OrgTreeNodeDTO();
        node.setOrgCode(org.getOrgCode());
        node.setOrgName(org.getOrgName());
        node.setDeptNo(org.getDeptNo());
        node.setOrgLevel(org.getOrgLevel());
        node.setParentOrgCode(org.getPId());
        node.setOrganState(org.getOrganState());
        return node;
    }
}
