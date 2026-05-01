package com.bank.branch.platform.performance.service;

import com.bank.branch.platform.performance.exception.PerfException;
import com.bank.branch.platform.performance.service.engine.SqlValidator;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * SubjectFetcher 单元测试（V1.7 P3）.
 */
class SubjectFetcherTest {

    private NamedParameterJdbcTemplate jdbc;
    private SqlValidator validator;
    private SubjectFetcher fetcher;

    @BeforeEach
    void setup() {
        jdbc = mock(NamedParameterJdbcTemplate.class);
        validator = mock(SqlValidator.class);
        fetcher = new SubjectFetcher(jdbc, validator);
    }

    /** subject_sql 为空或 null 时，抛 PERF-40022 */
    @Test
    void empty_sql_throws_subject_sql_required() {
        assertThatThrownBy(() -> fetcher.fetch("", Map.of()))
                .isInstanceOf(PerfException.class)
                .satisfies(ex -> assertThat(((PerfException) ex).getErrorCode().getCode())
                        .isEqualTo("PERF-40022"));
        assertThatThrownBy(() -> fetcher.fetch(null, Map.of()))
                .isInstanceOf(PerfException.class)
                .satisfies(ex -> assertThat(((PerfException) ex).getErrorCode().getCode())
                        .isEqualTo("PERF-40022"));
    }

    /** SqlValidator 黑名单异常向上透传，不被 SubjectFetcher 吞掉 */
    @Test
    void sql_validator_blacklist_propagates() {
        doThrow(new RuntimeException("DROP keyword forbidden"))
                .when(validator).validate(anyString());
        assertThatThrownBy(() -> fetcher.fetch("DROP TABLE x", Map.of()))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("DROP keyword");
    }

    /** 正常查询返回去重后的 key 列表 */
    @Test
    void normal_query_returns_distinct_keys() {
        when(jdbc.queryForList(eq("SELECT emp_id FROM t"), anyMap(), eq(String.class)))
                .thenReturn(List.of("E001", "E002", "E001"));
        List<String> result = fetcher.fetch("SELECT emp_id FROM t",
                Map.of("dataDate", LocalDate.now()));
        assertThat(result).containsExactlyInAnyOrder("E001", "E002");
    }

    /** SQL 返回空结果集时，fetch 返回空列表 */
    @Test
    void empty_result_returns_empty_list() {
        when(jdbc.queryForList(anyString(), anyMap(), eq(String.class)))
                .thenReturn(List.of());
        assertThat(fetcher.fetch("SELECT 1", Map.of())).isEmpty();
    }

    /** params 为 null 时不抛 NPE，等同于空 Map 传入 */
    @Test
    void null_params_does_not_throw_NPE() {
        when(jdbc.queryForList(anyString(), anyMap(), eq(String.class)))
                .thenReturn(List.of("E001"));
        List<String> result = fetcher.fetch("SELECT 1", null);
        assertThat(result).containsExactly("E001");
    }

    /** DataAccessException 被包装为 PERF-50004 PerfException */
    @Test
    void data_access_exception_wraps_to_perf_exception() {
        when(jdbc.queryForList(anyString(), anyMap(), eq(String.class)))
                .thenThrow(new DataAccessResourceFailureException("conn lost"));
        assertThatThrownBy(() -> fetcher.fetch("SELECT 1", Map.of()))
                .isInstanceOf(PerfException.class)
                .satisfies(ex -> assertThat(((PerfException) ex).getErrorCode().getCode())
                        .isEqualTo("PERF-50004"));
    }
}
