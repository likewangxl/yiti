package com.bank.branch.platform.performance.mapper;

import org.apache.ibatis.builder.xml.XMLMapperBuilder;
import org.apache.ibatis.mapping.BoundSql;
import org.apache.ibatis.mapping.MappedStatement;
import org.apache.ibatis.session.Configuration;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.CALLS_REAL_METHODS;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * 个人经营快照 Mapper 的无数据库验证。
 *
 * <p>使用 MyBatis 真正解析 XML 并生成 BoundSql，验证本人、日期、rowId、slot 和排序绑定；
 * 默认聚合另验证 0 保留、NULL 丢弃。</p>
 */
class EmpIndexResultMapperSqlTest {

    @Test
    void latestRowStatementBindsEmployeeAndTodayWithStableOrder() {
        MappedStatement statement = mappedStatement("selectLatestRowForEmployee");
        Map<String, Object> params = Map.of(
                "empId", "EMP_REAL",
                "today", LocalDate.of(2026, 9, 20));

        BoundSql boundSql = statement.getBoundSql(params);
        String sql = boundSql.getSql().replaceAll("\\s+", " ").trim();

        assertThat(sql).contains("FROM EMP_INDEX_RESULT")
                .contains("emp_id = ?")
                .contains("data_date <= ?")
                .contains("ORDER BY data_date DESC, updated_time DESC, version DESC, id DESC")
                .contains("LIMIT 1");
        assertThat(boundSql.getParameterMappings()).hasSize(2);
        assertThat(boundSql.getParameterObject()).isSameAs(params);
    }

    @Test
    void rowValueStatementBindsEmployeeRowIdsDatesAndMultipleSlots() {
        MappedStatement statement = mappedStatement("selectSlotValuesByRowIdsAndSlotsRaw");
        Map<String, Object> params = new HashMap<>();
        params.put("empId", "EMP_REAL");
        params.put("today", LocalDate.of(2026, 9, 20));
        params.put("rowIds", List.of(100L, 101L, 102L));
        params.put("slots", List.of(5, 11));

        BoundSql boundSql = statement.getBoundSql(params);
        String sql = boundSql.getSql().replaceAll("\\s+", " ").trim();

        assertThat(sql).contains("EMP_INDEX_RESULT r")
                .contains("r.emp_id = ?")
                .contains("r.data_date <= ?")
                .contains("r.id IN")
                .contains("val_5 AS metricValue")
                .contains("val_11 AS metricValue")
                .contains("UNION ALL");
        assertThat(boundSql.getParameterMappings()).hasSize(12);
        assertThat(boundSql.getParameterObject()).isSameAs(params);
    }

    @Test
    void historyRowsStatementPartitionsByDateAndKeepsEmployeeConstraint() {
        MappedStatement statement = mappedStatement("selectLatestRowsForEmployeeDates");
        Map<String, Object> params = Map.of(
                "empId", "EMP_REAL",
                "today", LocalDate.of(2026, 9, 20),
                "dates", List.of(LocalDate.of(2026, 8, 31), LocalDate.of(2025, 8, 13)));

        BoundSql boundSql = statement.getBoundSql(params);
        String sql = boundSql.getSql().replaceAll("\\s+", " ").trim();

        assertThat(sql).contains("PARTITION BY data_date")
                .contains("emp_id = ?")
                .contains("data_date <= ?")
                .contains("data_date IN")
                .contains("ORDER BY updated_time DESC, version DESC, id DESC")
                .contains("WHERE rowNo = 1");
        assertThat(boundSql.getParameterObject()).isSameAs(params);
    }

    @Test
    void defaultAggregationKeepsZeroAndDropsNullByRowIdAndSlot() {
        EmpIndexResultMapper mapper = mock(EmpIndexResultMapper.class, CALLS_REAL_METHODS);
        when(mapper.selectSlotValuesByRowIdsAndSlotsRaw(
                anyString(), anyList(), any(), anyList()))
                .thenReturn(List.of(
                        row(100L, LocalDate.of(2026, 8, 13), 5, BigDecimal.ZERO),
                        row(100L, LocalDate.of(2026, 8, 13), 11, new BigDecimal("2.5")),
                        row(101L, LocalDate.of(2026, 7, 31), 5, null)));

        Map<Long, Map<Integer, BigDecimal>> result = mapper.selectSlotValuesByRowIdsAndSlots(
                "EMP_REAL", List.of(100L, 101L), LocalDate.of(2026, 9, 20), List.of(5, 11));

        assertThat(result.get(100L)).containsEntry(5, BigDecimal.ZERO)
                .containsEntry(11, new BigDecimal("2.5"));
        assertThat(result).doesNotContainKey(101L);
    }

    private MappedStatement mappedStatement(String id) {
        Configuration configuration = new Configuration();
        configuration.setMapUnderscoreToCamelCase(true);
        try (var input = getClass().getResourceAsStream(
                "/mapper/performance/EmpIndexResultMapper.xml")) {
            assertThat(input).isNotNull();
            new XMLMapperBuilder(
                    input,
                    configuration,
                    "mapper/performance/EmpIndexResultMapper.xml",
                    configuration.getSqlFragments()).parse();
        } catch (Exception ex) {
            throw new AssertionError("Mapper XML should parse", ex);
        }
        return configuration.getMappedStatement(
                "com.bank.branch.platform.performance.mapper.EmpIndexResultMapper." + id);
    }

    private static EmpRowSlotValueRow row(Long rowId, LocalDate date, int slot, BigDecimal value) {
        EmpRowSlotValueRow row = new EmpRowSlotValueRow();
        row.setRowId(rowId);
        row.setDataDate(date);
        row.setSlot(slot);
        row.setMetricValue(value);
        return row;
    }
}
