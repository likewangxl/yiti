package com.bank.branch.platform.performance.mapper;

import com.bank.branch.platform.auth.api.RoleApi;
import com.bank.branch.platform.governance.api.AuditApi;
import com.bank.branch.platform.governance.api.DictApi;
import com.bank.branch.platform.governance.api.FileApi;
import com.bank.branch.platform.governance.api.NotifyApi;
import com.bank.branch.platform.performance.support.PerformanceMapperTestBase;
import com.bank.branch.platform.workflow.api.TodoQueryApi;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.jdbc.core.JdbcTemplate;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * StatShowArchiveMapper 集成测试（真库 onepl_test_bootstrap）.
 *
 * <p>用最小同构 fixture 表 {@code ARCH_IT_MAIN} / {@code ARCH_IT_HIST}（@BeforeEach 建、@AfterEach 删），
 * 不依赖真实 XAN_M98_* 宽表结构；仅验证 5 个按日 SQL 与 {@code ${表名}} 拼接。表结构无主键，与真实统计表一致。
 */
class StatShowArchiveMapperIT extends PerformanceMapperTestBase {

    private static final String MAIN = "ARCH_IT_MAIN";
    private static final String HIST = "ARCH_IT_HIST";

    @Autowired
    private StatShowArchiveMapper mapper;
    @Autowired
    private JdbcTemplate jdbc;

    // PerfTestConfig 未提供的跨模块 governance/auth/workflow API，补 mock 使 perf 测试上下文可加载（与本用例逻辑无关）
    @MockBean private NotifyApi notifyApi;
    @MockBean private FileApi fileApi;
    @MockBean private DictApi dictApi;
    @MockBean private AuditApi auditApi;
    @MockBean private RoleApi roleApi;
    @MockBean private TodoQueryApi todoQueryApi;

    @BeforeEach
    void setUp() {
        for (String t : new String[]{MAIN, HIST}) {
            jdbc.execute("DROP TABLE IF EXISTS " + t);
            jdbc.execute("CREATE TABLE " + t + " (STATIS_DT VARCHAR(10), CUST_ID VARCHAR(32), VAL VARCHAR(32))");
        }
    }

    @AfterEach
    void tearDown() {
        jdbc.execute("DROP TABLE IF EXISTS " + MAIN);
        jdbc.execute("DROP TABLE IF EXISTS " + HIST);
    }

    private void ins(String t, String dt, String id, String val) {
        jdbc.update("INSERT INTO " + t + "(STATIS_DT,CUST_ID,VAL) VALUES (?,?,?)", dt, id, val);
    }

    private long cnt(String t) {
        return jdbc.queryForObject("SELECT COUNT(*) FROM " + t, Long.class);
    }

    @Test
    void countByTableDate_countsOnlyThatDate() {
        ins(MAIN, "2026-07-05", "A", "1");
        ins(MAIN, "2026-07-05", "B", "1");
        ins(MAIN, "2026-07-06", "C", "1");
        assertThat(mapper.countByTableDate(MAIN, "2026-07-05")).isEqualTo(2);
        assertThat(mapper.countByTableDate(MAIN, "2026-07-09")).isZero();
    }

    @Test
    void insertHistByDate_copiesOnlyThatDate_allColumns() {
        ins(MAIN, "2026-07-05", "Y", "v-y");
        ins(MAIN, "2026-07-06", "Z", "v-z");
        int n = mapper.insertHistByDate(HIST, MAIN, "2026-07-05");
        assertThat(n).isEqualTo(1);
        assertThat(cnt(HIST)).isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT VAL FROM " + HIST + " WHERE CUST_ID='Y'", String.class)).isEqualTo("v-y");
    }

    @Test
    void deleteHistByDate_deletesOnlyThatDate() {
        ins(HIST, "2026-07-05", "A", "1");
        ins(HIST, "2026-07-05", "B", "1");
        ins(HIST, "2026-07-06", "C", "1");
        int n = mapper.deleteHistByDate(HIST, "2026-07-05");
        assertThat(n).isEqualTo(2);
        assertThat(cnt(HIST)).isEqualTo(1);
    }

    @Test
    void selectMaxStatisDt_returnsMaxInRange_orNull() {
        ins(MAIN, "2026-07-01", "A", "1");
        ins(MAIN, "2026-07-28", "B", "1");
        ins(MAIN, "2026-08-05", "C", "1");
        assertThat(mapper.selectMaxStatisDt(MAIN, "2026-07-01", "2026-07-31")).isEqualTo("2026-07-28");
        assertThat(mapper.selectMaxStatisDt(MAIN, "2026-09-01", "2026-09-30")).isNull();
    }

    @Test
    void deleteMainByDate_deletesOnlyThatDate() {
        ins(MAIN, "2026-07-15", "A", "1");
        ins(MAIN, "2026-07-16", "B", "1");
        int n = mapper.deleteMainByDate(MAIN, "2026-07-15");
        assertThat(n).isEqualTo(1);
        assertThat(cnt(MAIN)).isEqualTo(1);
    }
}
