package com.bank.branch.platform.governance.service;

import com.bank.branch.platform.common.web.PageResult;
import com.bank.branch.platform.governance.api.dto.AuditLogCmd;
import com.bank.branch.platform.governance.api.dto.AuditLogDTO;
import com.bank.branch.platform.governance.api.dto.AuditLogQueryReqDTO;
import com.bank.branch.platform.governance.entity.AuditLog;
import com.bank.branch.platform.governance.mapper.AuditLogMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * 审计日志服务单元测试
 * TDD RED 阶段：先编写测试用例，确保编译失败后再实现生产代码
 */
@ExtendWith(MockitoExtension.class)
class AuditLogServiceTest {

    @Mock
    AuditLogMapper auditLogMapper;

    @InjectMocks
    AuditLogService auditLogService;

    /**
     * 测试 log() 方法：验证 mapper.insert 被调用且实体字段正确映射
     */
    @Test
    void log_insertsRecord() {
        AuditLogCmd cmd = AuditLogCmd.builder()
                .traceId("trace-001")
                .empId("E001")
                .empName("张三")
                .bizType("SYS_CONFIG")
                .bizAction("CONFIG")
                .resourceUrl("/api/admin/sys/configs/C001")
                .requestMethod("PUT")
                .requestParams("{\"configValue\":\"new\"}")
                .responseStatus(200)
                .ipAddress("192.168.1.1")
                .userAgent("Mozilla/5.0")
                .executionTime(120)
                .reason("修改系统配置")
                .build();

        when(auditLogMapper.insert((AuditLog) any())).thenReturn(1);

        auditLogService.log(cmd);

        ArgumentCaptor<AuditLog> captor = ArgumentCaptor.forClass(AuditLog.class);
        verify(auditLogMapper).insert(captor.capture());

        AuditLog entity = captor.getValue();
        assertThat(entity.getTraceId()).isEqualTo("trace-001");
        assertThat(entity.getEmpId()).isEqualTo("E001");
        assertThat(entity.getEmpName()).isEqualTo("张三");
        assertThat(entity.getBizType()).isEqualTo("SYS_CONFIG");
        assertThat(entity.getBizAction()).isEqualTo("CONFIG");
        assertThat(entity.getResourceUrl()).isEqualTo("/api/admin/sys/configs/C001");
        assertThat(entity.getRequestMethod()).isEqualTo("PUT");
        assertThat(entity.getResponseStatus()).isEqualTo(200);
        assertThat(entity.getIpAddress()).isEqualTo("192.168.1.1");
        assertThat(entity.getExecutionTime()).isEqualTo(120);
        assertThat(entity.getReason()).isEqualTo("修改系统配置");
        assertThat(entity.getCreatedTime()).isNotNull();
    }

    /**
     * 测试 log() 方法：验证生成的 UUID 主键不为空
     */
    @Test
    void log_generatesUuidForId() {
        AuditLogCmd cmd = AuditLogCmd.builder()
                .traceId("trace-002")
                .empId("E002")
                .bizType("SYS_CONFIG")
                .bizAction("EXPORT")
                .build();

        when(auditLogMapper.insert((AuditLog) any())).thenReturn(1);

        auditLogService.log(cmd);

        ArgumentCaptor<AuditLog> captor = ArgumentCaptor.forClass(AuditLog.class);
        verify(auditLogMapper).insert(captor.capture());

        AuditLog entity = captor.getValue();
        assertThat(entity.getId()).isNotNull();
        assertThat(entity.getId()).isNotEmpty();
        // UUID 去掉横线后长度为 32
        assertThat(entity.getId()).hasSize(32);
    }

    /**
     * 测试 queryLogs() 方法：验证带筛选条件时正确传递参数给 mapper
     */
    @Test
    void queryLogs_withFilters_delegatesToMapper() {
        AuditLogQueryReqDTO query = new AuditLogQueryReqDTO();
        query.setEmpId("E001");
        query.setBizType("SYS_CONFIG");
        query.setBizAction("CONFIG");
        query.setStartTime("2026-04-01");
        query.setEndTime("2026-04-03");
        query.setKeyword("配置");

        when(auditLogMapper.countByPage(
                eq("E001"), eq("SYS_CONFIG"), eq("CONFIG"),
                any(LocalDate.class), any(LocalDate.class),
                eq("配置"), eq(0), eq(20)
        )).thenReturn(1L);

        AuditLog log = makeAuditLog("A001", "trace-001", "E001", "张三");
        when(auditLogMapper.selectByPage(
                eq("E001"), eq("SYS_CONFIG"), eq("CONFIG"),
                any(LocalDate.class), any(LocalDate.class),
                eq("配置"), eq(0), eq(20)
        )).thenReturn(List.of(log));

        PageResult<AuditLogDTO> result = auditLogService.queryLogs(query, 1, 20);

        verify(auditLogMapper).selectByPage(
                eq("E001"), eq("SYS_CONFIG"), eq("CONFIG"),
                any(LocalDate.class), any(LocalDate.class),
                eq("配置"), eq(0), eq(20)
        );
        verify(auditLogMapper).countByPage(
                eq("E001"), eq("SYS_CONFIG"), eq("CONFIG"),
                any(LocalDate.class), any(LocalDate.class),
                eq("配置"), eq(0), eq(20)
        );
    }

    /**
     * 测试 queryLogs() 方法：验证返回的 PageResult 结构正确
     */
    @Test
    void queryLogs_returnsPaginatedResult() {
        AuditLogQueryReqDTO query = new AuditLogQueryReqDTO();
        query.setEmpId("E001");

        AuditLog log1 = makeAuditLog("A001", "trace-001", "E001", "张三");
        AuditLog log2 = makeAuditLog("A002", "trace-002", "E001", "张三");

        when(auditLogMapper.countByPage(
                eq("E001"), isNull(), isNull(), isNull(), isNull(), isNull(), eq(0), eq(10)
        )).thenReturn(2L);
        when(auditLogMapper.selectByPage(
                eq("E001"), isNull(), isNull(), isNull(), isNull(), isNull(), eq(0), eq(10)
        )).thenReturn(List.of(log1, log2));

        PageResult<AuditLogDTO> result = auditLogService.queryLogs(query, 1, 10);

        assertThat(result.getTotal()).isEqualTo(2L);
        assertThat(result.getPageNo()).isEqualTo(1);
        assertThat(result.getPageSize()).isEqualTo(10);
        assertThat(result.getRecords()).hasSize(2);
        assertThat(result.getRecords().get(0).getId()).isEqualTo("A001");
        assertThat(result.getRecords().get(1).getId()).isEqualTo("A002");
    }

    /**
     * 测试 queryLogs() 方法：空筛选条件时仍可正常返回结果
     */
    @Test
    void queryLogs_noFilters_returnsAll() {
        AuditLogQueryReqDTO query = new AuditLogQueryReqDTO();
        // 所有筛选条件均为 null

        when(auditLogMapper.countByPage(
                isNull(), isNull(), isNull(), isNull(), isNull(), isNull(), eq(0), eq(20)
        )).thenReturn(0L);
        when(auditLogMapper.selectByPage(
                isNull(), isNull(), isNull(), isNull(), isNull(), isNull(), eq(0), eq(20)
        )).thenReturn(Collections.emptyList());

        PageResult<AuditLogDTO> result = auditLogService.queryLogs(query, 1, 20);

        assertThat(result.getTotal()).isEqualTo(0L);
        assertThat(result.getRecords()).isEmpty();
        assertThat(result.getPageNo()).isEqualTo(1);
        assertThat(result.getPageSize()).isEqualTo(20);
    }

    // ── 辅助方法 ──────────────────────────────────────────────────

    private AuditLog makeAuditLog(String id, String traceId, String empId, String empName) {
        AuditLog log = new AuditLog();
        log.setId(id);
        log.setTraceId(traceId);
        log.setEmpId(empId);
        log.setEmpName(empName);
        log.setBizType("SYS_CONFIG");
        log.setBizAction("CONFIG");
        log.setResourceUrl("/api/admin/sys/configs/C001");
        log.setRequestMethod("PUT");
        log.setRequestParams("{\"configValue\":\"new\"}");
        log.setResponseStatus(200);
        log.setIpAddress("192.168.1.1");
        log.setExecutionTime(120);
        log.setReason("修改系统配置");
        log.setCreatedTime(LocalDateTime.now());
        return log;
    }
}
