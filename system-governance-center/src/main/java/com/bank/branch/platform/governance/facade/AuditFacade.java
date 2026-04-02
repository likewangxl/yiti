package com.bank.branch.platform.governance.facade;

import com.bank.branch.platform.common.web.PageRequest;
import com.bank.branch.platform.common.web.PageResult;
import com.bank.branch.platform.governance.api.AuditApi;
import com.bank.branch.platform.governance.api.dto.AuditLogCmd;
import com.bank.branch.platform.governance.api.dto.AuditLogDTO;
import com.bank.branch.platform.governance.api.dto.AuditLogQueryReqDTO;
import com.bank.branch.platform.governance.service.AuditLogService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/**
 * 审计日志 Facade 实现
 * <p>
 * 实现 AuditApi 接口，委托 AuditLogService 完成审计日志的写入与查询。
 * </p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AuditFacade implements AuditApi {

    private final AuditLogService auditLogService;

    /**
     * 记录审计日志
     * 委托给 AuditLogService.log()，使用独立事务
     *
     * @param cmd 审计日志写入命令
     */
    @Override
    public void log(AuditLogCmd cmd) {
        auditLogService.log(cmd);
    }

    /**
     * 查询审计日志（分页）
     * 委托给 AuditLogService.queryLogs()
     *
     * @param query 查询条件
     * @param page  分页参数
     * @return 分页审计日志列表
     */
    @Override
    public PageResult<AuditLogDTO> queryLogs(AuditLogQueryReqDTO query, PageRequest page) {
        return auditLogService.queryLogs(query, page.getPageNo(), page.getPageSize());
    }
}
