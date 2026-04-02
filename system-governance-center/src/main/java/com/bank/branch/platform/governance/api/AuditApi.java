package com.bank.branch.platform.governance.api;

import com.bank.branch.platform.common.web.PageResult;
import com.bank.branch.platform.governance.api.dto.AuditLogCmd;
import com.bank.branch.platform.governance.api.dto.AuditLogDTO;
import com.bank.branch.platform.governance.api.dto.AuditLogQueryReqDTO;
import com.bank.branch.platform.common.web.PageRequest;

/**
 * 审计日志对外API
 * 提供审计日志的写入与查询能力
 * 写入为同步操作，确保审计数据与业务操作一致
 */
public interface AuditApi {

    /**
     * 记录审计日志
     * 同步写入 audit_log 表，使用独立事务（REQUIRES_NEW）
     *
     * @param cmd 审计日志写入命令
     * @throws IllegalArgumentException cmd 中必填字段缺失时
     */
    void log(AuditLogCmd cmd);

    /**
     * 查询审计日志（分页）
     *
     * @param query 查询条件
     * @param page  分页参数
     * @return 分页审计日志列表
     */
    PageResult<AuditLogDTO> queryLogs(AuditLogQueryReqDTO query, PageRequest page);
}
