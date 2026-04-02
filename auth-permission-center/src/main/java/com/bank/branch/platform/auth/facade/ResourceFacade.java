package com.bank.branch.platform.auth.facade;

import com.bank.branch.platform.auth.api.ResourceApi;
import com.bank.branch.platform.auth.api.dto.ResourceDTO;
import com.bank.branch.platform.auth.entity.PtResource;
import com.bank.branch.platform.auth.service.PermissionCacheService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.util.AntPathMatcher;

import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 资源匹配与权限 Facade 实现
 * 实现 ResourceApi，基于 Redis 缓存的全量资源进行 AntPath 匹配和权限判断。
 * 缓存由 PermissionCacheService 管理，此 Facade 不直接操作数据库。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ResourceFacade implements ResourceApi {

    private final PermissionCacheService permissionCacheService;

    /**
     * 根据请求URL和HTTP方法匹配系统已注册资源
     * 从缓存加载全量资源，使用 AntPathMatcher 进行路径匹配。
     * 仅匹配状态为启用（status=0）的资源；method 为 * 的资源匹配所有请求方法。
     *
     * @param url    请求路径
     * @param method HTTP 方法（GET/POST/PUT/DELETE）
     * @return 匹配到的第一个资源 DTO，未注册资源返回 empty
     */
    @Override
    public Optional<ResourceDTO> matchResource(String url, String method) {
        List<PtResource> resources = permissionCacheService.getAllResources();
        AntPathMatcher matcher = new AntPathMatcher();
        return resources.stream()
                // 只匹配已启用的资源
                .filter(r -> r.getStatus() != null && r.getStatus() == 0)
                // method 为 * 时匹配所有请求方法，否则大小写不敏感匹配
                .filter(r -> "*".equals(r.getResourceMethod())
                        || r.getResourceMethod().equalsIgnoreCase(method))
                // 使用 AntPathMatcher 支持通配符路径匹配
                .filter(r -> matcher.match(r.getResourceUrl(), url))
                .findFirst()
                .map(this::toResourceDTO);
    }

    /**
     * 判断指定员工是否拥有访问指定资源的权限
     * 遍历员工所有角色，检查角色的资源授权集合中是否包含目标资源。
     *
     * @param empId      员工ID
     * @param resourceId 资源ID
     * @return true 表示有权限
     */
    @Override
    public boolean hasResourcePermission(String empId, String resourceId) {
        Set<String> roleIds = permissionCacheService.getRoleIdsByEmpId(empId);
        for (String roleId : roleIds) {
            Set<String> resourceIds = permissionCacheService.getResourceIdsByRoleId(roleId);
            if (resourceIds.contains(resourceId)) {
                return true;
            }
        }
        return false;
    }

    /**
     * 查询指定员工所有可访问的资源列表
     * 合并该员工所有角色的资源授权，从缓存中取出对应资源详情。
     * 仅返回状态为启用（status=0）的资源。
     *
     * @param empId 员工ID
     * @return 资源 DTO 列表
     */
    @Override
    public List<ResourceDTO> listUserResources(String empId) {
        Set<String> roleIds = permissionCacheService.getRoleIdsByEmpId(empId);
        // 合并所有角色的资源ID，取并集
        Set<String> authorizedResourceIds = new HashSet<>();
        for (String roleId : roleIds) {
            authorizedResourceIds.addAll(permissionCacheService.getResourceIdsByRoleId(roleId));
        }
        return permissionCacheService.getAllResources().stream()
                // 只保留该员工被授权且处于启用状态的资源
                .filter(r -> authorizedResourceIds.contains(r.getResourceId())
                        && r.getStatus() != null && r.getStatus() == 0)
                .map(this::toResourceDTO)
                .collect(Collectors.toList());
    }

    /**
     * 查询指定员工所有可访问的资源URL集合（用于前端菜单过滤）
     *
     * @param empId 员工ID
     * @return 资源 URL 集合
     */
    @Override
    public Set<String> listUserResourceUrls(String empId) {
        return listUserResources(empId).stream()
                .map(ResourceDTO::getResourceUrl)
                .collect(Collectors.toSet());
    }

    // ── 私有方法 ──────────────────────────────────────────────────

    /**
     * 将 PtResource 实体转换为 ResourceDTO
     * 映射所有对外可见字段，内部字段（sysCode、createUser 等）不对外暴露
     *
     * @param resource 资源实体
     * @return ResourceDTO
     */
    private ResourceDTO toResourceDTO(PtResource resource) {
        ResourceDTO dto = new ResourceDTO();
        dto.setResourceId(resource.getResourceId());
        dto.setResourceUrl(resource.getResourceUrl());
        dto.setResourceMethod(resource.getResourceMethod());
        dto.setMenuName(resource.getMenuName());
        dto.setIsMenu(resource.getIsMenu());
        dto.setParentResourceId(resource.getParentResourceId());
        dto.setStatus(resource.getStatus());
        return dto;
    }
}
