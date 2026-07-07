package com.bank.branch.platform.performance.job;

import com.bank.branch.platform.performance.mapper.StatShowArchiveMapper;
import org.apache.ibatis.builder.xml.XMLMapperBuilder;
import org.apache.ibatis.datasource.unpooled.UnpooledDataSource;
import org.apache.ibatis.io.Resources;
import org.apache.ibatis.mapping.Environment;
import org.apache.ibatis.session.Configuration;
import org.apache.ibatis.session.SqlSession;
import org.apache.ibatis.session.SqlSessionFactory;
import org.apache.ibatis.session.SqlSessionFactoryBuilder;
import org.apache.ibatis.transaction.jdbc.JdbcTransactionFactory;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;

import java.io.InputStream;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.Statement;
import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * StatShowArchiveJob 端到端集成测试（真 MySQL，但用专用库 {@code stat_arch_test}，与真 yiti 库完全隔离）.
 *
 * <p>为什么不用 Spring：手工构建 MyBatis SqlSessionFactory，绕开 perf 测试上下文的环境漂移问题
 * （EVAL_ASSIGN_BATCH.total_rows）。直接调 {@code job.run(LocalDate)} 注入任意运行日，
 * 从而端到端验证 11/21/1 号边界分支与主表瘦身（不受系统真实日期限制）。
 *
 * <p>安全：任务里的表名不带库名（{@code ${table}}），连到哪个库就在哪个库操作；本测试连
 * {@code stat_arch_test}，真 yiti 库一行不动。@BeforeAll 建库建表、@AfterAll 删库。
 *
 * <p>fixture 表用最小同构结构（STATIS_DT+ID+VAL；main 与其 _H1/_H2/_H3 一致）——任务逻辑
 * 只认 STATIS_DT 与整行 SELECT、COUNT，与真实宽表 85 列无关，故最小结构即可完整验证归档逻辑。
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class StatShowArchiveJobIT {

    private static final String SERVER = "jdbc:mysql://127.0.0.1:3306/?useSSL=false&allowPublicKeyRetrieval=true&serverTimezone=Asia/Shanghai";
    private static final String DB = "stat_arch_test";
    private static final String URL = "jdbc:mysql://127.0.0.1:3306/" + DB + "?useSSL=false&allowPublicKeyRetrieval=true&serverTimezone=Asia/Shanghai";
    private static final String USER = "root";
    private static final String PWD = "djdev";

    private static final String CUST = "XAN_M98_CUST_STAT_SHOW3";
    private static final String EMP = "XAN_M98_EMP_STAT_SHOW3";
    private static final String[] MAINS = {CUST, EMP};

    private SqlSession session;
    private StatShowArchiveJob job;

    @BeforeAll
    void setUpSchemaAndJob() throws Exception {
        // 1) 建专用库
        try (Connection c = DriverManager.getConnection(SERVER, USER, PWD); Statement s = c.createStatement()) {
            s.execute("CREATE DATABASE IF NOT EXISTS " + DB + " DEFAULT CHARACTER SET utf8mb4");
        }
        // 2) 建 8 表（2 主表 + 各 3 历史表，最小同构结构）
        try (Connection c = DriverManager.getConnection(URL, USER, PWD); Statement s = c.createStatement()) {
            for (String m : MAINS) {
                for (String t : new String[]{m, m + "_H1", m + "_H2", m + "_H3"}) {
                    s.execute("DROP TABLE IF EXISTS " + t);
                    s.execute("CREATE TABLE " + t + " (STATIS_DT VARCHAR(10), ID VARCHAR(32), VAL VARCHAR(64))");
                }
            }
        }
        // 3) 手工 MyBatis（加载真实 mapper XML），autocommit=true 与生产"逐条提交、不加@Transactional"一致
        UnpooledDataSource ds = new UnpooledDataSource("com.mysql.cj.jdbc.Driver", URL, USER, PWD);
        Configuration config = new Configuration(new Environment("it", new JdbcTransactionFactory(), ds));
        try (InputStream in = Resources.getResourceAsStream("mapper/performance/StatShowArchiveMapper.xml")) {
            new XMLMapperBuilder(in, config, "mapper/performance/StatShowArchiveMapper.xml", config.getSqlFragments()).parse();
        }
        SqlSessionFactory factory = new SqlSessionFactoryBuilder().build(config);
        session = factory.openSession(true);
        job = new StatShowArchiveJob(session.getMapper(StatShowArchiveMapper.class));
    }

    @AfterAll
    void dropSchema() throws Exception {
        if (session != null) {
            session.close();
        }
        try (Connection c = DriverManager.getConnection(SERVER, USER, PWD); Statement s = c.createStatement()) {
            s.execute("DROP DATABASE IF EXISTS " + DB);
        }
    }

    @BeforeEach
    void truncateAll() throws Exception {
        try (Connection c = DriverManager.getConnection(URL, USER, PWD); Statement s = c.createStatement()) {
            for (String m : MAINS) {
                for (String t : new String[]{m, m + "_H1", m + "_H2", m + "_H3"}) {
                    s.execute("TRUNCATE TABLE " + t);
                }
            }
        }
    }

    // ---------- helpers ----------

    private void seed(String table, String dt, int n) throws Exception {
        try (Connection c = DriverManager.getConnection(URL, USER, PWD); Statement s = c.createStatement()) {
            for (int i = 0; i < n; i++) {
                s.execute("INSERT INTO " + table + "(STATIS_DT,ID,VAL) VALUES ('" + dt + "','" + dt + "_" + i + "','v')");
            }
        }
    }

    /** 两张主表都灌同样数据（对应用户要求：两主表都要有对应数据）. */
    private void seedBothMains(String dt, int n) throws Exception {
        for (String m : MAINS) {
            seed(m, dt, n);
        }
    }

    private void execSql(String sql) throws Exception {
        try (Connection c = DriverManager.getConnection(URL, USER, PWD); Statement s = c.createStatement()) {
            s.execute(sql);
        }
    }

    private long cnt(String table) throws Exception {
        return cntWhere(table, null);
    }

    private long cntDt(String table, String dt) throws Exception {
        return cntWhere(table, "STATIS_DT='" + dt + "'");
    }

    private long cntWhere(String table, String where) throws Exception {
        String sql = "SELECT COUNT(*) FROM " + table + (where == null ? "" : " WHERE " + where);
        try (Connection c = DriverManager.getConnection(URL, USER, PWD);
             Statement s = c.createStatement();
             ResultSet rs = s.executeQuery(sql)) {
            rs.next();
            return rs.getLong(1);
        }
    }

    // ---------- 场景 ----------

    @Test
    @DisplayName("日增量：run(07-06) 把 07-01~昨天(07-05) 搬进 _H2，不含今天07-06；主表不变")
    void dailyIncremental_intoH2() throws Exception {
        for (String d : new String[]{"2026-07-01", "2026-07-02", "2026-07-03", "2026-07-04", "2026-07-05", "2026-07-06"}) {
            seedBothMains(d, 2);
        }
        job.run(LocalDate.of(2026, 7, 6));
        for (String m : MAINS) {
            assertThat(cnt(m + "_H2")).isEqualTo(10);              // 07-01~07-05，5 天 ×2
            assertThat(cntDt(m + "_H2", "2026-07-06")).isZero();   // 今天不搬
            assertThat(cntDt(m + "_H2", "2026-07-01")).isEqualTo(2);
            assertThat(cnt(m)).isEqualTo(12);                      // 主表 6 天 ×2 不变
            assertThat(cnt(m + "_H1")).isZero();
            assertThat(cnt(m + "_H3")).isZero();
        }
    }

    @Test
    @DisplayName("幂等：重复 run(07-06) 结果不变、无重复行")
    void idempotent_repeatRun() throws Exception {
        for (String d : new String[]{"2026-07-01", "2026-07-02", "2026-07-03", "2026-07-04", "2026-07-05"}) {
            seedBothMains(d, 2);
        }
        job.run(LocalDate.of(2026, 7, 6));
        job.run(LocalDate.of(2026, 7, 6));
        for (String m : MAINS) {
            assertThat(cnt(m + "_H2")).isEqualTo(10);
        }
    }

    @Test
    @DisplayName("补全：删掉 _H2 中间某天后再 run，自动补回")
    void gapFill_backfillsMissingDay() throws Exception {
        for (String d : new String[]{"2026-07-01", "2026-07-02", "2026-07-03", "2026-07-04", "2026-07-05"}) {
            seedBothMains(d, 2);
        }
        job.run(LocalDate.of(2026, 7, 6));
        for (String m : MAINS) {
            execSql("DELETE FROM " + m + "_H2 WHERE STATIS_DT='2026-07-03'");
            assertThat(cntDt(m + "_H2", "2026-07-03")).isZero();
        }
        job.run(LocalDate.of(2026, 7, 6));
        for (String m : MAINS) {
            assertThat(cntDt(m + "_H2", "2026-07-03")).isEqualTo(2);
        }
    }

    @Test
    @DisplayName("count 不一致：主表某日新增后 run，历史表该日重抽为最新")
    void resync_onCountMismatch() throws Exception {
        seedBothMains("2026-07-05", 2);
        job.run(LocalDate.of(2026, 7, 6));
        for (String m : MAINS) {
            assertThat(cntDt(m + "_H2", "2026-07-05")).isEqualTo(2);
        }
        seedBothMains("2026-07-05", 3); // 主表 07-05 变 5，历史表仍 2 → count 不一致
        job.run(LocalDate.of(2026, 7, 6));
        for (String m : MAINS) {
            assertThat(cntDt(m + "_H2", "2026-07-05")).isEqualTo(5);
        }
    }

    @Test
    @DisplayName("边界 11 号：_H2 填当月1-10 且清上月1-10")
    void boundary11_fillCurrent_cleanPrev_H2() throws Exception {
        for (int d = 1; d <= 10; d++) {
            seedBothMains(String.format("2026-07-%02d", d), 2);        // 当月 7 月 1-10
        }
        for (String m : MAINS) {
            for (int d = 1; d <= 10; d++) {
                seed(m + "_H2", String.format("2026-06-%02d", d), 2);  // 上一代：6 月 1-10 预置在 _H2
            }
        }
        job.run(LocalDate.of(2026, 7, 11)); // 昨天=07-10
        for (String m : MAINS) {
            assertThat(cntDt(m + "_H2", "2026-07-10")).isEqualTo(2);              // 当月已填
            assertThat(cntWhere(m + "_H2", "STATIS_DT LIKE '2026-06-%'")).isZero(); // 上月已清
        }
    }

    @Test
    @DisplayName("边界 21 号：_H3 填当月11-20 且清上月11-20")
    void boundary21_fillCurrent_cleanPrev_H3() throws Exception {
        for (int d = 11; d <= 20; d++) {
            seedBothMains(String.format("2026-07-%02d", d), 2);
        }
        for (String m : MAINS) {
            for (int d = 11; d <= 20; d++) {
                seed(m + "_H3", String.format("2026-06-%02d", d), 2);
            }
        }
        job.run(LocalDate.of(2026, 7, 21)); // 昨天=07-20
        for (String m : MAINS) {
            assertThat(cntDt(m + "_H3", "2026-07-20")).isEqualTo(2);
            assertThat(cntWhere(m + "_H3", "STATIS_DT LIKE '2026-06-%'")).isZero();
        }
    }

    @Test
    @DisplayName("边界 1 号：_H1 填上月21-末、清上上月21-末、主表瘦身只留上月月末")
    void boundary1_fillH1_cleanTwoMonthsAgo_pruneMain() throws Exception {
        for (int d = 1; d <= 31; d++) {
            seedBothMains(String.format("2026-07-%02d", d), 2);        // 主表 7 月整月
        }
        for (String m : MAINS) {
            for (int d = 21; d <= 30; d++) {
                seed(m + "_H1", String.format("2026-06-%02d", d), 2);  // 上上月：6 月 21-30 预置在 _H1
            }
        }
        job.run(LocalDate.of(2026, 8, 1)); // 昨天=07-31
        for (String m : MAINS) {
            // _H1 填 7 月 21-31
            assertThat(cntDt(m + "_H1", "2026-07-31")).isEqualTo(2);
            assertThat(cntDt(m + "_H1", "2026-07-21")).isEqualTo(2);
            assertThat(cntWhere(m + "_H1", "STATIS_DT LIKE '2026-06-%'")).isZero(); // 上上月已清
            // 主表瘦身：7 月只剩月末 07-31
            assertThat(cntWhere(m, "STATIS_DT LIKE '2026-07-%'")).isEqualTo(2);
            assertThat(cntDt(m, "2026-07-31")).isEqualTo(2);
            assertThat(cntDt(m, "2026-07-15")).isZero();
        }
    }

    @Test
    @DisplayName("瘦身保护：上月缺月末当天时，保留该月实际 MAX(STATIS_DT)，不误删整月")
    void prune_keepsActualMaxWhenMonthEndMissing() throws Exception {
        for (int d = 1; d <= 30; d++) { // 7 月只到 30 号，没有 31
            seedBothMains(String.format("2026-07-%02d", d), 2);
        }
        job.run(LocalDate.of(2026, 8, 1));
        for (String m : MAINS) {
            assertThat(cntWhere(m, "STATIS_DT LIKE '2026-07-%'")).isEqualTo(2); // 只剩 1 天
            assertThat(cntDt(m, "2026-07-30")).isEqualTo(2);                    // 保留实际最大 07-30
        }
    }

    @Test
    @DisplayName("瘦身保护：上月无任何数据时不删主表")
    void prune_skipsWhenNoData() throws Exception {
        seedBothMains("2026-08-01", 2); // 只有 8 月，没有 7 月
        job.run(LocalDate.of(2026, 8, 1));
        for (String m : MAINS) {
            assertThat(cnt(m)).isEqualTo(2); // 未误删
        }
    }
}
