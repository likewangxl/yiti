package com.bank.branch.platform.performance.service.engine;

import com.bank.branch.platform.performance.enums.PerfErrorCode;
import com.bank.branch.platform.performance.exception.PerfException;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * 统计展示主表历史分片路由器的纯单测：日期策略与 SQL 关系表词法边界。
 */
class StatShowSqlRouterTest {

    private static final ZoneId SHANGHAI = ZoneId.of("Asia/Shanghai");
    private static final LocalDate TODAY = LocalDate.of(2026, 8, 31);

    private final StatShowSqlRouter router = new StatShowSqlRouter(
            Clock.fixed(Instant.parse("2026-08-30T16:00:00Z"), SHANGHAI));

    @Test
    void route_usesDataDatePlusOneAsArchiveRunDate() {
        assertThat(router.route(
                "SELECT * FROM XAN_M98_CUST_STAT_SHOW3 c", LocalDate.of(2026, 8, 20)))
                .isEqualTo("SELECT * FROM XAN_M98_CUST_STAT_SHOW3_H3 c");
        assertThat(router.route(
                "SELECT * FROM XAN_M98_EMP_STAT_SHOW3 e", LocalDate.of(2026, 8, 19)))
                .isEqualTo("SELECT * FROM XAN_M98_EMP_STAT_SHOW3_H2 e");
    }

    @Test
    void route_includesExactlyTwentyDaysOld() {
        assertThat(router.route(
                "SELECT * FROM XAN_M98_CUST_STAT_SHOW3", TODAY.minusDays(20)))
                .isEqualTo("SELECT * FROM XAN_M98_CUST_STAT_SHOW3_H2");
    }

    @Test
    void route_rejectsTodayAndFutureWithExactMessage() {
        assertThatThrownBy(() -> router.route(
                "SELECT * FROM XAN_M98_CUST_STAT_SHOW3", TODAY))
                .isInstanceOfSatisfying(PerfException.class, ex -> {
                    assertThat(ex.getErrorCode()).isEqualTo(PerfErrorCode.METRIC_RECALC_DATE_INVALID);
                    assertThat(ex.getMessage()).contains("当天及未来日期不可重算");
                });
        assertThatThrownBy(() -> router.route(
                "SELECT * FROM XAN_M98_CUST_STAT_SHOW3", TODAY.plusDays(1)))
                .isInstanceOfSatisfying(PerfException.class, ex -> {
                    assertThat(ex.getErrorCode()).isEqualTo(PerfErrorCode.METRIC_RECALC_DATE_INVALID);
                    assertThat(ex.getMessage()).contains("当天及未来日期不可重算");
                });
    }

    @Test
    void route_leavesNonTargetSqlWithMissingDateUnchanged() {
        assertThat(router.route("SELECT 1", null)).isEqualTo("SELECT 1");
        assertThatThrownBy(() -> router.validateRecalcDate(null))
                .isInstanceOfSatisfying(PerfException.class, ex -> {
                    assertThat(ex.getErrorCode()).isEqualTo(PerfErrorCode.METRIC_RECALC_DATE_INVALID);
                    assertThat(ex.getMessage()).contains("当天及未来日期不可重算");
                });
    }

    @Test
    void route_supportsCteColumnDefinitionsAndStraightJoin() {
        String sql = "WITH q(id) AS (SELECT id FROM XAN_M98_CUST_STAT_SHOW3) "
                + "SELECT * FROM q STRAIGHT_JOIN XAN_M98_EMP_STAT_SHOW3 e ON q.id=e.id";

        assertThat(router.route(sql, TODAY.minusDays(1)))
                .isEqualTo("WITH q(id) AS (SELECT id FROM XAN_M98_CUST_STAT_SHOW3_H3) "
                        + "SELECT * FROM q STRAIGHT_JOIN XAN_M98_EMP_STAT_SHOW3_H3 e ON q.id=e.id");
    }

    @Test
    void route_rejectsOlderNonMonthEndWithExactMessage() {
        LocalDate dataDate = TODAY.minusDays(21);
        assertThat(dataDate).isEqualTo(LocalDate.of(2026, 8, 10));
        assertThatThrownBy(() -> router.route(
                "SELECT * FROM XAN_M98_EMP_STAT_SHOW3", dataDate))
                .isInstanceOfSatisfying(PerfException.class, ex -> {
                    assertThat(ex.getErrorCode()).isEqualTo(PerfErrorCode.METRIC_RECALC_DATE_TOO_OLD);
                    assertThat(ex.getMessage()).contains("超过20天只能选择月末");
                });
    }

    @Test
    void route_keepsMainTableWhenOlderDateIsNaturalMonthEnd() {
        String sql = "SELECT * FROM XAN_M98_CUST_STAT_SHOW3 c";
        assertThat(router.route(sql, LocalDate.of(2026, 7, 31))).isSameAs(sql);
    }

    @Test
    void route_rewritesBothTablesAndNestedRelations() {
        String sql = "WITH q AS (SELECT * FROM XAN_M98_CUST_STAT_SHOW3) "
                + "SELECT * FROM q JOIN XAN_M98_EMP_STAT_SHOW3 e ON q.id=e.id "
                + "JOIN (SELECT * FROM XAN_M98_CUST_STAT_SHOW3) x ON x.id=e.id";

        assertThat(router.route(sql, TODAY.minusDays(1)))
                .isEqualTo("WITH q AS (SELECT * FROM XAN_M98_CUST_STAT_SHOW3_H3) "
                        + "SELECT * FROM q JOIN XAN_M98_EMP_STAT_SHOW3_H3 e ON q.id=e.id "
                        + "JOIN (SELECT * FROM XAN_M98_CUST_STAT_SHOW3_H3) x ON x.id=e.id");
    }

    @Test
    void route_supportsSchemaQuotedNamesAndAliases() {
        String sql = "SELECT * FROM bank.XAN_M98_CUST_STAT_SHOW3 AS c "
                + "JOIN `bank`.`XAN_M98_EMP_STAT_SHOW3` e ON c.id=e.id";

        assertThat(router.route(sql, TODAY.minusDays(1)))
                .isEqualTo("SELECT * FROM bank.XAN_M98_CUST_STAT_SHOW3_H3 AS c "
                        + "JOIN `bank`.`XAN_M98_EMP_STAT_SHOW3_H3` e ON c.id=e.id");
    }

    @Test
    void route_matchesTableNamesCaseInsensitively() {
        assertThat(router.route(
                "select * from xan_m98_cust_stat_show3 c join Xan_M98_Emp_Stat_Show3 e on 1=1",
                TODAY.minusDays(1)))
                .isEqualTo("select * from xan_m98_cust_stat_show3_H3 c "
                        + "join Xan_M98_Emp_Stat_Show3_H3 e on 1=1");
    }

    @Test
    void route_rewritesCommaSeparatedRelationsButNotSelectListText() {
        String sql = "SELECT XAN_M98_CUST_STAT_SHOW3, 1 FROM bank.XAN_M98_CUST_STAT_SHOW3 c, "
                + "XAN_M98_EMP_STAT_SHOW3 e WHERE c.id=e.id";

        assertThat(router.route(sql, TODAY.minusDays(1)))
                .isEqualTo("SELECT XAN_M98_CUST_STAT_SHOW3, 1 FROM bank.XAN_M98_CUST_STAT_SHOW3_H3 c, "
                        + "XAN_M98_EMP_STAT_SHOW3_H3 e WHERE c.id=e.id");
    }

    @Test
    void route_doesNotRewriteCommentsStringsSimilarNamesOrHistoryTables() {
        String sql = "SELECT 'XAN_M98_CUST_STAT_SHOW3' AS txt, "
                + "'XAN_M98_EMP_STAT_SHOW3' AS txt2 FROM XAN_M98_CUST_STAT_SHOW3 c "
                + "-- XAN_M98_EMP_STAT_SHOW3\n"
                + "# XAN_M98_CUST_STAT_SHOW3\n"
                + "/* XAN_M98_EMP_STAT_SHOW3 */ WHERE c.name='XAN_M98_CUST_STAT_SHOW3' "
                + "AND c.other_table='XAN_M98_CUST_STAT_SHOW30'";

        assertThat(router.route(sql, TODAY.minusDays(1)))
                .isEqualTo("SELECT 'XAN_M98_CUST_STAT_SHOW3' AS txt, "
                        + "'XAN_M98_EMP_STAT_SHOW3' AS txt2 FROM XAN_M98_CUST_STAT_SHOW3_H3 c "
                        + "-- XAN_M98_EMP_STAT_SHOW3\n"
                        + "# XAN_M98_CUST_STAT_SHOW3\n"
                        + "/* XAN_M98_EMP_STAT_SHOW3 */ WHERE c.name='XAN_M98_CUST_STAT_SHOW3' "
                        + "AND c.other_table='XAN_M98_CUST_STAT_SHOW30'");

        assertThat(router.route(
                "SELECT * FROM XAN_M98_CUST_STAT_SHOW3_H1", TODAY.minusDays(1)))
                .isEqualTo("SELECT * FROM XAN_M98_CUST_STAT_SHOW3_H1");
    }

    @Test
    void route_doesNotRewriteCteAliasNamedLikePhysicalTable() {
        String sql = "WITH XAN_M98_CUST_STAT_SHOW3 AS (SELECT 1 AS id) "
                + "SELECT * FROM XAN_M98_CUST_STAT_SHOW3";

        assertThat(router.route(sql, TODAY.minusDays(1))).isEqualTo(sql);
    }

    @Test
    void route_leavesNonTargetSqlUnchanged() {
        String sql = "SELECT * FROM ordinary_table WHERE data_date=:dataDate";
        assertThat(router.route(sql, TODAY.minusDays(21))).isSameAs(sql);
    }
}
