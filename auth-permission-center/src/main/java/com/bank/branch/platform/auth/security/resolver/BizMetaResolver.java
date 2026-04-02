package com.bank.branch.platform.auth.security.resolver;

import com.bank.branch.platform.common.security.annotation.BizAuth;
import org.springframework.stereotype.Component;
import org.springframework.web.method.HandlerMethod;

import java.util.Optional;

/**
 * BizType + BizAction 解析器
 * 从 Spring MVC 的 HandlerMethod 中提取 @BizAuth 注解元数据。
 * 若 handler 不是 HandlerMethod 或方法未标注 @BizAuth，返回 empty。
 */
@Component
public class BizMetaResolver {

    /**
     * 解析 handler 上的 @BizAuth 注解
     *
     * @param handler Spring MVC handler（可能是 HandlerMethod 或其他类型）
     * @return 包含 BizType 和 BizAction 的元数据；未声明时返回 empty
     */
    public Optional<BizMeta> resolve(Object handler) {
        if (!(handler instanceof HandlerMethod handlerMethod)) {
            return Optional.empty();
        }
        BizAuth annotation = handlerMethod.getMethodAnnotation(BizAuth.class);
        if (annotation == null) {
            return Optional.empty();
        }
        return Optional.of(new BizMeta(annotation.bizType(), annotation.action()));
    }
}
