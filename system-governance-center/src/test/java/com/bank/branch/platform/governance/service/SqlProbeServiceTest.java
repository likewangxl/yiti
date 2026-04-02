package com.bank.branch.platform.governance.service;

import com.bank.branch.platform.common.web.exception.BizException;
import com.bank.branch.platform.governance.enums.GovErrorCode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.Statement;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Semaphore;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * SQL探针服务单元测试
 * TDD RED 阶段：先编写测试用例，确保编译失败后再实现生产代码
 */
@ExtendWith(MockitoExtension.class)
class SqlProbeServiceTest {

    @Mock
    DataSource dataSource;

    @Mock
    Connection connection;

    @Mock
    Statement statement;

    @Mock
    ResultSet resultSet;

    @Mock
    ResultSetMetaData resultSetMetaData;

    @Mock
    AuditLogService auditLogService;

    @InjectMocks
    SqlProbeService sqlProbeService;

    /**
     * 设置 DataSource mock 链
     */
    @BeforeEach
    void setUp() throws Exception {
        lenient().when(dataSource.getConnection()).thenReturn(connection);
        lenient().when(connection.createStatement()).thenReturn(statement);
        lenient().when(statement.executeQuery(anyString())).thenReturn(resultSet);
        lenient().when(resultSet.next()).thenReturn(false);
        lenient().when(resultSet.getMetaData()).thenReturn(resultSetMetaData);
        lenient().when(resultSetMetaData.getColumnCount()).thenReturn(0);
    }

    /**
     * 测试：非SELECT语句（DELETE）应抛出 GOV-42201 异常
     */
    @Test
    void executeSql_nonSelect_throwsGov42201() {
        BizException ex = catchThrowableOfType(
                () -> sqlProbeService.executeSql("DELETE FROM users", "E001", "测试"),
                BizException.class
        );

        assertThat(ex).isNotNull();
        assertThat(ex.getCode()).isEqualTo(GovErrorCode.NOT_SELECT_SQL.getCode());
    }

    /**
     * 测试：非SELECT语句（INSERT）应抛出 GOV-42201 异常
     */
    @Test
    void executeSql_insertStatement_throwsGov42201() {
        BizException ex = catchThrowableOfType(
                () -> sqlProbeService.executeSql("INSERT INTO users(name) VALUES('test')", "E001", "测试"),
                BizException.class
        );

        assertThat(ex).isNotNull();
        assertThat(ex.getCode()).isEqualTo(GovErrorCode.NOT_SELECT_SQL.getCode());
    }

    /**
     * 测试：不带LIMIT的SELECT语句应自动追加 LIMIT 1000
     */
    @Test
    void executeSql_selectWithoutLimit_appendsLimit() throws Exception {
        String sql = "SELECT * FROM sys_dict";

        sqlProbeService.executeSql(sql, "E001", "测试");

        ArgumentCaptor<String> sqlCaptor = ArgumentCaptor.forClass(String.class);
        verify(statement).executeQuery(sqlCaptor.capture());

        assertThat(sqlCaptor.getValue()).isEqualTo("SELECT * FROM sys_dict LIMIT 1000");
    }

    /**
     * 测试：已有LIMIT的SELECT语句不应修改
     */
    @Test
    void executeSql_selectWithExistingLimit_doesNotModify() throws Exception {
        String sql = "SELECT * FROM sys_dict LIMIT 10";

        sqlProbeService.executeSql(sql, "E001", "测试");

        ArgumentCaptor<String> sqlCaptor = ArgumentCaptor.forClass(String.class);
        verify(statement).executeQuery(sqlCaptor.capture());

        assertThat(sqlCaptor.getValue()).isEqualTo("SELECT * FROM sys_dict LIMIT 10");
    }

    /**
     * 测试：执行成功后应写入审计日志
     */
    @Test
    void executeSql_success_writesAuditLog() throws Exception {
        String sql = "SELECT * FROM sys_dict LIMIT 5";

        sqlProbeService.executeSql(sql, "E001", "查询测试");

        verify(auditLogService).log(argThat(cmd ->
                "EXECUTE_SQL".equals(cmd.getBizAction())
                        && sql.equals(cmd.getRequestParams())
                        && "E001".equals(cmd.getEmpId())
                        && "查询测试".equals(cmd.getReason())
        ));
    }

    /**
     * 测试：并发超限时应抛出 GOV-42202 异常
     */
    @Test
    void executeSql_concurrencyExceeded_throwsGov42202() {
        // 将信号量设置为0个许可，模拟并发已满
        ReflectionTestUtils.setField(sqlProbeService, "semaphore", new Semaphore(0));

        BizException ex = catchThrowableOfType(
                () -> sqlProbeService.executeSql("SELECT 1", "E001", "测试"),
                BizException.class
        );

        assertThat(ex).isNotNull();
        assertThat(ex.getCode()).isEqualTo(GovErrorCode.SQL_CONCURRENCY_EXCEEDED.getCode());
    }

    /**
     * 测试：执行成功应返回结果集数据
     */
    @Test
    void executeSql_success_returnsResultData() throws Exception {
        // 模拟结果集有一行两列
        when(resultSet.next()).thenReturn(true, false);
        when(resultSetMetaData.getColumnCount()).thenReturn(2);
        when(resultSetMetaData.getColumnLabel(1)).thenReturn("id");
        when(resultSetMetaData.getColumnLabel(2)).thenReturn("name");
        when(resultSet.getObject(1)).thenReturn(1L);
        when(resultSet.getObject(2)).thenReturn("测试字典");

        List<Map<String, Object>> result = sqlProbeService.executeSql(
                "SELECT id, name FROM sys_dict LIMIT 1", "E001", "测试");

        assertThat(result).hasSize(1);
        assertThat(result.get(0).get("id")).isEqualTo(1L);
        assertThat(result.get(0).get("name")).isEqualTo("测试字典");
    }

    /**
     * 测试：设置了查询超时时间
     */
    @Test
    void executeSql_setsQueryTimeout() throws Exception {
        sqlProbeService.executeSql("SELECT 1", "E001", "测试");

        verify(statement).setQueryTimeout(30);
    }
}
