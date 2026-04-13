package com.bank.branch.platform.portal.support;

import org.junit.jupiter.api.extension.ExtendWith;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 测试用：注入 mock 的当前用户上下文 + 数据范围
 * 默认 dataScope 为 ORG_SUBTREE，避免测试漏掉真实过滤分支
 *
 * 使用方式：标注在 @SpringBootTest 测试类的方法上，配合 AbstractControllerIntegrationTest 基类。
 * 基类已 @MockBean(CurrentUserApi.class)，本扩展通过 ExtensionContext 拿到 mock 实例
 * 后用 Mockito.when(...) stub 各方法。
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
@ExtendWith(MockEmpContextExtension.class)
public @interface WithMockEmpContext {
    String empId() default "E10001";
    String orgCode() default "ORG_SZ_001";
    String[] roleCodes() default {"R_RM"};
    String dataScope() default "ORG_SUBTREE";
    String[] orgSubtree() default {"ORG_SZ_001"};
    boolean systemAdmin() default false;
}
