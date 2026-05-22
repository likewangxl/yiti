package com.bank.branch.platform.auth.service;

import com.bank.branch.platform.auth.api.dto.ResourceTreeNodeDTO;
import com.bank.branch.platform.auth.api.dto.ResourceUpdateReqDTO;
import com.bank.branch.platform.auth.entity.PtResource;
import com.bank.branch.platform.auth.enums.AuthErrorCode;
import com.bank.branch.platform.auth.mapper.ResourceMapper;
import com.bank.branch.platform.auth.mapper.RoleResourceMapper;
import com.bank.branch.platform.common.web.exception.BizException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * 资源管理服务
 * <p>
 * 负责 PT_RESOURCE 表的 CRUD 操作，维护资源树结构，并在变更时清除 Redis 中的全量资源缓存。
 * 资源唯一约束：resourceUrl + resourceMethod + sysCode 三元组不可重复。
 * 删除资源前须确认无子资源，并级联清理角色资源授权记录（PT_ROLE_RESOURCE）。
 * </p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ResourceService {

    private final ResourceMapper resourceMapper;
    private final RoleResourceMapper roleResourceMapper;
    private final PermissionCacheService cacheService;

    /**
     * 根据资源ID获取资源实体，不存在时抛出 AUTH-40402。
     *
     * @param resourceId 资源ID
     * @return PtResource 实体
     * @throws BizException AUTH-40402 当资源不存在时
     */
    public PtResource getEntityById(String resourceId) {
        PtResource resource = resourceMapper.selectByResourceId(resourceId);
        if (resource == null) {
            throw new BizException(AuthErrorCode.RESOURCE_NOT_FOUND.getCode(),
                    AuthErrorCode.RESOURCE_NOT_FOUND.getMessage());
        }
        return resource;
    }

    /**
     * 根据资源ID获取资源DTO，不存在时抛出 AUTH-40402。
     *
     * @param resourceId 资源ID
     * @return ResourceTreeNodeDTO（叶子节点，不含 children）
     * @throws BizException AUTH-40402 当资源不存在时
     */
    public ResourceTreeNodeDTO getById(String resourceId) {
        log.debug("[ResourceService.getById] resourceId={}", resourceId);
        return toDto(getEntityById(resourceId));
    }

    /**
     * 获取资源树，支持按 status 和 sysCode 过滤。
     * <p>
     * 从数据库获取扁平列表后，在内存中按 parentResourceId 构建树形结构。
     * 根节点定义为 parentResourceId 为 null 或空字符串的节点。
     * </p>
     *
     * @param status  资源状态过滤，null 时不过滤
     * @param sysCode 系统编号过滤，null 时不过滤
     * @return 资源树根节点列表
     */
    /**
     * 获取菜单树（仅 PT_RESOURCE.IS_MENU=1 部分，按 MENU_RANK_NO 升序）。
     * <p>用于"分配菜单"对话框：返回完整菜单层级（含分组节点 + 叶子菜单），
     * 前端 el-tree show-checkbox 渲染。状态不过滤（管理员可见禁用菜单）。</p>
     *
     * @return 菜单树根节点列表
     */
    public List<ResourceTreeNodeDTO> getMenuTree() {
        log.debug("[ResourceService.getMenuTree]");
        List<PtResource> all = resourceMapper.selectMenus();
        return buildTree(all);
    }

    public List<ResourceTreeNodeDTO> getResourceTree(Integer status, String sysCode) {
        log.debug("[ResourceService.getResourceTree] status={}, sysCode={}", status, sysCode);
        return buildTree(resourceMapper.selectAll(status, sysCode));
    }

    /**
     * 把扁平资源列表组装成树形结构。
     * <p>抽出共用方法供 {@link #getResourceTree} 与 {@link #getMenuTree} 复用。
     * 按 parentResourceId 父子关联；父节点不在当前结果集中时降级为根节点。</p>
     */
    private List<ResourceTreeNodeDTO> buildTree(List<PtResource> all) {
        // 将实体列表转换为 DTO，并按 resourceId 建立索引，方便父子关联
        Map<String, ResourceTreeNodeDTO> dtoMap = all.stream()
                .collect(Collectors.toMap(PtResource::getResourceId, this::toDto));

        List<ResourceTreeNodeDTO> roots = new ArrayList<>();
        for (PtResource res : all) {
            ResourceTreeNodeDTO dto = dtoMap.get(res.getResourceId());
            String parentId = res.getParentResourceId();
            if (parentId == null || parentId.isEmpty()) {
                roots.add(dto);
            } else {
                ResourceTreeNodeDTO parentDto = dtoMap.get(parentId);
                if (parentDto != null) {
                    if (parentDto.getChildren() == null) {
                        parentDto.setChildren(new ArrayList<>());
                    }
                    parentDto.getChildren().add(dto);
                } else {
                    // 父节点不在当前过滤结果集中（例如父节点被过滤掉），降级为根节点
                    roots.add(dto);
                }
            }
        }
        return roots;
    }

    /**
     * 新增资源。
     * <p>
     * 生成资源ID格式：RES_ + UUID前8位大写。
     * 校验 resourceUrl + resourceMethod + sysCode 三元唯一，重复时抛出 AUTH-40902。
     * 插入成功后清除全量资源缓存。
     * </p>
     *
     * @param resourceUrl      资源URL（支持 Ant 通配符）
     * @param resourceMethod   请求方法（GET/POST/PUT/DELETE/*）
     * @param menuName         菜单名称
     * @param isMenu           是否为菜单：0-是，1-否
     * @param menuEndFlag      是否为叶子菜单
     * @param menuRankNo       菜单排序号
     * @param parentResourceId 父资源ID，根节点传 null
     * @param sysCode          系统编号
     * @return 新建资源的 DTO
     * @throws BizException AUTH-40902 当 URL+Method+SysCode 已存在时
     */
    @Transactional
    public ResourceTreeNodeDTO createResource(String resourceUrl, String resourceMethod, String menuName,
            Integer isMenu, String menuEndFlag, Integer menuRankNo, String parentResourceId, String sysCode,
            String menuIconUrl) {
        log.info("[ResourceService.createResource] url={}, method={}, sysCode={}", resourceUrl, resourceMethod, sysCode);
        // 校验 URL + Method + SysCode 唯一性，防止重复注册导致鉴权歧义
        if (resourceMapper.selectByUrlAndMethod(resourceUrl, resourceMethod, sysCode) != null) {
            throw new BizException(AuthErrorCode.RESOURCE_URL_METHOD_DUPLICATE.getCode(),
                    AuthErrorCode.RESOURCE_URL_METHOD_DUPLICATE.getMessage());
        }
        String resourceId = "RES_" + UUID.randomUUID().toString().replace("-", "").substring(0, 8).toUpperCase();
        PtResource resource = new PtResource();
        resource.setResourceId(resourceId);
        resource.setResourceUrl(resourceUrl);
        resource.setResourceMethod(resourceMethod);
        resource.setMenuName(menuName);
        resource.setIsMenu(isMenu);
        resource.setMenuEndflag(menuEndFlag);
        resource.setMenuRankNo(menuRankNo);
        resource.setParentResourceId(parentResourceId);
        resource.setSysCode(sysCode);
        resource.setMenuIconUrl(menuIconUrl);
        resource.setStatus(0);
        resource.setCreateTime(LocalDateTime.now());
        resource.setUpdateTime(LocalDateTime.now());
        resourceMapper.insert(resource);
        // 新增资源后全量缓存失效，保证鉴权缓存及时反映最新资源集合
        cacheService.evictAllResourceCache();
        log.info("[ResourceService.createResource] 资源创建成功 resourceId={}", resourceId);
        return toDto(resource);
    }

    /**
     * 更新资源信息。
     * <p>
     * 若 URL 或 Method 发生变更，需重新校验唯一约束。
     * 更新后清除全量资源缓存。
     * </p>
     *
     * @param resourceId 资源ID
     * @param req        更新请求DTO（字段均为可选）
     * @return 更新后的资源DTO
     * @throws BizException AUTH-40402 资源不存在；AUTH-40902 URL+Method 冲突
     */
    @Transactional
    public ResourceTreeNodeDTO updateResource(String resourceId, ResourceUpdateReqDTO req) {
        log.info("[ResourceService.updateResource] resourceId={}", resourceId);
        PtResource existing = getEntityById(resourceId);

        // 判断 URL 或 Method 是否发生变更，变更时须重新校验唯一约束
        String newUrl = req.getResourceUrl() != null ? req.getResourceUrl() : existing.getResourceUrl();
        String newMethod = req.getResourceMethod() != null ? req.getResourceMethod() : existing.getResourceMethod();
        boolean urlOrMethodChanged = !newUrl.equals(existing.getResourceUrl())
                || !newMethod.equals(existing.getResourceMethod());

        if (urlOrMethodChanged) {
            PtResource conflict = resourceMapper.selectByUrlAndMethod(newUrl, newMethod, existing.getSysCode());
            if (conflict != null && !conflict.getResourceId().equals(resourceId)) {
                throw new BizException(AuthErrorCode.RESOURCE_URL_METHOD_DUPLICATE.getCode(),
                        AuthErrorCode.RESOURCE_URL_METHOD_DUPLICATE.getMessage());
            }
        }

        if (req.getResourceUrl() != null) existing.setResourceUrl(req.getResourceUrl());
        if (req.getResourceMethod() != null) existing.setResourceMethod(req.getResourceMethod());
        if (req.getMenuName() != null) existing.setMenuName(req.getMenuName());
        if (req.getIsMenu() != null) existing.setIsMenu(req.getIsMenu());
        if (req.getMenuEndFlag() != null) existing.setMenuEndflag(req.getMenuEndFlag());
        if (req.getMenuRankNo() != null) existing.setMenuRankNo(req.getMenuRankNo());
        if (req.getParentResourceId() != null) existing.setParentResourceId(req.getParentResourceId());
        if (req.getStatus() != null) existing.setStatus(req.getStatus());
        if (req.getMenuIconUrl() != null) existing.setMenuIconUrl(req.getMenuIconUrl());
        existing.setUpdateTime(LocalDateTime.now());

        resourceMapper.updateById(existing);
        // 资源属性变更后清除缓存，保证菜单树及鉴权数据一致
        cacheService.evictAllResourceCache();
        return toDto(existing);
    }

    /**
     * 物理删除资源。
     * <p>
     * 删除前校验是否存在子资源，有子资源时拒绝删除（防止孤立子节点）。
     * 物理删除：DELETE FROM PT_RESOURCE，并级联清理 PT_ROLE_RESOURCE 授权记录，清除资源缓存。
     * </p>
     *
     * @param resourceId 资源ID
     * @param reason     删除原因（供审计使用）
     * @throws BizException AUTH-40402 资源不存在；AUTH-40302 存在子资源时
     */
    @Transactional
    public void deleteResource(String resourceId, String reason) {
        log.info("[ResourceService.deleteResource] resourceId={}, reason={}", resourceId, reason);
        getEntityById(resourceId);
        // 存在子资源时拒绝删除，避免前端菜单树出现悬挂节点
        long childCount = resourceMapper.countChildren(resourceId);
        if (childCount > 0) {
            throw new BizException("AUTH-40302", "请先删除子资源");
        }
        // 先清角色绑定再删资源本体，避免遗留 PT_ROLE_RESOURCE 脏数据
        roleResourceMapper.deleteByResourceId(resourceId);
        resourceMapper.deleteById(resourceId);
        cacheService.evictAllResourceCache();
        log.info("[ResourceService.deleteResource] 资源已物理删除 resourceId={}", resourceId);
    }

    /**
     * 查询角色已绑定的资源ID列表，委托给 RoleResourceMapper。
     *
     * @param roleId 角色ID
     * @return 资源ID列表
     */
    public List<String> getResourceIdsByRoleId(String roleId) {
        log.debug("[ResourceService.getResourceIdsByRoleId] roleId={}", roleId);
        return roleResourceMapper.selectResourceIdsByRoleId(roleId);
    }

    /**
     * 将 PtResource 实体转换为 ResourceTreeNodeDTO。
     * children 初始化为 null，由 getResourceTree 在树构建阶段按需设置。
     *
     * @param resource 资源实体
     * @return ResourceTreeNodeDTO
     */
    private ResourceTreeNodeDTO toDto(PtResource resource) {
        ResourceTreeNodeDTO dto = new ResourceTreeNodeDTO();
        dto.setResourceId(resource.getResourceId());
        dto.setResourceUrl(resource.getResourceUrl());
        dto.setResourceMethod(resource.getResourceMethod());
        dto.setMenuName(resource.getMenuName());
        dto.setIsMenu(resource.getIsMenu());
        dto.setMenuEndFlag(resource.getMenuEndflag());
        dto.setMenuRankNo(resource.getMenuRankNo());
        dto.setStatus(resource.getStatus());
        dto.setChildren(null);
        return dto;
    }
}
