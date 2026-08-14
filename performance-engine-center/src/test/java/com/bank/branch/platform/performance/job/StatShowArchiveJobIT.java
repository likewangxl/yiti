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
 * StatShowArchiveJob 端到端集成测试（专用 MySQL 库）：验证 tmp 单日写入、边界 truncate、
 * 1 号主表幂等写入和当日重复触发。
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class StatShowArchiveJobIT {

    private static final String SERVER = "jdbc:mysql://127.0.0.1:3306/?useSSL=false&allowPublicKeyRetrieval=true&serverTimezone=Asia/Shanghai";
    private static final String DB = "stat_arch_test";
    private static final String URL = "jdbc:mysql://127.0.0.1:3306/" + DB
            + "?useSSL=false&allowPublicKeyRetrieval=true&serverTimezone=Asia/Shanghai";
    private static final String USER = "root";
    private static final String PWD = "djdev";

    private static final String CUST = "XAN_M98_CUST_STAT_SHOW3";
    private static final String EMP = "XAN_M98_EMP_STAT_SHOW3";
    private static final String[] MAINS = {CUST, EMP};

    private SqlSession session;
    private StatShowArchiveJob job;

    @BeforeAll
    void setUpSchemaAndJob() throws Exception {
        try (Connection c = DriverManager.getConnection(SERVER, USER, PWD); Statement s = c.createStatement()) {
            s.execute("CREATE DATABASE IF NOT EXISTS " + DB + " DEFAULT CHARACTER SET utf8mb4");
        }
        try (Connection c = DriverManager.getConnection(URL, USER, PWD); Statement s = c.createStatement()) {
            for (String main : MAINS) {
                for (String table : new String[]{main, main + "_tmp", main + "_H1", main + "_H2", main + "_H3"}) {
                    s.execute("DROP TABLE IF EXISTS " + table);
                    s.execute("CREATE TABLE " + table
                            + " (STATIS_DT VARCHAR(10), ID VARCHAR(32), VAL VARCHAR(64))");
                }
            }
        }
        UnpooledDataSource ds = new UnpooledDataSource("com.mysql.cj.jdbc.Driver", URL, USER, PWD);
        Configuration config = new Configuration(new Environment("it", new JdbcTransactionFactory(), ds));
        try (InputStream in = Resources.getResourceAsStream("mapper/performance/StatShowArchiveMapper.xml")) {
            new XMLMapperBuilder(in, config, "mapper/performance/StatShowArchiveMapper.xml",
                    config.getSqlFragments()).parse();
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
            for (String main : MAINS) {
                for (String table : new String[]{main, main + "_tmp", main + "_H1", main + "_H2", main + "_H3"}) {
                    s.execute("TRUNCATE TABLE " + table);
                }
            }
        }
    }

    private void seed(String table, String dt, int n) throws Exception {
        try (Connection c = DriverManager.getConnection(URL, USER, PWD); Statement s = c.createStatement()) {
            for (int i = 0; i < n; i++) {
                s.execute("INSERT INTO " + table + "(STATIS_DT,ID,VAL) VALUES ('"
                        + dt + "','" + dt + "_" + i + "','v')");
            }
        }
    }

    private void seedBothTmp(String dt, int n) throws Exception {
        for (String main : MAINS) {
            seed(main + "_tmp", dt, n);
        }
    }

    private long cnt(String table) throws Exception {
        try (Connection c = DriverManager.getConnection(URL, USER, PWD);
             Statement s = c.createStatement();
             ResultSet rs = s.executeQuery("SELECT COUNT(*) FROM " + table)) {
            rs.next();
            return rs.getLong(1);
        }
    }

    private long cntDt(String table, String dt) throws Exception {
        try (Connection c = DriverManager.getConnection(URL, USER, PWD);
             Statement s = c.createStatement();
             ResultSet rs = s.executeQuery("SELECT COUNT(*) FROM " + table
                     + " WHERE STATIS_DT='" + dt + "'")) {
            rs.next();
            return rs.getLong(1);
        }
    }

    @Test
    @DisplayName("普通日只把 tmp 的 T-1 写入运行日对应 H 表")
    void ordinaryDay_copiesOnlyTmpTMinusOne() throws Exception {
        seedBothTmp("2026-08-14", 2);
        for (String main : MAINS) {
            seed(main + "_H2", "2026-08-13", 1);
        }

        job.run(LocalDate.of(2026, 8, 15));

        for (String main : MAINS) {
            assertThat(cntDt(main + "_H2", "2026-08-14")).isEqualTo(2);
            assertThat(cntDt(main + "_H2", "2026-08-13")).isEqualTo(1);
            assertThat(cnt(main + "_H1")).isZero();
            assertThat(cnt(main + "_H3")).isZero();
        }
    }

    @Test
    @DisplayName("普通日重复触发按日期 delete+insert 保持幂等")
    void ordinaryDay_repeatRun_isIdempotent() throws Exception {
        seedBothTmp("2026-08-14", 2);

        job.run(LocalDate.of(2026, 8, 15));
        job.run(LocalDate.of(2026, 8, 15));

        for (String main : MAINS) {
            assertThat(cntDt(main + "_H2", "2026-08-14")).isEqualTo(2);
        }
    }

    @Test
    @DisplayName("11 号先清空 H2，再写入 tmp 的 10 号数据")
    void boundaryEleventh_truncatesH2BeforeInsert() throws Exception {
        seedBothTmp("2026-08-10", 2);
        for (String main : MAINS) {
            seed(main + "_H2", "2026-07-10", 3);
        }

        job.run(LocalDate.of(2026, 8, 11));

        for (String main : MAINS) {
            assertThat(cnt(main + "_H2")).isEqualTo(2);
            assertThat(cntDt(main + "_H2", "2026-08-10")).isEqualTo(2);
            assertThat(cntDt(main + "_H2", "2026-07-10")).isZero();
        }
    }

    @Test
    @DisplayName("1 号清 H1，并将上月月末 T-1 按日期幂等写入主表")
    void firstDay_writesPreviousMonthEndToH1AndMain() throws Exception {
        seedBothTmp("2026-07-31", 2);
        for (String main : MAINS) {
            seed(main + "_H1", "2026-06-30", 3);
            seed(main, "2026-07-31", 1);
            seed(main, "2026-08-01", 1);
        }

        job.run(LocalDate.of(2026, 8, 1));

        for (String main : MAINS) {
            assertThat(cnt(main + "_H1")).isEqualTo(2);
            assertThat(cntDt(main + "_H1", "2026-07-31")).isEqualTo(2);
            assertThat(cntDt(main + "_H1", "2026-06-30")).isZero();
            assertThat(cntDt(main, "2026-07-31")).isEqualTo(2);
            assertThat(cntDt(main, "2026-08-01")).isEqualTo(1);
        }
    }
}
