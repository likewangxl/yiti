package com.bank.branch.platform.auth.security.matcher;

import com.bank.branch.platform.auth.entity.PtResource;
import com.bank.branch.platform.auth.service.PermissionCacheService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.util.AntPathMatcher;

import java.util.List;
import java.util.Optional;

/**
 * 资源匹配器
 * 将请求的 URL + HTTP Method 映射到 PT_RESOURCE 表中已注册的资源记录。
 * 支持 Ant 风格路径通配（如 /api/users/{id}、/api/admin/**）。
 * 只匹配 status=0（启用）的资源；method 为 * 时匹配所有请求方法。
 */
@Component
@RequiredArgsConstructor
public class ResourceMatcher {

    private final PermissionCacheService cacheService;
    private final AntPathMatcher antPathMatcher = new AntPathMatcher();

    /**
     * 根据请求 URL 和 HTTP 方法匹配系统已注册资源
     *
     * @param url    请求路径（如 /api/users/E001）
     * @param method HTTP 方法（如 GET、POST）
     * @return 匹配到的 PtResource；无匹配时返回 empty
     */
    public Optional<PtResource> match(String url, String method) {
        List<PtResource> resources = cacheService.getAllResources();
        return resources.stream()
            // 只匹配已启用的资源（status=0）
            .filter(r -> r.getStatus() != null && r.getStatus() == 0)
            // method 为 * 时匹配所有请求方法，否则不区分大小写匹配
            .filter(r -> "*".equals(r.getResourceMethod())
                || r.getResourceMethod().equalsIgnoreCase(method))
            // 使用 AntPathMatcher 支持 {var} 和 ** 通配符
            .filter(r -> antPathMatcher.match(r.getResourceUrl(), url))
            .findFirst();
    }
}
