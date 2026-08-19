package com.bank.branch.platform.report.service.impl;

import com.bank.branch.platform.auth.api.CurrentUserApi;
import com.bank.branch.platform.auth.api.UserApi;
import com.bank.branch.platform.common.web.PageRequest;
import com.bank.branch.platform.common.web.PageResult;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.bank.branch.platform.governance.api.AuditApi;
import com.bank.branch.platform.governance.api.FileApi;
import com.bank.branch.platform.governance.api.dto.AuditLogCmd;
import com.bank.branch.platform.governance.api.dto.FileObjectDTO;
import com.bank.branch.platform.governance.storage.FileCategory;
import com.bank.branch.platform.report.dto.req.SqlProbeExportReqDTO;
import com.bank.branch.platform.report.dto.req.SqlProbeExecuteReqDTO;
import com.bank.branch.platform.report.dto.resp.SchemaWhitelistRespDTO;
import com.bank.branch.platform.report.dto.resp.SqlProbeExecuteRespDTO;
import com.bank.branch.platform.report.dto.resp.SqlProbeExportFileDTO;
import com.bank.branch.platform.report.dto.resp.SqlProbeExportTaskRespDTO;
import com.bank.branch.platform.report.dto.resp.SqlProbeHistoryRespDTO;
import com.bank.branch.platform.report.entity.SqlProbeExportTask;
import com.bank.branch.platform.report.entity.SqlProbeHistory;
import com.bank.branch.platform.report.enums.RptErrorCode;
import com.bank.branch.platform.report.exception.RptException;
import com.bank.branch.platform.report.mapper.SqlProbeHistoryMapper;
import com.bank.branch.platform.report.service.SqlProbeService;
import com.bank.branch.platform.report.support.SqlSafeResult;
import com.bank.branch.platform.report.support.SqlSafeValidator;
import com.bank.branch.platform.report.support.PathMultipartFile;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.SQLException;
import java.sql.SQLTimeoutException;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.Semaphore;
import java.util.Comparator;
import java.util.stream.Stream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

/**
 * {@link SqlProbeService} 实现（Task M4.2.1，Green）.
 *
 * <p>核心执行链（plan L2640-L2730）：
 * <ol>
 *   <li>访问控制：交由菜单授权（@BizAuth + PT_ROLE_RESOURCE），服务层不再硬编码角色白名单</li>
 *   <li>SqlSafeValidator 校验 + LIMIT 标准化</li>
 *   <li>INSERT SQL_PROBE_HISTORY(status=RUNNING) 占位（出失联场景能查到 RUNNING 行）</li>
 *   <li>Semaphore.tryAcquire (并发上限 10)</li>
 *   <li>readOnlyDataSource 取连接 + setReadOnly + setQueryTimeout 30s（行数不再限制）</li>
 *   <li>executeQuery + 行 Map 装配（返回全部结果行，仅受 30s 超时约束）</li>
 *   <li>UPDATE 终态 (SUCCESS / TIMEOUT / FAILED) + AuditApi.log 同步</li>
 * </ol>
 *
 * <p>所有失败路径（角色拒绝除外）必须落审计 + 更新历史，确保审计闭环。
 */
@Slf4j
@Service
public class SqlProbeServiceImpl implements SqlProbeService {

    private static final int CONCURRENT_LIMIT = 10;

    private static final int QUERY_TIMEOUT_SEC = 30;

    private static final int DEFAULT_EXPORT_COUNT = 1000;

    private static final int MAX_EXPORT_COUNT = 50000;

    private static final int EXCEL_CHUNK_ROWS = 10000;

    private static final String SQL_PROBE_FILE_CATEGORY = FileCategory.EXPORT_SQL_PROBE;

    private static final String OBS_FILE_REFERENCE_MAGIC = "OBS_FILE_ID:";

    private final SqlSafeValidator validator;

    private final SqlProbeHistoryMapper historyMapper;

    private final CurrentUserApi currentUserApi;

    private final AuditApi auditApi;

    private final FileApi fileApi;

    private final DataSource readOnlyDataSource;

    /** 白名单展示配置（D.4 用），由 RptSqlSafeConfig 提供同款配置项. */
    private final List<String> whitelistTablesView;

    private final List<String> forbiddenKeywordsView;

    private final int maxRowsView;

    private final int maxSqlLengthView;

    private final int maxSubqueryDepthView;

    /** 用户 API：把历史/审计里的 empId 解析成用户名称. */
    private final UserApi userApi;

    /** 异步导出任务表 mapper（MyBatis-Plus BaseMapper）. */
    private final com.bank.branch.platform.report.mapper.SqlProbeExportTaskMapper exportTaskMapper;

    /** SQL 探查异步导出专用线程池. */
    private final java.util.concurrent.Executor exportExecutor;

    private final Semaphore semaphore = new Semaphore(CONCURRENT_LIMIT);

    public SqlProbeServiceImpl(
            SqlSafeValidator validator,
            SqlProbeHistoryMapper historyMapper,
            CurrentUserApi currentUserApi,
            AuditApi auditApi,
            FileApi fileApi,
            @Qualifier("rptReadOnlyDataSource") DataSource readOnlyDataSource,
            @Value("#{'${rpt.sql.probe.whitelist-tables:CUST_MASTER,CUST_LEAD,CUST_TAG,touch_record,EMP_INDEX_RESULT,ORG_INDEX_RESULT,CUST_INDEX_RESULT,KPI_RESULT,metric_def,SYS_DICT,sys_dict_item,EXT_ORG_INFO,EXT_USER_ORG}'.split(',')}") List<String> whitelistTablesView,
            @Value("#{'${rpt.sql.probe.forbidden-keywords:DROP,DELETE,UPDATE,INSERT,TRUNCATE,ALTER,CREATE,RENAME,REPLACE,GRANT,REVOKE,LOCK,UNLOCK,SET,CALL,EXEC,EXECUTE,LOAD,SHUTDOWN,USE,DESCRIBE,EXPLAIN,SHOW,COMMIT,ROLLBACK,SAVEPOINT,DECLARE,HANDLER,SIGNAL,RESIGNAL}'.split(',')}") List<String> forbiddenKeywordsView,
            @Value("${rpt.sql.probe.max-rows:1000}") int maxRowsView,
            @Value("${rpt.sql.probe.max-sql-length:5000}") int maxSqlLengthView,
            @Value("${rpt.sql.probe.max-subquery-depth:3}") int maxSubqueryDepthView,
            UserApi userApi,
            com.bank.branch.platform.report.mapper.SqlProbeExportTaskMapper exportTaskMapper,
            @Qualifier("sqlProbeExportExecutor") java.util.concurrent.Executor exportExecutor) {
        this.validator = validator;
        this.historyMapper = historyMapper;
        this.currentUserApi = currentUserApi;
        this.auditApi = auditApi;
        this.fileApi = fileApi;
        this.readOnlyDataSource = readOnlyDataSource;
        this.whitelistTablesView = whitelistTablesView;
        this.forbiddenKeywordsView = forbiddenKeywordsView;
        this.maxRowsView = maxRowsView;
        this.maxSqlLengthView = maxSqlLengthView;
        this.maxSubqueryDepthView = maxSubqueryDepthView;
        this.userApi = userApi;
        this.exportTaskMapper = exportTaskMapper;
        this.exportExecutor = exportExecutor;
    }

    @Override
    public SqlProbeExecuteRespDTO execute(SqlProbeExecuteReqDTO req) {
        // 访问控制交由菜单授权（@BizAuth），服务层不再做角色白名单校验
        String empId = currentUserApi.getCurrentEmpId();

        // 校验 + 标准化 SQL（校验失败直接抛，不入库 / 不审计）
        SqlSafeResult safe = validator.validateAndNormalize(req.getSql());
        String normalizedSql = safe.getNormalizedSql();

        // 3) 占位 RUNNING 历史
        String historyId = UUID.randomUUID().toString().replace("-", "");
        SqlProbeHistory hist = new SqlProbeHistory();
        hist.setId(historyId);
        hist.setEmpId(empId);
        hist.setSqlText(normalizedSql);
        hist.setRemark(req.getRemark());
        hist.setStatus("RUNNING");
        hist.setCreatedTime(LocalDateTime.now());
        historyMapper.insert(hist);

        // 4) 并发控制 + 执行
        if (!semaphore.tryAcquire()) {
            log.warn("[SqlProbeService] 并发数超限（{}），拒绝 historyId={}", CONCURRENT_LIMIT, historyId);
            updateTerminal(historyId, "FAILED", null, null, "并发数超限");
            safelyAudit(empId, historyId, "FAILED", req.getRemark(), normalizedSql, "并发数超限");
            throw new RptException(RptErrorCode.SQL_CONCURRENT_LIMIT);
        }
        long startMs = System.currentTimeMillis();
        try (Connection conn = readOnlyDataSource.getConnection();
             PreparedStatement stmt = conn.prepareStatement(normalizedSql)) {
            // setReadOnly 双层防御（与 DataSource bean 内的 setDefaultReadOnly 互为兜底）
            try {
                conn.setReadOnly(true);
            } catch (SQLException ignore) {
                // 部分 mock / 异常实现可能不支持，忽略
            }
            stmt.setQueryTimeout(QUERY_TIMEOUT_SEC);
            // 行数限制已取消：内联执行返回全部结果（仍受 30s 超时保护），超大数据请走异步导出下载

            try (ResultSet rs = stmt.executeQuery()) {
                List<String> columns = readColumns(rs);
                List<Map<String, Object>> rows = readRows(rs, columns);
                int elapsedMs = (int) (System.currentTimeMillis() - startMs);

                updateTerminal(historyId, "SUCCESS", rows.size(), elapsedMs, null);
                safelyAudit(empId, historyId, "SUCCESS", req.getRemark(), normalizedSql, null);

                return SqlProbeExecuteRespDTO.builder()
                        .historyId(historyId)
                        .columns(columns)
                        .rows(rows)
                        .rowCount(rows.size())
                        .executionTimeMs(elapsedMs)
                        .build();
            }
        } catch (SQLTimeoutException ex) {
            log.warn("[SqlProbeService] 超时 historyId={} cause={}", historyId, ex.getMessage());
            updateTerminal(historyId, "TIMEOUT", null,
                    (int) (System.currentTimeMillis() - startMs), ex.getMessage());
            safelyAudit(empId, historyId, "FAILED", req.getRemark(), normalizedSql, ex.getMessage());
            throw new RptException(RptErrorCode.SQL_EXECUTION_TIMEOUT);
        } catch (SQLException ex) {
            log.warn("[SqlProbeService] 执行失败 historyId={} cause={}", historyId, ex.getMessage());
            updateTerminal(historyId, "FAILED", null,
                    (int) (System.currentTimeMillis() - startMs), ex.getMessage());
            safelyAudit(empId, historyId, "FAILED", req.getRemark(), normalizedSql, ex.getMessage());
            throw new com.bank.branch.platform.common.web.exception.BizException(
                    RptErrorCode.SQL_EXECUTION_FAILED.getCode(),
                    "SQL 执行失败：" + ex.getMessage());
        } finally {
            semaphore.release();
        }
    }

    @Override
    public PageResult<SqlProbeHistoryRespDTO> queryHistory(PageRequest page) {
        String empId = currentUserApi.getCurrentEmpId();
        long total = historyMapper.countByEmpId(empId);
        List<SqlProbeHistory> records = historyMapper.selectByEmpIdPaged(
                empId, page.getOffset(), page.getPageSize());
        List<SqlProbeHistoryRespDTO> dtos = records.stream().map(this::toDto).toList();
        Map<String, String> nameCache = new HashMap<>();
        dtos.forEach(d -> {
            // 列表 sqlText 截断 200 字
            if (d.getSqlText() != null && d.getSqlText().length() > 200) {
                d.setSqlText(d.getSqlText().substring(0, 200) + "...");
            }
            // 操作人展示为用户名称（按 empId 缓存解析）
            d.setEmpName(resolveEmpName(d.getEmpId(), nameCache));
        });
        return PageResult.of(page.getPageNo(), page.getPageSize(), total, dtos);
    }

    /** 把 empId 解析成用户名称，按 map 缓存避免同页重复查询；解析失败回退 empId. */
    private String resolveEmpName(String empId, Map<String, String> cache) {
        if (!StringUtils.hasText(empId)) return empId;
        return cache.computeIfAbsent(empId, id -> {
            try {
                String name = userApi.getUserName(id);
                return StringUtils.hasText(name) ? name : id;
            } catch (RuntimeException e) {
                log.warn("[SqlProbeService] 解析操作人名称失败 empId={} cause={}", id, e.getMessage());
                return id;
            }
        });
    }

    @Override
    public SqlProbeHistoryRespDTO getHistoryDetail(String id) {
        SqlProbeHistory hist = historyMapper.selectById(id);
        if (hist == null) {
            throw new RptException(RptErrorCode.SAVED_QUERY_NOT_FOUND);
        }
        // 仅本人可查（empId 不一致 → RPT-40302 无权访问）
        String currentEmp = currentUserApi.getCurrentEmpId();
        if (!StringUtils.hasText(hist.getEmpId()) || !hist.getEmpId().equals(currentEmp)) {
            throw new RptException(RptErrorCode.SQL_PROBE_NO_ACCESS);
        }
        SqlProbeHistoryRespDTO dto = toDto(hist);
        dto.setEmpName(resolveEmpName(hist.getEmpId(), new HashMap<>()));
        return dto;
    }

    @Override
    public SchemaWhitelistRespDTO getSchemaWhitelist() {
        // 白名单全部转小写返回，方便前端高亮匹配
        List<String> tables = whitelistTablesView.stream()
                .map(s -> s.trim().toLowerCase(Locale.ROOT))
                .sorted()
                .toList();
        List<String> kw = forbiddenKeywordsView.stream()
                .map(s -> s.trim().toUpperCase(Locale.ROOT))
                .sorted()
                .toList();
        return SchemaWhitelistRespDTO.builder()
                .tables(tables)
                .forbiddenKeywords(kw)
                .maxRows(maxRowsView)
                .maxSubqueryDepth(maxSubqueryDepthView)
                .maxSqlLength(maxSqlLengthView)
                .build();
    }

    @Override
    public String createExport(SqlProbeExportReqDTO req) {
        // 访问控制交由菜单授权（@BizAuth）；不再做角色白名单校验
        String empId = currentUserApi.getCurrentEmpId();
        int exportCount = normalizeExportCount(req.getExportCount());

        // 校验 + 标准化 SQL（失败直接抛，不建任务）
        SqlSafeResult safe = validator.validateAndNormalize(req.getSql());
        String normalizedSql = safe.getNormalizedSql();

        // 插入 RUNNING 占位任务
        String taskId = UUID.randomUUID().toString().replace("-", "");
        SqlProbeExportTask task = new SqlProbeExportTask();
        task.setId(taskId);
        task.setEmpId(empId);
        task.setSqlText(normalizedSql);
        task.setRemark(req.getRemark());
        task.setStatus("RUNNING");
        task.setCreatedTime(LocalDateTime.now());
        exportTaskMapper.insert(task);

        // 提交后台线程异步执行（提交失败兜底标记 FAILED）
        try {
            exportExecutor.execute(() -> runExport(taskId, empId, normalizedSql, req.getRemark(), exportCount));
        } catch (RuntimeException e) {
            log.error("[SqlProbeService.createExport] 任务提交失败 taskId={}", taskId, e);
            updateExportTerminal(taskId, "FAILED", null, null, null, "任务启动失败：" + e.getMessage());
            throw new RptException(RptErrorCode.EXPORT_START_FAILED);
        }
        log.info("[SqlProbeService.createExport] 已创建导出任务 taskId={} empId={}", taskId, empId);
        return taskId;
    }

    /**
     * 后台执行 SQL 导出：按请求上限跑查询 → 以 ResultSet 分批写临时 xlsx/zip → 上传治理中心 OBS，
     * 任务表 FILE_CONTENT 仅保存文件 ID 引用。
     * <p>任何异常都吞掉并落 FAILED，避免线程池工作线程因未捕获异常中断。</p>
     */
    void runExport(String taskId, String empId, String normalizedSql, String remark, int exportCount) {
        long startMs = System.currentTimeMillis();
        if (!semaphore.tryAcquire()) {
            log.warn("[SqlProbeService.runExport] 并发数超限（{}），taskId={}", CONCURRENT_LIMIT, taskId);
            updateExportTerminal(taskId, "FAILED", null, null, null, "并发数超限，请稍后重试");
            return;
        }
        ExportBuildResult built = null;
        String uploadedFileId = null;
        boolean uploadedFileNewlyCreated = false;
        boolean taskReferenceSaved = false;
        // 分阶段日志：内网若卡住，日志能定位卡在 取连接/查询/写Excel/写库 哪一步
        try (Connection conn = readOnlyDataSource.getConnection();
             PreparedStatement stmt = conn.prepareStatement(normalizedSql)) {
            log.info("[SqlProbeService.runExport] taskId={} 已取连接 ({}ms)", taskId, System.currentTimeMillis() - startMs);
            try {
                conn.setReadOnly(true);
            } catch (SQLException ignore) {
                // 部分实现不支持，忽略
            }
            stmt.setQueryTimeout(QUERY_TIMEOUT_SEC);
            stmt.setMaxRows(exportCount);
            try (ResultSet rs = stmt.executeQuery()) {
                List<String> columns = readColumns(rs);
                built = buildExportFile(rs, columns, exportCount);
                log.info("[SqlProbeService.runExport] taskId={} 查询完成 rows={} ({}ms)", taskId,
                        built.rowCount(), System.currentTimeMillis() - startMs);
                String suffix = built.chunkCount() > 1 ? ".zip" : ".xlsx";
                String fileName = "SQL探查导出_" + taskId.substring(0, 8) + suffix;
                String contentType = contentTypeForFileName(fileName);
                PathMultipartFile uploadFile = new PathMultipartFile(
                        built.contentPath(), "file", fileName, contentType);
                FileObjectDTO uploaded = fileApi.upload(uploadFile, empId, SQL_PROBE_FILE_CATEGORY);
                if (uploaded == null || !StringUtils.hasText(uploaded.getId())) {
                    throw new IllegalStateException("治理中心未返回 SQL 探查导出文件 ID");
                }
                uploadedFileId = uploaded.getId();
                uploadedFileNewlyCreated = Boolean.TRUE.equals(uploaded.getNewlyCreated());
                // 先建立业务关联：FileApi 按 MD5 去重，未知对象是否为本次新建，不能无条件 deleteFile。
                fileApi.bindFile("SQL_PROBE_EXPORT", taskId, uploadedFileId, "RESULT");
                byte[] fileReference = encodeObsFileReference(uploadedFileId);
                if (!updateExportTerminal(taskId, "SUCCESS", built.rowCount(), fileName,
                        fileReference, null)) {
                    throw new IllegalStateException("SQL 探查导出任务终态写库失败");
                }
                taskReferenceSaved = true;
                safelyAudit(empId, taskId, "SUCCESS", remark, normalizedSql, null);
                log.info("[SqlProbeService.runExport] 导出成功 taskId={} rows={} chunks={} bytes={} elapsedMs={}",
                        taskId, built.rowCount(), built.chunkCount(),
                        Files.size(built.contentPath()), System.currentTimeMillis() - startMs);
            }
        } catch (Throwable ex) {
            // 捕获 Throwable（含 Error，如 OOM/临时盘问题），确保任何失败都落 FAILED 而非永久 RUNNING
            log.warn("[SqlProbeService.runExport] 导出失败 taskId={} ({}ms) cause={}",
                    taskId, System.currentTimeMillis() - startMs, ex.toString());
            if (uploadedFileId != null && !taskReferenceSaved) {
                if (uploadedFileNewlyCreated) {
                    safelyDeleteUploadedFile(uploadedFileId);
                } else {
                    log.error("[SqlProbeService.runExport] 任务终态写库失败，保留已绑定 OBS 文件以避免误删共享对象 "
                                    + "taskId={} fileId={}", taskId, uploadedFileId);
                }
            }
            updateExportTerminal(taskId, "FAILED", null, null, null, String.valueOf(ex.getMessage()));
            safelyAudit(empId, taskId, "FAILED", remark, normalizedSql, ex.getMessage());
        } finally {
            if (built != null) {
                cleanupTempDir(built.tempDir());
            }
            semaphore.release();
        }
    }

    @Override
    public PageResult<SqlProbeExportTaskRespDTO> listExportTasks(PageRequest page) {
        String empId = currentUserApi.getCurrentEmpId();
        Page<SqlProbeExportTask> mapperPage = new Page<>(page.getPageNo(), page.getPageSize());
        com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<SqlProbeExportTask> qw =
                new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<>();
        // 列表只取轻量列，显式排除 FILE_CONTENT 大字段，避免把每行 xlsx 字节读进内存
        qw.select(SqlProbeExportTask::getId, SqlProbeExportTask::getEmpId, SqlProbeExportTask::getSqlText,
                  SqlProbeExportTask::getRemark, SqlProbeExportTask::getStatus, SqlProbeExportTask::getRowCount,
                  SqlProbeExportTask::getFileName, SqlProbeExportTask::getErrorMsg,
                  SqlProbeExportTask::getCreatedTime, SqlProbeExportTask::getFinishedTime)
          .eq(SqlProbeExportTask::getEmpId, empId)
          .orderByDesc(SqlProbeExportTask::getCreatedTime);
        Page<SqlProbeExportTask> result = exportTaskMapper.selectPage(mapperPage, qw);
        List<SqlProbeExportTaskRespDTO> records = result.getRecords().stream()
                .map(this::toExportDto)
                .toList();
        return PageResult.of(page.getPageNo(), page.getPageSize(), result.getTotal(), records);
    }

    @Override
    public SqlProbeExportFileDTO getExportFile(String taskId) {
        SqlProbeExportTask task = exportTaskMapper.selectById(taskId);
        if (task == null) {
            throw new RptException(RptErrorCode.EXPORT_TASK_NOT_FOUND_OR_EXPIRED);
        }
        // 仅本人可下载自己的导出任务
        String currentEmp = currentUserApi.getCurrentEmpId();
        if (!StringUtils.hasText(task.getEmpId()) || !task.getEmpId().equals(currentEmp)) {
            throw new RptException(RptErrorCode.EXPORT_DOWNLOAD_FORBIDDEN);
        }
        if (!"SUCCESS".equals(task.getStatus()) || task.getFileContent() == null) {
            throw new RptException(RptErrorCode.EXPORT_TASK_NOT_READY);
        }
        String fileName = StringUtils.hasText(task.getFileName()) ? task.getFileName() : "sql-export.xlsx";
        String fileId = decodeObsFileReference(task.getFileContent());
        if (fileId != null) {
            return SqlProbeExportFileDTO.builder()
                    .fileName(fileName)
                    .fileId(fileId)
                    .contentType(contentTypeForFileName(fileName))
                    .build();
        }
        // 兼容迁移前任务：FILE_CONTENT 仍是完整 BLOB 时由 Controller 直接回写。
        return SqlProbeExportFileDTO.builder()
                .fileName(fileName)
                .contentType(contentTypeForFileName(fileName))
                .fileSize((long) task.getFileContent().length)
                .content(task.getFileContent())
                .build();
    }

    /** 更新导出任务终态（updateById NOT_NULL 策略：null 字段不覆盖）。 */
    private boolean updateExportTerminal(String taskId, String status, Integer rowCount,
                                         String fileName, byte[] fileContent, String errorMsg) {
        try {
            SqlProbeExportTask upd = new SqlProbeExportTask();
            upd.setId(taskId);
            upd.setStatus(status);
            upd.setRowCount(rowCount);
            upd.setFileName(fileName);
            upd.setFileContent(fileContent);
            upd.setErrorMsg(errorMsg != null && errorMsg.length() > 500 ? errorMsg.substring(0, 500) : errorMsg);
            upd.setFinishedTime(LocalDateTime.now());
            return exportTaskMapper.updateById(upd) > 0;
        } catch (RuntimeException e) {
            log.error("[SqlProbeService.updateExportTerminal] 更新失败 taskId={} status={} cause={}",
                    taskId, status, e.getMessage());
            return false;
        }
    }

    private SqlProbeExportTaskRespDTO toExportDto(SqlProbeExportTask e) {
        String sql = e.getSqlText();
        if (sql != null && sql.length() > 200) {
            sql = sql.substring(0, 200) + "...";
        }
        return SqlProbeExportTaskRespDTO.builder()
                .id(e.getId())
                .sqlText(sql)
                .remark(e.getRemark())
                .status(e.getStatus())
                .rowCount(e.getRowCount())
                .fileName(e.getFileName())
                .errorMsg(e.getErrorMsg())
                .createdTime(e.getCreatedTime())
                .finishedTime(e.getFinishedTime())
                .build();
    }

    /* =====================================================================
     * 辅助方法
     * ===================================================================== */

    /**
     * 直接从 ResultSet 分批构建导出文件，避免先把所有结果行装入 List/Map。
     * 每个工作簿最多 10000 行数据；单个工作簿直接返回 xlsx，多个工作簿合并为 zip。
     */
    private ExportBuildResult buildExportFile(ResultSet rs, List<String> columns, int exportCount)
            throws SQLException, IOException {
        List<Path> tempParts = new ArrayList<>();
        Path tempDir = Files.createTempDirectory("sql-probe-export-");
        try {
            // 每个工作簿直接写受控临时文件；最终成品也留在同一临时目录供 FileApi 流式上传。
            Path firstPart = Files.createTempFile(tempDir, "part-001-", ".xlsx");
            tempParts.add(firstPart);
            boolean hasCurrentRow = rs.next();
            ChunkBuildState first;
            try (OutputStream out = Files.newOutputStream(firstPart)) {
                first = writeExcelChunk(rs, columns, exportCount, 0, hasCurrentRow, out);
            }
            int rowCount = first.rowCount();
            hasCurrentRow = first.hasCurrentRow();
            // 不超过一万行（含空结果）直接使用单个 xlsx 临时文件。
            if (!hasCurrentRow) {
                return new ExportBuildResult(rowCount, 1, firstPart, tempDir);
            }

            int partNo = 2;
            while (hasCurrentRow && rowCount < exportCount) {
                Path part = Files.createTempFile(tempDir, String.format("part-%03d-", partNo), ".xlsx");
                tempParts.add(part);
                try (OutputStream out = Files.newOutputStream(part, StandardOpenOption.TRUNCATE_EXISTING)) {
                    ChunkBuildState state = writeExcelChunk(rs, columns, exportCount, rowCount,
                            hasCurrentRow, out);
                    rowCount = state.rowCount();
                    hasCurrentRow = state.hasCurrentRow();
                }
                partNo++;
            }
            Path zipPath = tempDir.resolve("sql-probe-export.zip");
            zipTempParts(tempParts, zipPath);
            return new ExportBuildResult(rowCount, tempParts.size(), zipPath, tempDir);
        } catch (IOException | SQLException | RuntimeException | Error ex) {
            cleanupTempDir(tempDir);
            throw ex;
        }
    }

    /** 将当前 ResultSet 游标开始的至多一万行直接写入给定输出流。 */
    private ChunkBuildState writeExcelChunk(ResultSet rs, List<String> columns, int exportCount,
                                            int rowCount, boolean hasCurrentRow, OutputStream out)
            throws SQLException, IOException {
        int chunkRows = 0;
        try (org.apache.poi.xssf.usermodel.XSSFWorkbook workbook =
                     new org.apache.poi.xssf.usermodel.XSSFWorkbook()) {
            org.apache.poi.ss.usermodel.Sheet sheet = workbook.createSheet("查询结果");
            org.apache.poi.ss.usermodel.Row header = sheet.createRow(0);
            for (int c = 0; c < columns.size(); c++) {
                header.createCell(c).setCellValue(columns.get(c));
            }
            while (hasCurrentRow && chunkRows < EXCEL_CHUNK_ROWS && rowCount < exportCount) {
                org.apache.poi.ss.usermodel.Row row = sheet.createRow(chunkRows + 1);
                for (int c = 0; c < columns.size(); c++) {
                    Object value = rs.getObject(c + 1);
                    row.createCell(c).setCellValue(value != null ? String.valueOf(value) : "");
                }
                chunkRows++;
                rowCount++;
                // 达到请求上限时不再向 ResultSet 前移；JDBC setMaxRows 仍是第一层限制。
                hasCurrentRow = rowCount < exportCount && rs.next();
            }
            workbook.write(out);
        }
        return new ChunkBuildState(rowCount, hasCurrentRow);
    }

    private void zipTempParts(List<Path> parts, Path zipPath) throws IOException {
        try (OutputStream out = Files.newOutputStream(zipPath);
             ZipOutputStream zip = new ZipOutputStream(out)) {
            for (int i = 0; i < parts.size(); i++) {
                zip.putNextEntry(new ZipEntry("SQL探查导出_" + (i + 1) + ".xlsx"));
                try (InputStream in = Files.newInputStream(parts.get(i))) {
                    in.transferTo(zip);
                }
                zip.closeEntry();
            }
            zip.finish();
        }
    }

    private void cleanupTempDir(Path tempDir) {
        if (tempDir != null) {
            try (Stream<Path> paths = Files.walk(tempDir)) {
                paths.sorted(Comparator.reverseOrder()).forEach(path -> {
                    try {
                        Files.deleteIfExists(path);
                    } catch (IOException e) {
                        log.warn("[SqlProbeService.runExport] 临时文件清理失败 path={} cause={}",
                                path, e.getMessage());
                    }
                });
            } catch (IOException e) {
                log.warn("[SqlProbeService.runExport] 临时目录扫描失败 path={} cause={}",
                        tempDir, e.getMessage());
            }
        }
    }

    private void safelyDeleteUploadedFile(String fileId) {
        try {
            fileApi.deleteFile(fileId);
        } catch (RuntimeException cleanupFailure) {
            log.error("[SqlProbeService.runExport] 新建 OBS 文件补偿删除失败 fileId={}",
                    fileId, cleanupFailure);
        }
    }

    private int normalizeExportCount(Integer exportCount) {
        int count = exportCount == null ? DEFAULT_EXPORT_COUNT : exportCount;
        if (count < 1 || count > MAX_EXPORT_COUNT) {
            throw new RptException(RptErrorCode.EXPORT_START_FAILED,
                    "exportCount 必须在 1 到 " + MAX_EXPORT_COUNT + " 之间");
        }
        return count;
    }

    private byte[] encodeObsFileReference(String fileId) {
        return (OBS_FILE_REFERENCE_MAGIC + fileId).getBytes(java.nio.charset.StandardCharsets.UTF_8);
    }

    private String decodeObsFileReference(byte[] content) {
        if (content == null || content.length <= OBS_FILE_REFERENCE_MAGIC.length()) {
            return null;
        }
        String value = new String(content, java.nio.charset.StandardCharsets.UTF_8);
        if (!value.startsWith(OBS_FILE_REFERENCE_MAGIC)) {
            return null;
        }
        String fileId = value.substring(OBS_FILE_REFERENCE_MAGIC.length()).trim();
        return StringUtils.hasText(fileId) ? fileId : null;
    }

    private String contentTypeForFileName(String fileName) {
        return fileName != null && fileName.toLowerCase(Locale.ROOT).endsWith(".zip")
                ? "application/zip"
                : "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet";
    }

    private record ExportBuildResult(int rowCount, int chunkCount, Path contentPath, Path tempDir) {
    }

    private record ChunkBuildState(int rowCount, boolean hasCurrentRow) {
    }

    private List<String> readColumns(ResultSet rs) throws SQLException {
        ResultSetMetaData md = rs.getMetaData();
        int n = md.getColumnCount();
        List<String> cols = new ArrayList<>(n);
        for (int i = 1; i <= n; i++) {
            cols.add(md.getColumnLabel(i));
        }
        return cols;
    }

    private List<Map<String, Object>> readRows(ResultSet rs, List<String> columns) throws SQLException {
        // 行数限制已取消：读取全部结果行（仅受 30s 查询超时约束）
        List<Map<String, Object>> out = new ArrayList<>();
        while (rs.next()) {
            Map<String, Object> row = new HashMap<>(columns.size() * 2);
            for (int i = 0; i < columns.size(); i++) {
                row.put(columns.get(i), rs.getObject(i + 1));
            }
            out.add(row);
        }
        return out;
    }

    private void updateTerminal(String historyId, String status, Integer rowCount,
                                Integer executionTimeMs, String errorMsg) {
        try {
            SqlProbeHistory upd = new SqlProbeHistory();
            upd.setId(historyId);
            upd.setStatus(status);
            upd.setRowCount(rowCount);
            upd.setExecutionTimeMs(executionTimeMs);
            upd.setErrorMsg(errorMsg);
            historyMapper.updateTerminalStatus(upd);
        } catch (RuntimeException e) {
            // 不让审计/历史更新失败掩盖原始错误
            log.error("[SqlProbeService] 历史更新失败 historyId={} status={} cause={}",
                    historyId, status, e.getMessage());
        }
    }

    /** 安全调用 AuditApi.log（任何异常仅 warn 不中断主流程，符合"审计失败不阻塞业务"原则）. */
    private void safelyAudit(String empId, String historyId, String resultStatus,
                              String reason, String sqlText, String errorMsg) {
        try {
            AuditLogCmd cmd = AuditLogCmd.builder()
                    .empId(empId)
                    .bizType("REPORT")
                    .bizAction("EXECUTE_SQL")
                    .resourceUrl("/api/reports/sql-probe/execute")
                    .requestMethod("POST")
                    .requestParams("historyId=" + historyId
                            + ", sql=" + truncateForAudit(sqlText))
                    .responseStatus("SUCCESS".equals(resultStatus) ? 200 : 500)
                    .errorMsg(errorMsg)
                    .reason(reason)
                    .build();
            auditApi.log(cmd);
        } catch (RuntimeException e) {
            log.warn("[SqlProbeService] AuditApi.log 失败 historyId={} cause={}",
                    historyId, e.getMessage());
        }
    }

    private String truncateForAudit(String sql) {
        if (sql == null) {
            return null;
        }
        return sql.length() > 200 ? sql.substring(0, 200) + "..." : sql;
    }

    private SqlProbeHistoryRespDTO toDto(SqlProbeHistory e) {
        return SqlProbeHistoryRespDTO.builder()
                .id(e.getId())
                .empId(e.getEmpId())
                .sqlText(e.getSqlText())
                .remark(e.getRemark())
                .rowCount(e.getRowCount())
                .executionTimeMs(e.getExecutionTimeMs())
                .status(e.getStatus())
                .errorMsg(e.getErrorMsg())
                .createdTime(e.getCreatedTime())
                .build();
    }
}
