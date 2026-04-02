package com.bank.branch.platform.auth.api.dto;

import com.bank.branch.platform.common.security.enums.BizAction;
import com.bank.branch.platform.common.security.enums.BizType;
import com.bank.branch.platform.common.security.enums.DataScopeType;

import java.util.Set;

/**
 * 数据范围上下文记录
 * 封装某次业务操作的数据权限范围信息，供数据过滤使用
 *
 * @param scopeType       数据范围类型
 * @param empId           当前操作员工ID
 * @param orgCode         当前员工主机构编码
 * @param orgSubtreeCodes 机构子树编码集合（scopeType=ORG_SUBTREE时填充）
 * @param bizType         业务类型
 * @param action          业务操作类型
 */
public record DataScopeContext(
    DataScopeType scopeType,
    String empId,
    String orgCode,
    Set<String> orgSubtreeCodes,
    BizType bizType,
    BizAction action
) {}
