package com.bank.branch.platform.auth.api;

import com.bank.branch.platform.auth.api.dto.ResourceDTO;

import java.util.List;
import java.util.Optional;
import java.util.Set;

/**
 * 资源匹配与权限对外API
 * 提供URL资源匹配、用户资源权限查询能力
 * 结果有缓存，调用开销可控
 */
public interface ResourceApi {

    /**
     * 根据请求URL和HTTP方法匹配系统资源
     *
     * @param url    请求路径
     * @param method HTTP方法（GET/POST/PUT/DELETE）
     * @return 匹配到的资源，未注册资源返回 empty
     */
    Optional<ResourceDTO> matchResource(String url, String method);

    /**
     * 判断指定员工是否拥有访问指定资源的权限
     *
     * @param empId      员工ID
     * @param resourceId 资源ID
     * @return true 表示有权限
     */
    boolean hasResourcePermission(String empId, String resourceId);

    /**
     * 查询指定员工所有可访问的资源列表
     *
     * @param empId 员工ID
     * @return 资源DTO列表
     */
    List<ResourceDTO> listUserResources(String empId);

    /**
     * 查询指定员工所有可访问的资源URL集合（用于前端菜单过滤）
     *
     * @param empId 员工ID
     * @return 资源URL集合
     */
    Set<String> listUserResourceUrls(String empId);
}
