package com.bank.branch.platform.governance.service;

import com.bank.branch.platform.common.web.PageResult;
import com.bank.branch.platform.governance.api.dto.AuditLogCmd;
import com.bank.branch.platform.governance.api.dto.AuditLogDTO;
import com.bank.branch.platform.governance.api.dto.AuditLogQueryReqDTO;
import com.bank.branch.platform.governance.entity.AuditLog;
import com.bank.branch.platform.governance.mapper.AuditLogMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * 审计日志服务
 * <p>
 * 负责审计日志的写入与分页查询。
 * log() 方法使用 REQUIRES_NEW 独立事务，确保即使业务事务回滚，审计日志仍保留。
 * </p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AuditLogService {

    private final AuditLogMapper auditLogMapper;

    /** 日期时间格式化器，用于 DTO 输出 */
    private static final DateTimeFormatter DT_FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    /**
     * 记录审计日志（独立事务）。
     * <p>
     * 使用 REQUIRES_NEW 传播级别，使审计日志写入在独立事务中完成，
     * 即使外层业务事务回滚，审计记录也不会丢失。
     * </p>
     *
     * @param cmd 审计日志写入命令
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void log(AuditLogCmd cmd) {
        log.info("[AuditLogService.log] traceId={}, empId={}, bizType={}, bizAction={}",
                cmd.getTraceId(), cmd.getEmpId(), cmd.getBizType(), cmd.getBizAction());

        AuditLog entity = new AuditLog();
        entity.setId(UUID.randomUUID().toString().replace("-", ""));
        entity.setTraceId(cmd.getTraceId());
        entity.setEmpId(cmd.getEmpId());
        entity.setEmpName(cmd.getEmpName());
        entity.setBizType(cmd.getBizType());
        entity.setBizAction(cmd.getBizAction());
        entity.setResourceUrl(cmd.getResourceUrl());
        entity.setRequestMethod(cmd.getRequestMethod());
        entity.setRequestParams(cmd.getRequestParams());
        entity.setResponseStatus(cmd.getResponseStatus());
        entity.setErrorMsg(cmd.getErrorMsg());
        entity.setIpAddress(cmd.getIpAddress());
        entity.setUserAgent(cmd.getUserAgent());
        entity.setExecutionTime(cmd.getExecutionTime());
        entity.setReason(cmd.getReason());
        entity.setCreatedTime(LocalDateTime.now());

        auditLogMapper.insert(entity);
        log.info("[AuditLogService.log] 审计日志写入成功 id={}", entity.getId());
    }

    /**
     * 分页查询审计日志。
     *
     * @param query    查询条件（所有字段均可选）
     * @param pageNo   当前页码（从 1 开始）
     * @param pageSize 每页大小
     * @return 分页结果
     */
    public PageResult<AuditLogDTO> queryLogs(AuditLogQueryReqDTO query, int pageNo, int pageSize) {
        log.debug("[AuditLogService.queryLogs] empId={}, bizType={}, pageNo={}, pageSize={}",
                query.getEmpId(), query.getBizType(), pageNo, pageSize);

        int offset = (pageNo - 1) * pageSize;

        // 解析日期字符串为 LocalDate
        LocalDate startDate = parseDate(query.getStartTime());
        LocalDate endDate = parseDate(query.getEndTime());

        long total = auditLogMapper.countByPage(
                query.getEmpId(), query.getBizType(), query.getBizAction(),
                startDate, endDate, query.getKeyword(),
                offset, pageSize
        );

        List<AuditLog> records = auditLogMapper.selectByPage(
                query.getEmpId(), query.getBizType(), query.getBizAction(),
                startDate, endDate, query.getKeyword(),
                offset, pageSize
        );

        List<AuditLogDTO> dtos = records.stream()
                .map(this::toDTO)
                .collect(Collectors.toList());

        return PageResult.of(pageNo, pageSize, total, dtos);
    }

    // ── 私有方法 ──────────────────────────────────────────────────

    /**
     * 将 AuditLog 实体转换为 AuditLogDTO
     *
     * @param entity 审计日志实体
     * @return AuditLogDTO
     */
    private AuditLogDTO toDTO(AuditLog entity) {
        AuditLogDTO dto = new AuditLogDTO();
        dto.setId(entity.getId());
        dto.setTraceId(entity.getTraceId());
        dto.setEmpId(entity.getEmpId());
        dto.setEmpName(entity.getEmpName());
        dto.setBizType(entity.getBizType());
        dto.setBizAction(entity.getBizAction());
        dto.setResourceUrl(entity.getResourceUrl());
        dto.setRequestMethod(entity.getRequestMethod());
        dto.setRequestParams(entity.getRequestParams());
        dto.setResponseStatus(entity.getResponseStatus());
        dto.setErrorMsg(entity.getErrorMsg());
        dto.setIpAddress(entity.getIpAddress());
        dto.setExecutionTime(entity.getExecutionTime());
        dto.setReason(entity.getReason());
        dto.setCreatedTime(entity.getCreatedTime() != null ? entity.getCreatedTime().format(DT_FMT) : null);
        return dto;
    }

    /**
     * 将日期字符串解析为 LocalDate，null 或空字符串返回 null
     *
     * @param dateStr 日期字符串（yyyy-MM-dd 格式）
     * @return LocalDate 或 null
     */
    private LocalDate parseDate(String dateStr) {
        if (dateStr == null || dateStr.isEmpty()) {
            return null;
        }
        return LocalDate.parse(dateStr);
    }
}
