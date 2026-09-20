package com.bank.branch.platform.report.service.screen;

import com.bank.branch.platform.auth.api.OrgApi;
import com.bank.branch.platform.auth.api.dto.UniqueUserOrgDTO;
import com.bank.branch.platform.report.dto.resp.ScreenDataRespDTO;
import com.bank.branch.platform.report.exception.RptException;
import org.junit.jupiter.api.Test;

import javax.sql.DataSource;
import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.Date;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class M98StatQueryTest {

    @Test
    void summaryUsesUniqueMappedEmployeesCurrency01AndOneCommonLatestDate() throws Exception {
        OrgApi orgApi = mock(OrgApi.class);
        when(orgApi.listUniqueUserOrgs(List.of("330", "90"))).thenReturn(List.of(
                mapping("U330", "330"), mapping("U90", "90")));
        JdbcFixture jdbc = jdbc(Date.valueOf("2026-07-24"), List.of(
                row("U330", "2026-07-24", "盈利指标", "税前利润", "营业收入", "板块分类", "1", "公司", "100.5", null),
                row("U330", "2026-07-24", "盈利指标", "税前利润", "营业收入", "板块分类", "1", "公司", "200.5", null),
                row("U90", "2026-07-24", "盈利指标", "税前利润", "营业收入", "板块分类", "1", "公司", "300", null)));

        ScreenDataRespDTO response = new M98StatQuery(jdbc.dataSource(), orgApi)
                .query("CORP_REVENUE", List.of("330", "90"), List.of("330", "90"),
                        LocalDate.of(2026, 7, 25), 1000);

        assertThat(response.getColumns()).containsExactly("data_date", "amount");
        assertThat(response.getRows()).containsExactly(
                List.of("2026-07-24", new BigDecimal("601.0")));
        assertThat(response.getQuality()).satisfies(q -> {
            assertThat(q.getDataDate()).isEqualTo("2026-07-24");
            assertThat(q.getExpectedSubjects()).isEqualTo(2);
            assertThat(q.getReceivedSubjects()).isEqualTo(2);
            assertThat(q.getStatus()).isEqualTo("COMPLETE");
        });
        assertThat(jdbc.dateSql()).contains("MAX(STR_TO_DATE(STATIS_DT").contains("CURRENCY_TYPE = '199'")
                .doesNotContain("CURRENCY_TYPE = '01'");
        assertThat(jdbc.rowsSql()).contains("CURRENCY_TYPE = '199'").doesNotContain("CURRENCY_TYPE = '01'");
    }

    @Test
    void nplUnknownClassificationAndInvalidAmountAreNullAndPartialWithoutZeroFill() throws Exception {
        OrgApi orgApi = mock(OrgApi.class);
        when(orgApi.listUniqueUserOrgs(List.of("330", "90"))).thenReturn(List.of(
                mapping("U330", "330"), mapping("U90", "90")));
        JdbcFixture jdbc = jdbc(Date.valueOf("2026-07-24"), List.of(
                nplRow("U330", "正常", "100"), nplRow("U330", "关注", "50"),
                nplRow("U330", "次级", "10"), nplRow("U330", "可疑", "10"), nplRow("U330", "损失", "10"),
                nplRow("U90", "正常", "bad"), nplRow("U90", "未知", "10")));

        ScreenDataRespDTO response = new M98StatQuery(jdbc.dataSource(), orgApi)
                .query("CORP_NPL_RATE", List.of("330", "90"), List.of("330", "90"),
                        LocalDate.of(2026, 7, 25), 1000);

        assertThat(response.getColumns()).containsExactly("data_date", "ratio");
        List<Object> invalidRow = new ArrayList<>(List.of("2026-07-24"));
        invalidRow.add(null);
        assertThat(response.getRows()).containsExactly(
                invalidRow);
        assertThat(response.getQuality().getStatus()).isEqualTo("PARTIAL");
        assertThat(response.getQuality().getReceivedSubjects()).isEqualTo(2);
    }

    @Test
    void invalidSelectedDateMakesAmountNullAndPartial() throws Exception {
        OrgApi orgApi = mock(OrgApi.class);
        when(orgApi.listUniqueUserOrgs(List.of("330"))).thenReturn(List.of(mapping("U330", "330")));
        JdbcFixture jdbc = jdbc(Date.valueOf("2026-07-24"), List.<String[]>of(
                row("U330", "not-a-date", "盈利指标", "税前利润", "营业收入", "板块分类", "1", "公司", "100", null)));

        ScreenDataRespDTO response = new M98StatQuery(jdbc.dataSource(), orgApi)
                .query("CORP_REVENUE", List.of("330"), List.of("330"),
                        LocalDate.of(2026, 7, 25), 1000);

        List<Object> result = response.getRows().get(0);
        assertThat(result.get(1)).isNull();
        assertThat(response.getQuality().getStatus()).isEqualTo("PARTIAL");
    }

    @Test
    void nplSummaryUsesWeightedTotalsInsteadOfAveragingInstitutionRates() throws Exception {
        OrgApi orgApi = mock(OrgApi.class);
        when(orgApi.listUniqueUserOrgs(List.of("330", "90"))).thenReturn(List.of(
                mapping("U330", "330"), mapping("U90", "90")));
        JdbcFixture jdbc = jdbc(Date.valueOf("2026-07-24"), List.of(
                nplRow("U330", "正常", "9"), nplRow("U330", "次级", "1"),
                nplRow("U90", "正常", "81"), nplRow("U90", "次级", "9")));

        ScreenDataRespDTO response = new M98StatQuery(jdbc.dataSource(), orgApi)
                .query("CORP_NPL_RATE", List.of("330", "90"), List.of("330", "90"),
                        LocalDate.of(2026, 7, 25), 1000);

        assertThat(response.getRows()).containsExactly(List.of("2026-07-24", new BigDecimal("10.00")));
        assertThat(response.getQuality().getStatus()).isEqualTo("COMPLETE");
        assertThat(jdbc.dateSql()).contains("CURRENCY_TYPE = '01'").doesNotContain("CURRENCY_TYPE = '199'");
        assertThat(jdbc.rowsSql()).contains("CURRENCY_TYPE = '01'").doesNotContain("CURRENCY_TYPE = '199'");
    }

    @Test
    void emptyMappingReturnsNoSourceWithoutOpeningReadonlyDatabase() throws Exception {
        OrgApi orgApi = mock(OrgApi.class);
        when(orgApi.listUniqueUserOrgs(List.of("330"))).thenReturn(List.of());
        DataSource dataSource = mock(DataSource.class);

        ScreenDataRespDTO response = new M98StatQuery(dataSource, orgApi)
                .query("RETAIL_LOAN", List.of("330"), List.of("330"),
                        LocalDate.of(2026, 7, 25), 1000);

        assertThat(response.getRows()).isEmpty();
        assertThat(response.getQuality().getStatus()).isEqualTo("NO_SOURCE");
        verify(dataSource, never()).getConnection();
    }

    @Test
    void employeeMappingLimitFailsClosedInsteadOfTruncatingAggregation() {
        OrgApi orgApi = mock(OrgApi.class);
        List<UniqueUserOrgDTO> mappings = new ArrayList<>();
        for (int i = 0; i < M98StatQuery.MAX_EMPLOYEE_IDS + 1; i++) {
            mappings.add(mapping("U" + i, "O" + i));
        }
        when(orgApi.listUniqueUserOrgs(org.mockito.ArgumentMatchers.anyCollection())).thenReturn(mappings);

        assertThatThrownBy(() -> new M98StatQuery(mock(DataSource.class), orgApi)
                .query("RETAIL_LOAN", mappings.stream().map(UniqueUserOrgDTO::getOrgCode).toList(),
                        mappings.stream().map(UniqueUserOrgDTO::getOrgCode).toList(),
                        LocalDate.of(2026, 7, 25), 1000))
                .isInstanceOf(RuntimeException.class);
    }

    @Test
    void detailRowLimitFailsClosedInsteadOfTruncatingSummary() throws Exception {
        OrgApi orgApi = mock(OrgApi.class);
        when(orgApi.listUniqueUserOrgs(List.of("330"))).thenReturn(List.of(mapping("U330", "330")));
        List<String[]> rows = new ArrayList<>();
        for (int i = 0; i < M98StatQuery.MAX_DETAIL_ROWS + 1; i++) {
            rows.add(row("U330", "2026-07-24", "盈利指标", "税前利润", "营业收入",
                    "板块分类", "1", "公司", "1", null));
        }
        JdbcFixture jdbc = jdbc(Date.valueOf("2026-07-24"), rows);

        assertThatThrownBy(() -> new M98StatQuery(jdbc.dataSource(), orgApi)
                .query("CORP_REVENUE", List.of("330"), List.of("330"),
                        LocalDate.of(2026, 7, 25), 1000))
                .isInstanceOf(RptException.class);
    }

    private static UniqueUserOrgDTO mapping(String empId, String orgCode) {
        UniqueUserOrgDTO dto = new UniqueUserOrgDTO();
        dto.setEmpId(empId);
        dto.setOrgCode(orgCode);
        return dto;
    }

    private static String[] row(String empId, String date, String type, String level1, String level2,
                                String subType, String subCode, String subName, String ftpRevenue, String currBal) {
        return new String[]{empId, date, type, level1, level2, null, subType, subCode, subName,
                currBal, ftpRevenue};
    }

    private static String[] nplRow(String empId, String category, String amount) {
        return new String[]{empId, "2026-07-24", "报表指标", "对公业务（不含汇兑，含逾期）",
                "对公资产业务", "对公板块贷款", "五级分类", "", category, amount, null};
    }

    private static JdbcFixture jdbc(Date commonDate, List<String[]> rows) throws Exception {
        DataSource dataSource = mock(DataSource.class);
        Connection connection = mock(Connection.class);
        PreparedStatement dateStatement = mock(PreparedStatement.class);
        PreparedStatement rowsStatement = mock(PreparedStatement.class);
        ResultSet dateResult = mock(ResultSet.class);
        ResultSet rowsResult = mock(ResultSet.class);
        ResultSetMetaData metadata = mock(ResultSetMetaData.class);
        List<String> sqls = new ArrayList<>();
        when(dataSource.getConnection()).thenReturn(connection);
        when(connection.prepareStatement(anyString())).thenAnswer(invocation -> {
            sqls.add(invocation.getArgument(0, String.class));
            return sqls.size() == 1 ? dateStatement : rowsStatement;
        });
        when(dateStatement.executeQuery()).thenReturn(dateResult);
        when(rowsStatement.executeQuery()).thenReturn(rowsResult);
        when(dateResult.next()).thenReturn(true, false);
        when(dateResult.getObject(1)).thenReturn(commonDate);
        when(rowsResult.getMetaData()).thenReturn(metadata);
        when(metadata.getColumnCount()).thenReturn(11);
        Iterator<String[]> iterator = rows.iterator();
        String[][] current = new String[1][];
        when(rowsResult.next()).thenAnswer(invocation -> {
            if (!iterator.hasNext()) {
                return false;
            }
            current[0] = iterator.next();
            return true;
        });
        when(rowsResult.getString(anyInt())).thenAnswer(invocation ->
                current[0][invocation.getArgument(0, Integer.class) - 1]);
        return new JdbcFixture(dataSource, sqls);
    }

    private record JdbcFixture(DataSource dataSource, List<String> sqls) {
        String dateSql() {
            return sqls.isEmpty() ? "" : sqls.get(0);
        }

        String rowsSql() {
            return sqls.size() < 2 ? "" : sqls.get(1);
        }
    }
}
