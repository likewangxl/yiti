package com.bank.branch.platform.portal.mapper;

import com.bank.branch.platform.portal.support.AbstractMapperIntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.jdbc.Sql;

import javax.sql.DataSource;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * GuaranteeSyncMapper 集成测试（直连本地 MySQL onepl_test_bootstrap）。
 *
 * <p>覆盖 {@code updateToGuarantee} / {@code saveToGuarantee} 两条核心同步 SQL 的语义，
 * 作为性能重写（自连接 groupwise-max → ROW_NUMBER 单遍去重）的回归护栏：
 * 重写前后行为必须完全一致。</p>
 *
 * <p>口径要点：按客户取「最大 creditno」的一条；只取当日(hive_sys_time)、未过期、credittype=010010、
 * 客户名含「担保」的记录；update 按客户名匹配刷新已存在客户，save 只补录尚不存在的客户。</p>
 */
@Sql(scripts = "/sql/clean-guarantee-sync.sql", executionPhase = Sql.ExecutionPhase.BEFORE_TEST_METHOD)
class GuaranteeSyncMapperIntegrationTest extends AbstractMapperIntegrationTest {

    /** 同步基准日：所有当日 clms 造数的 hive_sys_time 取这一天。 */
    private static final String YDATE = "2026-06-29";

    @Autowired
    private GuaranteeSyncMapper mapper;

    private JdbcTemplate jdbc;

    @Autowired
    void setDataSource(DataSource dataSource) {
        this.jdbc = new JdbcTemplate(dataSource);
    }

    /**
     * updateToGuarantee：已存在客户应被「当日最大 creditno」那条记录刷新，且字段映射正确；
     * 非担保 / 已过期 / 错类型 / 非当日的记录被过滤；今日 clms 没有的已存在客户保持不变。
     */
    @Test
    void updateToGuarantee_refreshesExistingCustomerWithMaxCreditnoRow() {
        // C1：同一客户两条 creditno，最大 002 应胜出
        insertClms("TEST_CR_C1_001", "010010", 100, 11, 21, "2026-01-01", "2030/12/31",
                "TEST_C1", "TEST_担保甲", "OP1", YDATE + " 08:00:00");
        insertClms("TEST_CR_C1_002", "010010", 200, 12, 22, "2026-02-02", "2031/01/01",
                "TEST_C1", "TEST_担保甲", "OP2", YDATE + " 08:00:00");
        // 各类应被过滤的噪声
        insertClms("TEST_CR_N1", "010010", 9, 9, 9, "2026-01-01", "2020-01-01",
                "TEST_N1", "TEST_担保丙过期", "OPx", YDATE + " 08:00:00");        // 已过期
        insertClms("TEST_CR_N2", "010010", 9, 9, 9, "2026-01-01", "2030/12/31",
                "TEST_N2", "TEST_非担保客户", "OPx", YDATE + " 08:00:00");        // 名称不含担保
        insertClms("TEST_CR_N3", "000020", 9, 9, 9, "2026-01-01", "2030/12/31",
                "TEST_N3", "TEST_担保丁错类型", "OPx", YDATE + " 08:00:00");      // 错类型
        insertClms("TEST_CR_N4", "010010", 9, 9, 9, "2026-01-01", "2030/12/31",
                "TEST_N4", "TEST_担保戊昨日", "OPx", "2026-06-28 08:00:00");      // 非当日

        // 担保表已存在该客户（旧值）+ 一个今日 clms 没有的客户（应保持不变）
        insertGuarantee("TEST_OLD_C1", "TEST_担保甲", "OLD", "OLD", "OLD-OP");
        insertGuarantee("TEST_OLD_KEEP", "TEST_担保己无更新", "KEEP", "KEEP", "KEEP-OP");

        int updated = mapper.updateToGuarantee(YDATE);

        assertThat(updated).isEqualTo(1);
        Map<String, Object> c1 = selectGuarantee("TEST_担保甲");
        assertThat(c1.get("client_num")).isEqualTo("TEST_C1");
        assertThat(c1.get("notional_amount")).isEqualTo("200.000000");        // execnominalsum (002)
        assertThat(c1.get("occupy_notional_amount")).isEqualTo("12.000000");  // usablenominalsum (002)
        assertThat(c1.get("occupy_exposure_amount")).isEqualTo("12.000000");  // usablenominalsum (002)
        assertThat(c1.get("usablenominalsum")).isEqualTo("22.000000");        // suboccupynominalsum (002)
        assertThat(c1.get("start")).isEqualTo("2026-02-02");
        assertThat(c1.get("last_expire")).isEqualTo("2031/01/01");
        assertThat(c1.get("operator")).isEqualTo("OP2");

        // 今日 clms 无此客户 → 原行保持不变
        Map<String, Object> keep = selectGuarantee("TEST_担保己无更新");
        assertThat(keep.get("client_num")).isEqualTo("TEST_OLD_KEEP");
        assertThat(keep.get("operator")).isEqualTo("KEEP-OP");
    }

    /**
     * saveToGuarantee：只补录担保表尚不存在的当日担保客户；已存在客户不重复插入；
     * 噪声记录（非担保/过期/错类型/非当日）不插入。
     */
    @Test
    void saveToGuarantee_insertsOnlyMissingCustomers() {
        // C1 已存在（应跳过）；C2 不存在（应插入）
        insertClms("TEST_CR_C1_001", "010010", 100, 11, 21, "2026-01-01", "2030/12/31",
                "TEST_C1", "TEST_担保甲", "OP1", YDATE + " 08:00:00");
        insertClms("TEST_CR_C2_001", "010010", 300, 33, 43, "2026-03-03", "2032/05/05",
                "TEST_C2", "TEST_担保乙", "OP3", YDATE + " 08:00:00");
        insertClms("TEST_CR_N1", "010010", 9, 9, 9, "2026-01-01", "2020-01-01",
                "TEST_N1", "TEST_担保丙过期", "OPx", YDATE + " 08:00:00");
        insertClms("TEST_CR_N3", "000020", 9, 9, 9, "2026-01-01", "2030/12/31",
                "TEST_N3", "TEST_担保丁错类型", "OPx", YDATE + " 08:00:00");

        insertGuarantee("TEST_OLD_C1", "TEST_担保甲", "OLD", "OLD", "OLD-OP");

        int inserted = mapper.saveToGuarantee(YDATE);

        assertThat(inserted).isEqualTo(1);
        // 担保甲未被重复插入
        assertThat(countGuarantee("TEST_担保甲")).isEqualTo(1);
        // 担保乙被补录，字段映射正确
        Map<String, Object> c2 = selectGuarantee("TEST_担保乙");
        assertThat(c2.get("client_num")).isEqualTo("TEST_C2");
        assertThat(c2.get("notional_amount")).isEqualTo("300.000000");
        assertThat(c2.get("occupy_notional_amount")).isEqualTo("33.000000");
        assertThat(c2.get("usablenominalsum")).isEqualTo("43.000000");
        assertThat(c2.get("start")).isEqualTo("2026-03-03");
        assertThat(c2.get("last_expire")).isEqualTo("2032/05/05");
        assertThat(c2.get("operator")).isEqualTo("OP3");
        // 噪声未插入
        assertThat(countGuarantee("TEST_担保丙过期")).isZero();
        assertThat(countGuarantee("TEST_担保丁错类型")).isZero();
    }

    // ---------- fixtures ----------

    private void insertClms(String creditno, String credittype, double exec, double usable,
                            double suboccupy, String startdate, String expiredate,
                            String customerid, String customername, String inputuserid, String hiveTime) {
        jdbc.update("INSERT INTO clms_ed_credit_info "
                        + "(creditno, credittype, execnominalsum, usablenominalsum, suboccupynominalsum, "
                        + "startdate, expiredate, customerid, customername, inputuserid, hive_sys_time) "
                        + "VALUES (?,?,?,?,?,?,?,?,?,?,?)",
                creditno, credittype, exec, usable, suboccupy,
                startdate, expiredate, customerid, customername, inputuserid, hiveTime);
    }

    private void insertGuarantee(String clientNum, String clientName, String notional,
                                 String usable, String operator) {
        jdbc.update("INSERT INTO zh_guarantee_info "
                        + "(client_num, client_name, notional_amount, usablenominalsum, operator) "
                        + "VALUES (?,?,?,?,?)",
                clientNum, clientName, notional, usable, operator);
    }

    private Map<String, Object> selectGuarantee(String clientName) {
        return jdbc.queryForMap("SELECT * FROM zh_guarantee_info WHERE client_name = ?", clientName);
    }

    private int countGuarantee(String clientName) {
        Integer n = jdbc.queryForObject(
                "SELECT COUNT(1) FROM zh_guarantee_info WHERE client_name = ?", Integer.class, clientName);
        return n == null ? 0 : n;
    }
}
