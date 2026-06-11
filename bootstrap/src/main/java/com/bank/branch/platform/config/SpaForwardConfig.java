package com.bank.branch.platform.config;

import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.Resource;
import org.springframework.lang.NonNull;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;
import org.springframework.web.servlet.resource.PathResourceResolver;

/**
 * 前后端一体部署的 SPA 路由回退（history 模式）。
 *
 * <p>问题：Vue 用 history 路由，直接访问/刷新 {@code /login}、{@code /workspace}、
 * {@code /system/users} 等前端深链接时，后端 classpath:/static 下并没有对应文件，
 * Spring Boot 3 抛 NoResourceFoundException → 页面 404/500 打不开。</p>
 *
 * <p>方案：对未命中真实静态资源的请求做回退——
 * <ul>
 *   <li>真实存在的静态文件（index.html / assets/*.js / *.css / 图片等）正常返回；</li>
 *   <li>{@code /api/**} 一律不回退（保持 REST 404/正常响应，绝不返回 HTML）；</li>
 *   <li>带扩展名的资源路径（含 "."）找不到就按 404，不回退（避免把缺失的 js/图片伪装成页面）；</li>
 *   <li>其余（前端路由路径）回退到 index.html，交给前端路由接管。</li>
 * </ul>
 * 走 Spring MVC ResourceResolver 层实现，与内嵌 Tomcat / 宝蓝德(BES) 等容器无关。</p>
 */
@Configuration
public class SpaForwardConfig implements WebMvcConfigurer {

    @Override
    public void addResourceHandlers(@NonNull ResourceHandlerRegistry registry) {
        registry.addResourceHandler("/**")
                .addResourceLocations("classpath:/static/")
                .resourceChain(true)
                .addResolver(new PathResourceResolver() {
                    @Override
                    protected Resource getResource(@NonNull String resourcePath, @NonNull Resource location) {
                        try {
                            Resource requested = location.createRelative(resourcePath);
                            if (requested.exists() && requested.isReadable()) {
                                return requested;   // 真实静态资源，正常返回
                            }
                        } catch (Exception ignore) {
                            // createRelative 异常视为不存在，继续走回退判断
                        }
                        // /api/** 不回退；带扩展名的缺失资源不回退（按 404）
                        if (resourcePath.startsWith("api/") || resourcePath.contains(".")) {
                            return null;
                        }
                        // 前端路由 → 回退 index.html，由前端路由渲染对应页面
                        return new ClassPathResource("/static/index.html");
                    }
                });
    }
}
