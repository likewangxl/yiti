package com.bank.branch.platform.auth.security.resolver;

import com.bank.branch.platform.common.security.enums.BizAction;
import com.bank.branch.platform.common.security.enums.BizType;

/**
 * 业务元数据
 * 从 @BizAuth 注解解析出的业务类型和操作类型，供授权拦截器使用
 */
public record BizMeta(BizType bizType, BizAction action) {}
