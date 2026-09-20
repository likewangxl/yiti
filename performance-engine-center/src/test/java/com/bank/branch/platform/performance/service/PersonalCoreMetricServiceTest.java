package com.bank.branch.platform.performance.service;

import com.bank.branch.platform.auth.api.UserApi;
import com.bank.branch.platform.auth.api.dto.UserDTO;
import com.bank.branch.platform.performance.api.dto.MetricCardDTO;
import com.bank.branch.platform.performance.entity.PerfMetricDef;
import com.bank.branch.platform.performance.entity.EmpIndexResult;
import com.bank.branch.platform.performance.exception.PerfException;
import com.bank.branch.platform.performance.mapper.EmpIndexResultMapper;
import com.bank.branch.platform.performance.support.PerformanceServiceTestBase;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * PersonalCoreMetricService 单元测试。
 *
 * <p>只使用 Mockito，不访问真实数据库；验证个人核心指标只读取 EMP 当前发布版本，
 * 并按指标定义名称选择真实宽表值。</p>
 */
class PersonalCoreMetricServiceTest extends PerformanceServiceTestBase {

    @Mock
    private MetricDefService metricDefService;

    @Mock
    private UserApi userApi;

    @Mock
    private EmpIndexResultMapper empIndexResultMapper;

    @InjectMocks
    private PersonalCoreMetricService service;

    @BeforeEach
    void setUpIdentity() {
        lenient().when(userApi.getUserByEmpId("E001"))
                .thenReturn(user("E001", "EMP_REAL"));
    }

    @Test
    @DisplayName("无 KPI 方案依赖时仍返回六张真实 EMP 核心卡，优先有值项并保留 0")
    void returnsCoreCardsWithoutKpiSchemes() {
        String empId = "E001";
        LocalDate current = LocalDate.of(2026, 8, 13);
        LocalDate previous = LocalDate.of(2026, 7, 31);
        LocalDate yearAgo = LocalDate.of(2025, 8, 13);
        when(empIndexResultMapper.selectLatestRowForEmployee(eq("EMP_REAL"), any(LocalDate.class)))
                .thenReturn(row(100L, "EMP_REAL", current, "import-v2"));
        when(empIndexResultMapper.selectLatestRowsForEmployeeDates(
                eq("EMP_REAL"), eq(List.of(previous, yearAgo)), any(LocalDate.class)))
                .thenReturn(List.of(
                        row(101L, "EMP_REAL", previous, "import-v1"),
                        row(102L, "EMP_REAL", yearAgo, "import-v1")));

        PerfMetricDef publicBalance = def("M_PUBLIC_BAL", "对公一般性存款余额-员工", 1, "万元");
        PerfMetricDef retailBalance = def("M_RETAIL_BAL", "零售一般性存款余额-员工", 2, "万元");
        PerfMetricDef monthlyBalance = def("M_MONTHLY_BAL", "一般性存款月均余额-员工", 3, "万元");
        PerfMetricDef yearlyBalance = def("M_YEARLY_BAL", "一般性存款年日均余额-员工", 4, null);
        PerfMetricDef monthlyIncrease = def("M_MONTHLY_INCREASE", "一般性存款月均余额较上月-员工", 5, "%");
        PerfMetricDef publicLoan = def("M_PUBLIC_LOAN", "对公一般性贷款余额-员工", 6, "万元");
        PerfMetricDef publicLoanIncrease = def("M_PUBLIC_LOAN_INCREASE", "对公一般性贷款余额较上月-员工", 7, "万元");
        when(metricDefService.listActiveMetrics("EMP", null)).thenReturn(List.of(
                publicBalance, retailBalance, monthlyBalance, yearlyBalance,
                monthlyIncrease, publicLoan, publicLoanIncrease));

        Map<Long, Map<Integer, BigDecimal>> values = new LinkedHashMap<>();
        values.put(100L, Map.of(2, new BigDecimal("20"), 3, BigDecimal.ZERO,
                4, new BigDecimal("30"), 5, new BigDecimal("10"),
                6, new BigDecimal("50"), 7, new BigDecimal("60")));
        values.put(101L, Map.of(3, new BigDecimal("100"), 5, new BigDecimal("8"),
                7, new BigDecimal("40")));
        values.put(102L, Map.of(4, new BigDecimal("25")));
        when(empIndexResultMapper.selectSlotValuesByRowIdsAndSlots(
                eq(empId.equals("E001") ? "EMP_REAL" : empId),
                eq(List.of(100L, 101L, 102L)), any(LocalDate.class), anyList()))
                .thenReturn(values);

        List<MetricCardDTO> cards = service.getPersonalCoreMetricCards(empId);

        assertThat(cards).hasSize(6);
        assertThat(cards).extracting(MetricCardDTO::getMetricCode)
                .containsExactly(
                        "M_MONTHLY_BAL", "M_YEARLY_BAL", "M_MONTHLY_INCREASE",
                        "M_PUBLIC_LOAN", "M_PUBLIC_LOAN_INCREASE", "M_RETAIL_BAL");

        MetricCardDTO fallback = cards.stream()
                .filter(card -> "M_RETAIL_BAL".equals(card.getMetricCode()))
                .findFirst().orElseThrow();
        assertThat(fallback.getMetricName()).isEqualTo("零售一般性存款余额-员工");
        assertThat(fallback.getCurrentValue()).isEqualByComparingTo("20");
        assertThat(fallback.getUnit()).isEqualTo("万元");

        MetricCardDTO zero = cards.stream()
                .filter(card -> "M_MONTHLY_BAL".equals(card.getMetricCode()))
                .findFirst().orElseThrow();
        assertThat(zero.getCurrentValue()).isZero();
        assertThat(zero.getPreviousValue()).isEqualByComparingTo("100");
        assertThat(zero.getMom()).isEqualByComparingTo("-100.00");

        MetricCardDTO noUnit = cards.stream()
                .filter(card -> "M_YEARLY_BAL".equals(card.getMetricCode()))
                .findFirst().orElseThrow();
        assertThat(noUnit.getUnit()).isNull();
        assertThat(noUnit.getYoy()).isEqualByComparingTo("20.00");

        assertThat(cards.get(3).getMom())
                .as("名称含“较”的增量类指标不应再次计算环比")
                .isNull();
        assertThat(cards.get(3).getYoy())
                .as("名称含“较”的增量类指标不应再次计算同比")
                .isNull();
        assertThat(cards).allSatisfy(card -> {
            assertThat(card.getTargetValue()).isNull();
            assertThat(card.getAchievementRate()).isNull();
            assertThat(card.getDataDate()).isEqualTo(current);
        });

        ArgumentCaptor<List<Integer>> slots = ArgumentCaptor.forClass(List.class);
        verify(empIndexResultMapper).selectSlotValuesByRowIdsAndSlots(
                eq("EMP_REAL"), eq(List.of(100L, 101L, 102L)), any(LocalDate.class), slots.capture());
        assertThat(slots.getValue()).containsExactly(1, 3, 4, 5, 6, 7, 2);
    }

    @Test
    @DisplayName("只接受 ACTIVE、未删除、EMP 且 slot 在 1..400 的定义")
    void filtersInvalidDefinitions() {
        LocalDate current = LocalDate.of(2026, 8, 13);
        when(empIndexResultMapper.selectLatestRowForEmployee(eq("EMP_REAL"), any(LocalDate.class)))
                .thenReturn(row(200L, "EMP_REAL", current, "v"));

        PerfMetricDef valid = def("M_VALID", "一般性存款月均余额-员工", 8, "万元");
        PerfMetricDef disabled = def("M_DISABLED", "对公一般性存款余额-员工", 9, "万元");
        disabled.setStatus("DISABLED");
        PerfMetricDef deleted = def("M_DELETED", "一般性存款年日均余额-员工", 10, "万元");
        deleted.setDeleted(1);
        PerfMetricDef org = def("M_ORG", "对公一般性贷款余额-员工", 11, "万元");
        org.setBaseDim("ORG");
        PerfMetricDef zeroSlot = def("M_ZERO_SLOT", "一般性存款月均余额较上月-员工", 0, "%");
        PerfMetricDef highSlot = def("M_HIGH_SLOT", "对公一般性贷款余额较上月-员工", 401, "%");
        when(metricDefService.listActiveMetrics("EMP", null)).thenReturn(
                List.of(valid, disabled, deleted, org, zeroSlot, highSlot));
        when(empIndexResultMapper.selectSlotValuesByRowIdsAndSlots(
                eq("EMP_REAL"), eq(List.of(200L)), any(LocalDate.class), eq(List.of(8))))
                .thenReturn(Map.of(200L, Map.of(8, new BigDecimal("1"))));

        List<MetricCardDTO> cards = service.getPersonalCoreMetricCards("E001");

        assertThat(cards).hasSize(1);
        assertThat(cards.get(0).getMetricCode()).isEqualTo("M_VALID");
        verify(empIndexResultMapper).selectSlotValuesByRowIdsAndSlots(
                eq("EMP_REAL"), eq(List.of(200L)), any(LocalDate.class), eq(List.of(8)));
    }

    @Test
    @DisplayName("同名不同 code/slot 的定义不任取其一，继续选择后续无歧义候选")
    void skipsAmbiguousNormalizedName() {
        LocalDate current = LocalDate.of(2026, 8, 13);
        when(empIndexResultMapper.selectLatestRowForEmployee(eq("EMP_REAL"), any(LocalDate.class)))
                .thenReturn(row(300L, "EMP_REAL", current, "v"));
        PerfMetricDef duplicateA = def("M_DUP_A", "一般性存款月均余额-员工", 12, "万元");
        PerfMetricDef duplicateB = def("M_DUP_B", "一般性存款月均余额-员工", 13, "元");
        PerfMetricDef unambiguous = def("M_YEARLY", "一般性存款年日均余额-员工", 14, "万元");
        when(metricDefService.listActiveMetrics("EMP", null))
                .thenReturn(List.of(duplicateA, duplicateB, unambiguous));
        when(empIndexResultMapper.selectSlotValuesByRowIdsAndSlots(
                eq("EMP_REAL"), eq(List.of(300L)), any(LocalDate.class), eq(List.of(14))))
                .thenReturn(Map.of(300L, Map.of(14, new BigDecimal("8"))));

        List<MetricCardDTO> cards = service.getPersonalCoreMetricCards("E001");

        assertThat(cards).hasSize(1);
        assertThat(cards.get(0).getMetricCode()).isEqualTo("M_YEARLY");
        verify(empIndexResultMapper).selectSlotValuesByRowIdsAndSlots(
                eq("EMP_REAL"), eq(List.of(300L)), any(LocalDate.class), eq(List.of(14)));
    }

    @Test
    @DisplayName("本人没有宽表行时保留匹配的核心定义，但不借用其他员工日期或值")
    void noPersonalRow_keepsCoreDefinitionsWithNullValues() {
        when(empIndexResultMapper.selectLatestRowForEmployee(eq("EMP_REAL"), any(LocalDate.class)))
                .thenReturn(null);
        when(metricDefService.listActiveMetrics("EMP", null)).thenReturn(List.of(
                def("M_CORE_1", "对公一般性存款余额-员工", 21, null),
                def("M_CORE_2", "一般性存款月均余额-员工", 22, null),
                def("M_CORE_3", "一般性存款年日均余额-员工", 23, null),
                def("M_CORE_4", "一般性存款月均余额较上月-员工", 24, null),
                def("M_CORE_5", "对公一般性贷款余额-员工", 25, null),
                def("M_CORE_6", "对公一般性贷款余额较上月-员工", 26, null)));

        List<MetricCardDTO> cards = service.getPersonalCoreMetricCards("E001");

        assertThat(cards).hasSize(6);
        assertThat(cards).allSatisfy(card -> {
            assertThat(card.getCurrentValue()).isNull();
            assertThat(card.getPreviousValue()).isNull();
            assertThat(card.getDataDate()).isNull();
        });
        verify(empIndexResultMapper, never()).selectLatestRowsForEmployeeDates(
                anyString(), anyList(), any(LocalDate.class));
        verify(empIndexResultMapper, never()).selectSlotValuesByRowIdsAndSlots(
                anyString(), anyList(), any(LocalDate.class), anyList());
    }

    @Test
    @DisplayName("空员工号或身份映射异常直接失败，不回退其他工号")
    void invalidIdentityVersionOrDate_returnsEmpty() {
        assertThatThrownBy(() -> service.getPersonalCoreMetricCards(null))
                .isInstanceOf(PerfException.class);
        assertThatThrownBy(() -> service.getPersonalCoreMetricCards("  "))
                .isInstanceOf(PerfException.class);
        verify(userApi, never()).getUserByEmpId("E001");

        when(userApi.getUserByEmpId("UNKNOWN")).thenReturn(null);
        assertThatThrownBy(() -> service.getPersonalCoreMetricCards("UNKNOWN"))
                .isInstanceOf(PerfException.class);
        verify(empIndexResultMapper, never()).selectLatestRowForEmployee(
                eq("UNKNOWN"), any(LocalDate.class));

        when(userApi.getUserByEmpId("MISMATCH")).thenReturn(user("OTHER", "EMP_OTHER"));
        assertThatThrownBy(() -> service.getPersonalCoreMetricCards("MISMATCH"))
                .isInstanceOf(PerfException.class);

        when(userApi.getUserByEmpId("FUTURE")).thenReturn(user("FUTURE", "EMP_FUTURE"));
        when(empIndexResultMapper.selectLatestRowForEmployee(
                eq("EMP_FUTURE"), any(LocalDate.class)))
                .thenReturn(row(999L, "EMP_FUTURE", LocalDate.now().plusDays(1), "future"));
        assertThatThrownBy(() -> service.getPersonalCoreMetricCards("FUTURE"))
                .isInstanceOf(PerfException.class);
    }

    private static PerfMetricDef def(String code, String name, int slot, String unit) {
        PerfMetricDef def = new PerfMetricDef();
        def.setMetricCode(code);
        def.setMetricName(name);
        def.setBaseDim("EMP");
        def.setValSlot(slot);
        def.setStatus("ACTIVE");
        def.setDeleted(0);
        def.setUnit(unit);
        return def;
    }

    private static UserDTO user(String empId, String username) {
        UserDTO user = new UserDTO();
        user.setEmpId(empId);
        user.setUsername(username);
        return user;
    }

    private static EmpIndexResult row(long id, String empId, LocalDate date, String version) {
        EmpIndexResult row = new EmpIndexResult();
        row.setId(id);
        row.setEmpId(empId);
        row.setDataDate(date);
        row.setVersion(version);
        return row;
    }
}
