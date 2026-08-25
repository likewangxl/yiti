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

/** StatShowArchiveMapper 集成测试：单日 delete/insert 和边界 truncate。 */
class StatShowArchiveMapperIT extends PerformanceMapperTestBase {

    private static final String MAIN = "ARCH_IT_MAIN";
    private static final String TMP = "ARCH_IT_TMP";
    private static final String HIST = "ARCH_IT_HIST";

    @Autowired
    private StatShowArchiveMapper mapper;
    @Autowired
    private JdbcTemplate jdbc;

    // PerfTestConfig 未提供的跨模块 API，补 mock 使 perf 测试上下文可加载
    @MockBean private NotifyApi notifyApi;
    @MockBean private FileApi fileApi;
    @MockBean private DictApi dictApi;
    @MockBean private AuditApi auditApi;
    @MockBean private RoleApi roleApi;
    @MockBean private TodoQueryApi todoQueryApi;

    @BeforeEach
    void setUp() {
        for (String table : new String[]{MAIN, TMP, HIST}) {
            jdbc.execute("DROP TABLE IF EXISTS " + table);
            jdbc.execute("CREATE TABLE " + table
                    + " (STATIS_DT VARCHAR(10), CUST_ID VARCHAR(32), VAL VARCHAR(32))");
        }
    }

    @AfterEach
    void tearDown() {
        for (String table : new String[]{MAIN, TMP, HIST}) {
            jdbc.execute("DROP TABLE IF EXISTS " + table);
        }
    }

    private void ins(String table, String dt, String id, String val) {
        jdbc.update("INSERT INTO " + table + "(STATIS_DT,CUST_ID,VAL) VALUES (?,?,?)", dt, id, val);
    }

    private long cnt(String table) {
        return jdbc.queryForObject("SELECT COUNT(*) FROM " + table, Long.class);
    }

    @Test
    void deleteByTableDate_deletesOnlySelectedDate() {
        ins(HIST, "2026-07-05", "A", "1");
        ins(HIST, "2026-07-05", "B", "1");
        ins(HIST, "2026-07-06", "C", "1");

        int deleted = mapper.deleteByTableDate(HIST, "2026-07-05");

        assertThat(deleted).isEqualTo(2);
        assertThat(cnt(HIST)).isEqualTo(1);
    }

    @Test
    void insertFromTmpByDate_copiesOnlyTMinusOne_allColumns() {
        ins(TMP, "2026-07-05", "Y", "v-y");
        ins(TMP, "2026-07-06", "Z", "v-z");

        int inserted = mapper.insertFromTmpByDate(HIST, TMP, "2026-07-05");

        assertThat(inserted).isEqualTo(1);
        assertThat(cnt(HIST)).isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT VAL FROM " + HIST + " WHERE CUST_ID='Y'", String.class))
                .isEqualTo("v-y");
    }

    @Test
    void truncateTable_clearsAllRows() {
        ins(HIST, "2026-07-05", "A", "1");
        ins(HIST, "2026-07-06", "B", "1");

        mapper.truncateTable(HIST);

        assertThat(cnt(HIST)).isZero();
    }

    @Test
    void countByTableDate_countsOnlySelectedDate() {
        ins(TMP, "2026-07-05", "A", "1");
        ins(TMP, "2026-07-05", "B", "1");
        ins(TMP, "2026-07-06", "C", "1");

        assertThat(mapper.countByTableDate(TMP, "2026-07-05")).isEqualTo(2);
    }
}
