package com.bank.branch.platform.portal.adapter;

import com.bank.branch.platform.common.security.context.DataScopeContext;

/**
 * record (auth.api.dto.DataScopeContext) → POJO (common.security.context.DataScopeContext) 转换桥
 *
 * 项目中存在两个 DataScopeContext 类：
 * - auth.api.dto.DataScopeContext: BizScopeApi.buildScopeContext() 的返回类型，是 record
 * - common.security.context.DataScopeContext: ThreadLocal 持有 + Mapper XML 引用的对象，是 @Data POJO
 *
 * 业务 Service / Mapper 期望使用 POJO，因此 Controller 在拿到 BizScopeApi 的返回值后必须用本工具转换。
 */
public final class DataScopeAdapter {

    private DataScopeAdapter() {}

    public static DataScopeContext fromAuthRecord(
            com.bank.branch.platform.auth.api.dto.DataScopeContext authRecord) {
        if (authRecord == null) return null;
        DataScopeContext pojo = new DataScopeContext();
        pojo.setBizType(authRecord.bizType());
        pojo.setAction(authRecord.action());
        pojo.setScope(authRecord.scopeType());           // record .scopeType() → POJO .scope (字段名差异)
        pojo.setEmpId(authRecord.empId());
        pojo.setOrgCode(authRecord.orgCode());
        pojo.setOrgSubtreeCodes(authRecord.orgSubtreeCodes());
        return pojo;
    }
}
