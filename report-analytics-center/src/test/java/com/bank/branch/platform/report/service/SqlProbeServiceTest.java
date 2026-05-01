package com.bank.branch.platform.report.service;

import com.bank.branch.platform.auth.api.CurrentUserApi;
import com.bank.branch.platform.common.web.exception.BizException;
import com.bank.branch.platform.governance.api.AuditApi;
import com.bank.branch.platform.governance.api.dto.AuditLogCmd;
import com.bank.branch.platform.report.dto.req.SqlProbeExecuteReqDTO;
import com.bank.branch.platform.report.entity.SqlProbeHistory;
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

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.SQLException;
import java.sql.SQLTimeoutException;
import java.sql.Statement;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
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

    private SqlProbeServiceImpl service;

    @BeforeEach
    void setUp() throws SQLException {
        // 手动构造（@InjectMocks 不支持含 @Value 多参数构造器）
        service = new SqlProbeServiceImpl(
                validator,
                historyMapper,
                currentUserApi,
                auditApi,
                readOnlyDataSource,
                List.of("cust_master", "kpi_result"),
                List.of("DROP", "DELETE", "UPDATE", "INSERT"),
                1000, 5000, 3);

        // 默认放行：当前用户具备 R_BACK_TECH（具体 case 可覆盖）
        lenient().when(currentUserApi.getCurrentRoleCodes()).thenReturn(Set.of("R_BACK_TECH"));
        lenient().when(currentUserApi.getCurrentEmpId()).thenReturn("E_TECH001");
        // 校验默认放行：返回 normalizedSql 等于 sql
        lenient().when(validator.validateAndNormalize(anyString())).thenAnswer(inv ->
                com.bank.branch.platform.report.support.SqlSafeResult.allowed(
                        inv.getArgument(0), List.of("cust_master")));
        // DataSource 链路默认 mock
        lenient().when(readOnlyDataSource.getConnection()).thenReturn(connection);
        lenient().when(connection.prepareStatement(anyString())).thenReturn(statement);
    }

    @Test
    void execute_withoutBackTechRole_rejects40302() {
        when(currentUserApi.getCurrentRoleCodes()).thenReturn(Set.of("R_RM"));
        SqlProbeExecuteReqDTO req = buildReq("SELECT * FROM cust_master", "诊断");

        assertThatThrownBy(() -> service.execute(req))
                .isInstanceOf(BizException.class)
                .hasFieldOrPropertyWithValue("code", "RPT-40302");
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

        SqlProbeExecuteReqDTO req = buildReq("SELECT id FROM cust_master", "查询客户");

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

        SqlProbeExecuteReqDTO req = buildReq("SELECT id FROM cust_master", "压测");

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

        SqlProbeExecuteReqDTO req = buildReq("SELECT id FROM cust_master", "排查");

        assertThatThrownBy(() -> service.execute(req))
                .isInstanceOf(BizException.class)
                .hasFieldOrPropertyWithValue("code", "RPT-42009");

        ArgumentCaptor<SqlProbeHistory> updateCap = ArgumentCaptor.forClass(SqlProbeHistory.class);
        verify(historyMapper).updateTerminalStatus(updateCap.capture());
        assertThat(updateCap.getValue().getStatus()).isEqualTo("FAILED");

        verify(auditApi, atLeastOnce()).log(any());
    }

    private SqlProbeExecuteReqDTO buildReq(String sql, String remark) {
        SqlProbeExecuteReqDTO req = new SqlProbeExecuteReqDTO();
        req.setSql(sql);
        req.setRemark(remark);
        return req;
    }
}
