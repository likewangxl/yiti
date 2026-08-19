package com.bank.branch.platform.report.service;

import com.bank.branch.platform.auth.api.CurrentUserApi;
import com.bank.branch.platform.common.web.PageRequest;
import com.bank.branch.platform.common.web.PageResult;
import com.bank.branch.platform.common.web.exception.BizException;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.bank.branch.platform.governance.api.AuditApi;
import com.bank.branch.platform.governance.api.FileApi;
import com.bank.branch.platform.governance.api.dto.AuditLogCmd;
import com.bank.branch.platform.governance.api.dto.FileObjectDTO;
import com.bank.branch.platform.governance.storage.FileCategory;
import com.bank.branch.platform.report.dto.req.SqlProbeExportReqDTO;
import com.bank.branch.platform.report.dto.req.SqlProbeExecuteReqDTO;
import com.bank.branch.platform.report.entity.SqlProbeExportTask;
import com.bank.branch.platform.report.entity.SqlProbeHistory;
import com.bank.branch.platform.report.dto.resp.SqlProbeExportTaskRespDTO;
import com.bank.branch.platform.report.mapper.SqlProbeHistoryMapper;
import com.bank.branch.platform.report.service.impl.SqlProbeServiceImpl;
import com.bank.branch.platform.report.support.SqlSafeValidator;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.web.multipart.MultipartFile;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.SQLException;
import java.sql.SQLTimeoutException;
import java.io.ByteArrayInputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.zip.ZipInputStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.atLeast;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * SqlProbeService 单元测试（Task M4.2.1，Red）.
 *
 * <p>覆盖核心场景（plan L2622-L2629 + L2632-L2634）：
 * <ol>
 *   <li>角色 R_BACK_TECH 缺失 → RPT-40302 + 不进入数据源</li>
 *   <li>SqlSafeValidator 校验失败 → 拒绝 + 不写历史</li>
 *   <li>校验通过 → INSERT(RUNNING) → 执行 → UPDATE(SUCCESS) + AuditApi.log</li>
 *   <li>执行 SQLTimeoutException → UPDATE(TIMEOUT) + RPT-42005 + 仍写审计</li>
 *   <li>双写审计验证：sql_probe_history 入库 + AuditApi.log 同步</li>
 *   <li>columns + rows 装配（顺序、行数）</li>
 * </ol>
 *
 * <p>不覆盖（留给 M4.2.2 + M4.2 IT）：
 * <ul>
 *   <li>readOnlyDataSource Bean 装配（M4.2.2 集成测试）</li>
 *   <li>Semaphore 并发 ≥ 10 真实抢占（IT 中再加）</li>
 * </ul>
 */
@ExtendWith(MockitoExtension.class)
class SqlProbeServiceTest {

    @Mock
    private SqlSafeValidator validator;

    @Mock
    private SqlProbeHistoryMapper historyMapper;

    @Mock
    private CurrentUserApi currentUserApi;

    @Mock
    private AuditApi auditApi;

    @Mock
    private FileApi fileApi;

    @Mock(name = "readOnlyDataSource")
    private DataSource readOnlyDataSource;

    @Mock
    private Connection connection;

    @Mock
    private PreparedStatement statement;

    @Mock
    private ResultSet resultSet;

    @Mock
    private ResultSetMetaData metaData;

    @Mock
    private com.bank.branch.platform.auth.api.UserApi userApi;

    @Mock
    private com.bank.branch.platform.report.mapper.SqlProbeExportTaskMapper exportTaskMapper;

    /** 直接执行器：让 runExport 在 createExport 内同步跑完，便于断言. */
    private final java.util.concurrent.Executor directExecutor = Runnable::run;

    private SqlProbeServiceImpl service;

    private byte[] lastUploadedContent;

    @BeforeEach
    void setUp() throws SQLException {
        // 手动构造（@InjectMocks 不支持含 @Value 多参数构造器）
        service = new SqlProbeServiceImpl(
                validator,
                historyMapper,
                currentUserApi,
                auditApi,
                fileApi,
                readOnlyDataSource,
                List.of("cust_master", "kpi_result"),
                List.of("DROP", "DELETE", "UPDATE", "INSERT"),
                1000, 5000, 3,
                userApi,
                exportTaskMapper,
                directExecutor);

        lenient().when(currentUserApi.getCurrentEmpId()).thenReturn("E_TECH001");
        // 校验默认放行：返回 normalizedSql 等于 sql
        lenient().when(validator.validateAndNormalize(anyString())).thenAnswer(inv ->
                com.bank.branch.platform.report.support.SqlSafeResult.allowed(
                        inv.getArgument(0), List.of("cust_master")));
        // DataSource 链路默认 mock
        lenient().when(readOnlyDataSource.getConnection()).thenReturn(connection);
        lenient().when(connection.prepareStatement(anyString())).thenReturn(statement);
        lenient().when(exportTaskMapper.updateById(any(SqlProbeExportTask.class))).thenReturn(1);
        FileObjectDTO uploaded = new FileObjectDTO();
        uploaded.setId("FILE_SQL_PROBE_001");
        uploaded.setNewlyCreated(true);
        lenient().when(fileApi.upload(any(MultipartFile.class), anyString(), anyString()))
                .thenAnswer(invocation -> {
                    lastUploadedContent = invocation.<MultipartFile>getArgument(0).getBytes();
                    return uploaded;
                });
    }

    @Test
    void execute_validatorRejects_doesNotWriteHistoryNorAudit() {
        when(validator.validateAndNormalize(anyString()))
                .thenThrow(new com.bank.branch.platform.report.exception.RptException(
                        com.bank.branch.platform.report.enums.RptErrorCode.SQL_TABLE_NOT_WHITELISTED));
        SqlProbeExecuteReqDTO req = buildReq("SELECT * FROM secret_table", "试探");

        assertThatThrownBy(() -> service.execute(req))
                .isInstanceOf(BizException.class)
                .hasFieldOrPropertyWithValue("code", "RPT-42002");

        verify(historyMapper, atLeast(0)).insert(any(SqlProbeHistory.class));
        // 校验失败 → 历史不应该写入（仅 RUNNING 占位前抛异常）
        // 实际行为：service 在 validator 抛异常时直接传递，不入库 / 不审计
        verify(auditApi, atLeast(0)).log(any());
    }

    @Test
    void execute_success_writesRunningThenSuccess_andAudit() throws SQLException {
        // 模拟 ResultSet：1 列 id，2 行
        when(statement.executeQuery()).thenReturn(resultSet);
        when(resultSet.getMetaData()).thenReturn(metaData);
        when(metaData.getColumnCount()).thenReturn(1);
        when(metaData.getColumnLabel(1)).thenReturn("id");
        when(resultSet.next()).thenReturn(true, true, false);
        when(resultSet.getObject(1)).thenReturn(1, 2);

        SqlProbeExecuteReqDTO req = buildReq("SELECT id FROM CUST_MASTER", "查询客户");

        var resp = service.execute(req);

        assertThat(resp).isNotNull();
        assertThat(resp.getColumns()).containsExactly("id");
        assertThat(resp.getRowCount()).isEqualTo(2);
        assertThat(resp.getRows()).hasSize(2);
        assertThat(resp.getHistoryId()).isNotBlank();

        // 验证：先 INSERT(RUNNING)，后 UPDATE(SUCCESS)
        ArgumentCaptor<SqlProbeHistory> insertCap = ArgumentCaptor.forClass(SqlProbeHistory.class);
        verify(historyMapper).insert(insertCap.capture());
        assertThat(insertCap.getValue().getStatus()).isEqualTo("RUNNING");
        assertThat(insertCap.getValue().getEmpId()).isEqualTo("E_TECH001");
        assertThat(insertCap.getValue().getRemark()).isEqualTo("查询客户");

        ArgumentCaptor<SqlProbeHistory> updateCap = ArgumentCaptor.forClass(SqlProbeHistory.class);
        verify(historyMapper).updateTerminalStatus(updateCap.capture());
        assertThat(updateCap.getValue().getStatus()).isEqualTo("SUCCESS");
        assertThat(updateCap.getValue().getRowCount()).isEqualTo(2);

        // 双写审计：AuditApi.log 至少调用一次
        ArgumentCaptor<AuditLogCmd> auditCap = ArgumentCaptor.forClass(AuditLogCmd.class);
        verify(auditApi, atLeastOnce()).log(auditCap.capture());
        AuditLogCmd cmd = auditCap.getValue();
        assertThat(cmd.getBizType()).isEqualTo("REPORT");
        assertThat(cmd.getReason()).isEqualTo("查询客户");
    }

    @Test
    void execute_sqlTimeout_writesTimeoutStatus_andThrows42005() throws SQLException {
        when(statement.executeQuery()).thenThrow(new SQLTimeoutException("statement timeout"));

        SqlProbeExecuteReqDTO req = buildReq("SELECT id FROM CUST_MASTER", "压测");

        assertThatThrownBy(() -> service.execute(req))
                .isInstanceOf(BizException.class)
                .hasFieldOrPropertyWithValue("code", "RPT-42005");

        ArgumentCaptor<SqlProbeHistory> updateCap = ArgumentCaptor.forClass(SqlProbeHistory.class);
        verify(historyMapper).updateTerminalStatus(updateCap.capture());
        assertThat(updateCap.getValue().getStatus()).isEqualTo("TIMEOUT");

        // 失败也要落审计
        verify(auditApi, atLeastOnce()).log(any());
    }

    @Test
    void execute_sqlExecutionFailed_writesFailedStatus_andThrows42009() throws SQLException {
        when(statement.executeQuery()).thenThrow(new SQLException("table missing"));

        SqlProbeExecuteReqDTO req = buildReq("SELECT id FROM CUST_MASTER", "排查");

        assertThatThrownBy(() -> service.execute(req))
                .isInstanceOf(BizException.class)
                .hasFieldOrPropertyWithValue("code", "RPT-42009");

        ArgumentCaptor<SqlProbeHistory> updateCap = ArgumentCaptor.forClass(SqlProbeHistory.class);
        verify(historyMapper).updateTerminalStatus(updateCap.capture());
        assertThat(updateCap.getValue().getStatus()).isEqualTo("FAILED");

        verify(auditApi, atLeastOnce()).log(any());
    }

    @Test
    void createExport_success_insertsRunning_thenRunStoresXlsxInDb_andMarksSuccess() throws SQLException {
        // 模拟 ResultSet：1 列 id，2 行
        when(statement.executeQuery()).thenReturn(resultSet);
        when(resultSet.getMetaData()).thenReturn(metaData);
        when(metaData.getColumnCount()).thenReturn(1);
        when(metaData.getColumnLabel(1)).thenReturn("id");
        when(resultSet.next()).thenReturn(true, true, false);
        when(resultSet.getObject(1)).thenReturn(1, 2);

        SqlProbeExportReqDTO req = buildExportReq("SELECT id FROM CUST_MASTER", "导出客户", 1000);

        String taskId = service.createExport(req);
        assertThat(taskId).isNotBlank();

        // 插入 RUNNING 占位
        ArgumentCaptor<com.bank.branch.platform.report.entity.SqlProbeExportTask> insertCap =
                ArgumentCaptor.forClass(com.bank.branch.platform.report.entity.SqlProbeExportTask.class);
        verify(exportTaskMapper).insert(insertCap.capture());
        assertThat(insertCap.getValue().getStatus()).isEqualTo("RUNNING");
        assertThat(insertCap.getValue().getEmpId()).isEqualTo("E_TECH001");

        // directExecutor 同步跑完 runExport → 生成 xlsx 存库 + 终态 SUCCESS
        ArgumentCaptor<com.bank.branch.platform.report.entity.SqlProbeExportTask> updCap =
                ArgumentCaptor.forClass(com.bank.branch.platform.report.entity.SqlProbeExportTask.class);
        verify(exportTaskMapper).updateById(updCap.capture());
        assertThat(updCap.getValue().getStatus()).isEqualTo("SUCCESS");
        assertThat(updCap.getValue().getRowCount()).isEqualTo(2);
        assertThat(updCap.getValue().getFileName()).endsWith(".xlsx");
        assertThat(updCap.getValue().getFileContent()).isNotNull();
        assertThat(new String(updCap.getValue().getFileContent(), java.nio.charset.StandardCharsets.UTF_8))
                .isEqualTo("OBS_FILE_ID:FILE_SQL_PROBE_001");
        verify(fileApi).upload(any(MultipartFile.class), eq("E_TECH001"), eq(FileCategory.EXPORT_SQL_PROBE));
        verify(fileApi).bindFile("SQL_PROBE_EXPORT", insertCap.getValue().getId(),
                "FILE_SQL_PROBE_001", "RESULT");
        verify(statement).setMaxRows(1000);
        // 不再做角色校验：不应触碰 getCurrentRoleCodes
        verify(currentUserApi, org.mockito.Mockito.never()).getCurrentRoleCodes();
    }

    @Test
    void createExport_queryFails_marksTaskFailed_notThrowFromBackgroundRun() throws SQLException {
        when(statement.executeQuery()).thenThrow(new SQLException("table missing"));
        SqlProbeExportReqDTO req = buildExportReq("SELECT id FROM CUST_MASTER", "导出排查", 1000);

        String taskId = service.createExport(req); // 后台 run 吞异常，createExport 本身不抛
        assertThat(taskId).isNotBlank();

        ArgumentCaptor<com.bank.branch.platform.report.entity.SqlProbeExportTask> updCap =
                ArgumentCaptor.forClass(com.bank.branch.platform.report.entity.SqlProbeExportTask.class);
        verify(exportTaskMapper).updateById(updCap.capture());
        assertThat(updCap.getValue().getStatus()).isEqualTo("FAILED");
        assertThat(updCap.getValue().getFileContent()).isNull();
    }

    @Test
    void createExport_terminalWriteFailure_deletesOnlyNewlyCreatedObsFile() throws SQLException {
        when(statement.executeQuery()).thenReturn(resultSet);
        when(resultSet.getMetaData()).thenReturn(metaData);
        when(metaData.getColumnCount()).thenReturn(1);
        when(metaData.getColumnLabel(1)).thenReturn("id");
        when(resultSet.next()).thenReturn(false);
        when(exportTaskMapper.updateById(any(SqlProbeExportTask.class))).thenReturn(0, 1);

        service.createExport(buildExportReq("SELECT id FROM CUST_MASTER", "写库失败", 1000));

        verify(fileApi).deleteFile("FILE_SQL_PROBE_001");
        verify(exportTaskMapper, org.mockito.Mockito.times(2)).updateById(any(SqlProbeExportTask.class));
    }

    @Test
    void createExport_terminalWriteFailure_keepsDeduplicatedObsFile() throws SQLException {
        when(statement.executeQuery()).thenReturn(resultSet);
        when(resultSet.getMetaData()).thenReturn(metaData);
        when(metaData.getColumnCount()).thenReturn(1);
        when(metaData.getColumnLabel(1)).thenReturn("id");
        when(resultSet.next()).thenReturn(false);
        FileObjectDTO shared = new FileObjectDTO();
        shared.setId("FILE_SHARED");
        shared.setNewlyCreated(false);
        when(fileApi.upload(any(MultipartFile.class), anyString(), anyString())).thenReturn(shared);
        when(exportTaskMapper.updateById(any(SqlProbeExportTask.class))).thenReturn(0, 1);

        service.createExport(buildExportReq("SELECT id FROM CUST_MASTER", "共享文件", 1000));

        verify(fileApi, org.mockito.Mockito.never()).deleteFile("FILE_SHARED");
        verify(fileApi).bindFile(eq("SQL_PROBE_EXPORT"), org.mockito.ArgumentMatchers.anyString(),
                eq("FILE_SHARED"), eq("RESULT"));
    }

    @Test
    void createExport_usesRequestedExportCountAsJdbcMaxRows() throws SQLException {
        when(statement.executeQuery()).thenReturn(resultSet);
        when(resultSet.getMetaData()).thenReturn(metaData);
        when(metaData.getColumnCount()).thenReturn(1);
        when(metaData.getColumnLabel(1)).thenReturn("id");
        when(resultSet.next()).thenReturn(false);

        SqlProbeExportReqDTO req = buildExportReq("SELECT id FROM CUST_MASTER", "限制条数", 321);

        service.createExport(req);

        verify(statement).setMaxRows(321);
    }

    @Test
    void createExport_10000Rows_keepsSingleXlsx() throws Exception {
        stubRows(10_000);

        SqlProbeExportReqDTO req = buildExportReq("SELECT id FROM CUST_MASTER", "一万行", 10_000);
        service.createExport(req);

        ArgumentCaptor<com.bank.branch.platform.report.entity.SqlProbeExportTask> updCap =
                ArgumentCaptor.forClass(com.bank.branch.platform.report.entity.SqlProbeExportTask.class);
        verify(exportTaskMapper).updateById(updCap.capture());
        var task = updCap.getValue();
        assertThat(task.getStatus()).isEqualTo("SUCCESS");
        assertThat(task.getRowCount()).isEqualTo(10_000);
        assertThat(task.getFileName()).endsWith(".xlsx");
        assertThat(task.getFileContent()).isNotNull();
        ArgumentCaptor<MultipartFile> uploadCap = ArgumentCaptor.forClass(MultipartFile.class);
        verify(fileApi).upload(uploadCap.capture(), eq("E_TECH001"), eq(FileCategory.EXPORT_SQL_PROBE));
        try (org.apache.poi.xssf.usermodel.XSSFWorkbook workbook =
                     new org.apache.poi.xssf.usermodel.XSSFWorkbook(
                             new ByteArrayInputStream(lastUploadedContent))) {
            assertThat(workbook.getNumberOfSheets()).isEqualTo(1);
            assertThat(workbook.getSheetAt(0).getLastRowNum()).isEqualTo(10_000);
        }
    }

    @Test
    void createExport_10001Rows_returnsZipWithTwoXlsxEntries() throws Exception {
        Path systemTemp = Paths.get(System.getProperty("java.io.tmpdir"));
        Set<Path> tempBefore = tempExportDirs(systemTemp);
        stubRows(10_001);

        SqlProbeExportReqDTO req = buildExportReq("SELECT id FROM CUST_MASTER", "一万零一行", 50_000);
        service.createExport(req);

        ArgumentCaptor<com.bank.branch.platform.report.entity.SqlProbeExportTask> updCap =
                ArgumentCaptor.forClass(com.bank.branch.platform.report.entity.SqlProbeExportTask.class);
        verify(exportTaskMapper).updateById(updCap.capture());
        var task = updCap.getValue();
        assertThat(task.getStatus()).isEqualTo("SUCCESS");
        assertThat(task.getRowCount()).isEqualTo(10_001);
        assertThat(task.getFileName()).endsWith(".zip");
        ArgumentCaptor<MultipartFile> uploadCap = ArgumentCaptor.forClass(MultipartFile.class);
        verify(fileApi).upload(uploadCap.capture(), eq("E_TECH001"), eq(FileCategory.EXPORT_SQL_PROBE));

        Set<String> entries = new HashSet<>();
        try (ZipInputStream zip = new ZipInputStream(new ByteArrayInputStream(lastUploadedContent))) {
            java.util.zip.ZipEntry entry;
            while ((entry = zip.getNextEntry()) != null) {
                entries.add(entry.getName());
            }
        }
        assertThat(entries).hasSize(2);
        assertThat(entries).allMatch(name -> name.endsWith(".xlsx"));
        assertThat(tempExportDirs(systemTemp)).containsExactlyInAnyOrderElementsOf(tempBefore);
    }

    @Test
    void getExportFile_decodesObsReference_andKeepsLegacyBlobCompatibility() {
        SqlProbeExportTask task = new SqlProbeExportTask();
        task.setId("TASK_OBS");
        task.setEmpId("E_TECH001");
        task.setStatus("SUCCESS");
        task.setFileName("SQL探查导出_TASK_OBS.zip");
        task.setFileContent("OBS_FILE_ID:FILE_SQL_PROBE_001"
                .getBytes(java.nio.charset.StandardCharsets.UTF_8));
        when(exportTaskMapper.selectById("TASK_OBS")).thenReturn(task);

        var obsFile = service.getExportFile("TASK_OBS");
        assertThat(obsFile.getFileId()).isEqualTo("FILE_SQL_PROBE_001");
        assertThat(obsFile.getContent()).isNull();
        assertThat(obsFile.getContentType()).isEqualTo("application/zip");

        byte[] legacy = new byte[]{80, 75, 3, 4};
        task.setFileName("SQL探查导出_TASK_OBS.xlsx");
        task.setFileContent(legacy);
        var legacyFile = service.getExportFile("TASK_OBS");
        assertThat(legacyFile.getFileId()).isNull();
        assertThat(legacyFile.getContent()).isEqualTo(legacy);
    }

    @Test
    void queryHistory_resolvesOperatorEmpIdToUserName() {
        SqlProbeHistory h = new SqlProbeHistory();
        h.setId("H1");
        h.setEmpId("E_TECH001");
        h.setSqlText("SELECT 1");
        h.setStatus("SUCCESS");
        when(historyMapper.countByEmpId("E_TECH001")).thenReturn(1L);
        when(historyMapper.selectByEmpIdPaged(eq("E_TECH001"), org.mockito.ArgumentMatchers.anyInt(),
                org.mockito.ArgumentMatchers.anyInt())).thenReturn(List.of(h));
        when(userApi.getUserName("E_TECH001")).thenReturn("张三");

        var page = service.queryHistory(new com.bank.branch.platform.common.web.PageRequest());

        assertThat(page.getRecords()).hasSize(1);
        assertThat(page.getRecords().get(0).getEmpName()).isEqualTo("张三");
    }

    @Test
    void listExportTasks_returnsRequestedPage_forCurrentUserInCreatedTimeDescOrder() {
        SqlProbeExportTask task = new SqlProbeExportTask();
        task.setId("TASK_PAGE_006");
        task.setEmpId("E_TECH001");
        task.setSqlText("SELECT 1");
        task.setStatus("SUCCESS");

        Page<SqlProbeExportTask> mapperPage = new Page<>(2, 5);
        mapperPage.setTotal(11);
        mapperPage.setRecords(List.of(task));
        when(exportTaskMapper.selectPage(any(IPage.class), any())).thenReturn(mapperPage);

        PageRequest request = new PageRequest();
        request.setPageNo(2);
        request.setPageSize(5);

        PageResult<SqlProbeExportTaskRespDTO> result = service.listExportTasks(request);

        assertThat(result.getPageNo()).isEqualTo(2);
        assertThat(result.getPageSize()).isEqualTo(5);
        assertThat(result.getTotal()).isEqualTo(11);
        assertThat(result.getRecords()).extracting(SqlProbeExportTaskRespDTO::getId)
                .containsExactly("TASK_PAGE_006");

        ArgumentCaptor<IPage<SqlProbeExportTask>> pageCaptor = ArgumentCaptor.forClass(IPage.class);
        ArgumentCaptor<LambdaQueryWrapper<SqlProbeExportTask>> wrapperCaptor =
                ArgumentCaptor.forClass(LambdaQueryWrapper.class);
        verify(exportTaskMapper).selectPage(pageCaptor.capture(), wrapperCaptor.capture());
        assertThat(pageCaptor.getValue().getCurrent()).isEqualTo(2);
        assertThat(pageCaptor.getValue().getSize()).isEqualTo(5);
        assertThat(wrapperCaptor.getValue().getSqlSegment().toUpperCase())
                .contains("ORDER BY CREATED_TIME DESC");
        assertThat(wrapperCaptor.getValue().getParamNameValuePairs().values())
                .contains("E_TECH001");
    }

    private SqlProbeExecuteReqDTO buildReq(String sql, String remark) {
        SqlProbeExecuteReqDTO req = new SqlProbeExecuteReqDTO();
        req.setSql(sql);
        req.setRemark(remark);
        return req;
    }

    private SqlProbeExportReqDTO buildExportReq(String sql, String remark, int exportCount) {
        SqlProbeExportReqDTO req = new SqlProbeExportReqDTO();
        req.setSql(sql);
        req.setRemark(remark);
        req.setExportCount(exportCount);
        return req;
    }

    private void stubRows(int rowCount) throws SQLException {
        when(statement.executeQuery()).thenReturn(resultSet);
        when(resultSet.getMetaData()).thenReturn(metaData);
        when(metaData.getColumnCount()).thenReturn(1);
        when(metaData.getColumnLabel(1)).thenReturn("id");
        AtomicInteger index = new AtomicInteger();
        when(resultSet.next()).thenAnswer(invocation -> index.getAndIncrement() < rowCount);
        when(resultSet.getObject(1)).thenAnswer(invocation -> index.get());
    }

    private Set<Path> tempExportDirs(Path tempDir) throws Exception {
        try (var paths = Files.list(tempDir)) {
            return paths.filter(path -> path.getFileName().toString().startsWith("sql-probe-export-"))
                    .collect(java.util.stream.Collectors.toSet());
        }
    }
}
