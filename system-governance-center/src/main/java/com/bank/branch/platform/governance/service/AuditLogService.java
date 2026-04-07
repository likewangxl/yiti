package com.bank.branch.platform.governance.service;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

import jakarta.servlet.http.HttpServletResponse;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellStyle;
import org.apache.poi.ss.usermodel.FillPatternType;
import org.apache.poi.ss.usermodel.Font;
import org.apache.poi.ss.usermodel.HorizontalAlignment;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.VerticalAlignment;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;

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

    /**
     * 根据ID查询单条审计日志详情（D.2）。
     *
     * @param id 审计日志ID
     * @return 审计日志详情DTO
     */
    public AuditLogDTO getById(String id) {
        log.debug("[AuditLogService.getById] id={}", id);
        AuditLog entity = auditLogMapper.selectById(id);
        if (entity == null) {
            log.warn("[AuditLogService.getById] 审计日志不存在 id={}", id);
            return null;
        }
        return toDTO(entity);
    }

    /**
     * 导出审计日志为Excel文件（D.3）。
     * <p>
     * 导出的列包括：日志ID、操作时间、操作人工号、操作人姓名、
     * 业务类型、业务动作、资源URL、请求方法、响应状态、
     * 执行时长(ms)、IP地址、原因、错误信息。
     * </p>
     *
     * @param query    查询条件（与列表查询条件一致）
     * @param response HTTP响应对象，用于写入Excel文件
     */
    public void exportLogs(AuditLogQueryReqDTO query, HttpServletResponse response) {
        log.info("[AuditLogService.exportLogs] empId={}, bizType={}", query.getEmpId(), query.getBizType());

        LocalDate startDate = parseDate(query.getStartTime());
        LocalDate endDate = parseDate(query.getEndTime());

        // 查询所有匹配记录（不分页，上限10000条）
        int maxExportRows = 10000;
        List<AuditLog> records = auditLogMapper.selectByPage(
                query.getEmpId(), query.getBizType(), query.getBizAction(),
                startDate, endDate, query.getKeyword(),
                0, maxExportRows
        );

        try (Workbook workbook = new XSSFWorkbook()) {
            Sheet sheet = workbook.createSheet("审计日志");

            // 创建表头样式
            CellStyle headerStyle = workbook.createCellStyle();
            Font headerFont = workbook.createFont();
            headerFont.setBold(true);
            headerStyle.setFont(headerFont);
            headerStyle.setAlignment(HorizontalAlignment.CENTER);
            headerStyle.setVerticalAlignment(VerticalAlignment.CENTER);
            headerStyle.setFillForegroundColor((short) 0xC6EFCE);
            headerStyle.setFillPattern(FillPatternType.SOLID_FOREGROUND);

            // 创建表头行
            String[] headers = {"日志ID", "操作时间", "操作人工号", "操作人姓名", "业务类型",
                    "业务动作", "资源URL", "请求方法", "响应状态", "执行时长(ms)",
                    "IP地址", "原因", "错误信息"};
            Row headerRow = sheet.createRow(0);
            for (int i = 0; i < headers.length; i++) {
                Cell cell = headerRow.createCell(i);
                cell.setCellValue(headers[i]);
                cell.setCellStyle(headerStyle);
            }

            // 填充数据行
            int rowNum = 1;
            for (AuditLog record : records) {
                Row row = sheet.createRow(rowNum++);
                row.createCell(0).setCellValue(record.getId());
                row.createCell(1).setCellValue(record.getCreatedTime() != null
                        ? record.getCreatedTime().format(DT_FMT) : "");
                row.createCell(2).setCellValue(record.getEmpId() != null ? record.getEmpId() : "");
                row.createCell(3).setCellValue(record.getEmpName() != null ? record.getEmpName() : "");
                row.createCell(4).setCellValue(record.getBizType() != null ? record.getBizType() : "");
                row.createCell(5).setCellValue(record.getBizAction() != null ? record.getBizAction() : "");
                row.createCell(6).setCellValue(record.getResourceUrl() != null ? record.getResourceUrl() : "");
                row.createCell(7).setCellValue(record.getRequestMethod() != null ? record.getRequestMethod() : "");
                row.createCell(8).setCellValue(record.getResponseStatus() != null
                        ? record.getResponseStatus().toString() : "");
                row.createCell(9).setCellValue(record.getExecutionTime() != null ? record.getExecutionTime().doubleValue() : 0.0);
                row.createCell(10).setCellValue(record.getIpAddress() != null ? record.getIpAddress() : "");
                row.createCell(11).setCellValue(record.getReason() != null ? record.getReason() : "");
                row.createCell(12).setCellValue(record.getErrorMsg() != null ? record.getErrorMsg() : "");
            }

            // 自动调整列宽
            for (int i = 0; i < headers.length; i++) {
                sheet.autoSizeColumn(i);
            }

            // 设置响应头并写入
            String filename = "audit_log_" + LocalDateTime.now().format(
                    DateTimeFormatter.ofPattern("yyyyMMddHHmmss")) + ".xlsx";
            response.setContentType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");
            response.setHeader("Content-Disposition",
                    "attachment; filename=" + java.net.URLEncoder.encode(filename, java.nio.charset.StandardCharsets.UTF_8));
            response.setHeader("Access-Control-Expose-Headers", "Content-Disposition");
            workbook.write(response.getOutputStream());
            response.getOutputStream().flush();

            log.info("[AuditLogService.exportLogs] 导出完成，共{}条记录", records.size());
        } catch (Exception e) {
            log.error("[AuditLogService.exportLogs] 导出失败", e);
            throw new RuntimeException("导出审计日志失败: " + e.getMessage(), e);
        }
    }

}
