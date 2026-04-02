package com.bank.branch.platform.auth.security.context;

import com.bank.branch.platform.common.security.context.CurrentUserContext;
import org.springframework.stereotype.Component;

/**
 * 当前用户上下文提供器（ThreadLocal管理）
 * 由 AuthenticationFilter 在请求进入时填充，afterCompletion 时清除。
 * 设计为无状态组件：每个请求线程独立持有上下文，避免并发污染。
 */
@Component
public class CurrentUserProvider {

    private static final ThreadLocal<CurrentUserContext> HOLDER = new ThreadLocal<>();

    /**
     * 设置当前线程的用户上下文
     * 由过滤器/拦截器在请求预处理阶段调用
     *
     * @param ctx 用户上下文
     */
    public void set(CurrentUserContext ctx) {
        HOLDER.set(ctx);
    }

    /**
     * 获取当前线程的用户上下文
     * 未登录或未经过认证过滤器时返回 null
     *
     * @return 当前用户上下文，未登录时返回 null
     */
    public CurrentUserContext get() {
        return HOLDER.get();
    }

    /**
     * 清除当前线程的用户上下文
     * 必须在请求结束后（如过滤器 afterCompletion）调用，防止 ThreadLocal 内存泄漏
     */
    public void clear() {
        HOLDER.remove();
    }
}
