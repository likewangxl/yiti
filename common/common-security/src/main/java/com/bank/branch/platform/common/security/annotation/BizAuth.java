package com.bank.branch.platform.common.security.annotation;

import com.bank.branch.platform.common.security.enums.BizAction;
import com.bank.branch.platform.common.security.enums.BizType;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 业务权限注解
 * 标注在 Controller 方法上，声明该接口所需的业务类型和操作权限
 * 运行时由 AOP 拦截器进行权限校验
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface BizAuth {
    /** 业务类型 */
    BizType bizType();
    /** 业务操作，默认为 READ */
    BizAction action() default BizAction.READ;
}
