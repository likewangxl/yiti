package com.bank.branch.platform.auth.enums;

import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * AUTH 模块错误码枚举
 * 格式：AUTH-{HTTP状态码}{序号}
 */
@Getter
@AllArgsConstructor
public enum AuthErrorCode {

    // 400 参数错误
    OLD_PASSWORD_MISMATCH("AUTH-40001", "旧密码不正确"),
    INVALID_USER_IDS("AUTH-40002", "用户ID列表为空或超出上限"),

    // 401 认证失败
    LOGIN_FAILED("AUTH-40101", "用户名或密码错误"),
    ACCOUNT_LOCKED("AUTH-40102", "用户账号已锁定"),
    ACCOUNT_EXPIRED("AUTH-40103", "用户账号已过期"),
    ACCOUNT_DISABLED("AUTH-40104", "用户账号未启用"),
    NOT_AUTHENTICATED("AUTH-40105", "未登录或会话已过期"),
    PASSWORD_ATTEMPTS_EXCEEDED("AUTH-40106", "密码错误次数超限"),
    USER_NO_ROLE("AUTH-40107", "用户未分配角色，禁止登录"),

    // 403 授权失败
    RBAC_DENIED("AUTH-40301", "无接口访问权限"),
    RESOURCE_NOT_REGISTERED("AUTH-40302", "资源未登记"),
    DATA_SCOPE_DENIED("AUTH-40303", "数据范围拒绝"),
    BIZ_TYPE_NOT_CONFIGURED("AUTH-40304", "BizType未配置"),
    ENTITY_OWNERSHIP_DENIED("AUTH-40305", "实体归属校验失败"),
    STATUS_CONSTRAINT_DENIED("AUTH-40306", "状态约束拒绝"),
    HIGH_RISK_ACTION_MISSING_REASON("AUTH-40307", "高危动作缺少原因"),
    PERMISSION_CACHE_UNAVAILABLE("AUTH-40308", "权限缓存不可用"),

    // 404 资源不存在
    ROLE_NOT_FOUND("AUTH-40401", "角色不存在"),
    RESOURCE_NOT_FOUND("AUTH-40402", "资源不存在"),
    USER_NOT_FOUND("AUTH-40403", "用户不存在"),
    ORG_NOT_FOUND("AUTH-40404", "机构不存在"),
    BIZ_SCOPE_NOT_FOUND("AUTH-40405", "BizScope配置不存在"),

    // 409 冲突
    ROLE_CODE_DUPLICATE("AUTH-40901", "角色编码已存在"),
    RESOURCE_URL_METHOD_DUPLICATE("AUTH-40902", "资源URL+Method已存在"),
    BIZ_SCOPE_DUPLICATE("AUTH-40903", "BizScope配置已存在"),
    USER_ID_DUPLICATE("AUTH-40904", "用户ID已存在"),
    USERNAME_DUPLICATE("AUTH-40905", "用户名已存在"),

    // 500 内部错误
    INTERNAL_ERROR("AUTH-50001", "权限服务内部错误"),
    CACHE_ERROR("AUTH-50002", "缓存服务异常"),

    // 503 服务暂不可用
    AUTH_SERVICE_UNAVAILABLE("AUTH-50301", "认证服务暂不可用，请稍后重试");

    private final String code;
    private final String message;
}
