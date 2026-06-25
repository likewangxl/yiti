package com.bank.branch.platform.performance.service;

import com.bank.branch.platform.auth.api.OrgApi;
import com.bank.branch.platform.auth.api.UserApi;
import com.bank.branch.platform.auth.api.dto.OrgDTO;
import com.bank.branch.platform.auth.api.dto.UserDTO;
import com.bank.branch.platform.common.web.PageResult;
import com.bank.branch.platform.performance.entity.PerfKpiItem;
import com.bank.branch.platform.performance.entity.PerfTargetPlan;
import com.bank.branch.platform.performance.entity.PerfTargetValue;
import com.bank.branch.platform.performance.enums.PerfErrorCode;
import com.bank.branch.platform.performance.exception.PerfException;
import com.bank.branch.platform.performance.mapper.PerfKpiItemMapper;
import com.bank.branch.platform.performance.mapper.PerfTargetPlanMapper;
import com.bank.branch.platform.performance.mapper.PerfTargetValueMapper;
import com.bank.branch.platform.performance.service.cmd.UpsertTargetValueCmd;
import com.bank.branch.platform.performance.support.TargetTestDataBuilder;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * TargetValueService 单元测试.
 *
 * <p>覆盖 plan Task 3.2 的 TargetValueService DoD 必含场景 + Task 3.1 I-2/I-3 契约落实：
 * <ul>
 *   <li>size > 500 抛 TARGET_BATCH_TOO_BIG (Plan L1372 / §5.3)</li>
 *   <li>全部成功返回 MySQL 受影响行数 (Plan L1373 / §5.3)</li>
 *   <li>单值 upsertOne 委托批量 (Plan L1374)</li>
 *   <li>empty list early return 0 (I-3 落实)</li>
 *   <li>createdBy 被强制覆盖为 operator (I-2 落实)</li>
 *   <li>listByPlan 入口 planId 非空校验 (I-3 落实)</li>
 * </ul>
 */
@ExtendWith(MockitoExtension.class)
class TargetValueServiceTest {

    @Mock
    private PerfTargetValueMapper targetValueMapper;

    /** 2026-06-15：目标值主体存在性校验下沉后端，EMP 查 PT_USER 工号. */
    @Mock
    private UserApi userApi;

    /** 2026-06-15：ORG 维度查 EXT_ORG_INFO（部门编号优先，编码兜底）. */
    @Mock
    private OrgApi orgApi;

    /** 2026-06-15：解析目标方案关联的 KPI 方案（校验指标是否在方案内）. */
    @Mock
    private PerfTargetPlanMapper targetPlanMapper;

    /** 2026-06-15：取 KPI 方案定义的指标项集合. */
    @Mock
    private PerfKpiItemMapper kpiItemMapper;

    @InjectMocks
    private TargetValueService service;

    /**
     * 宽松默认桩：员工/机构默认存在，让既有 upsert 用例（EMP/E001 等）不被新校验拦截；
     * 负向用例各自覆盖具体 subjectId 的桩。
     */
    @BeforeEach
    void permissiveDefaults() {
        lenient().when(userApi.getUsersByUsernames(anyList())).thenReturn(List.of(new UserDTO()));
        // 存在性校验已下沉到轻量 filterExistingUsernames：默认放行（输入即视作存在），负向用例各自覆盖
        lenient().when(userApi.filterExistingUsernames(anyList()))
                .thenAnswer(inv -> new java.util.ArrayList<>(inv.getArgument(0)));
        lenient().when(orgApi.getOrgByDeptNo(anyString())).thenReturn(null);
        lenient().when(orgApi.getOrg(anyString())).thenReturn(new OrgDTO());
        // 默认无同对象同指标的存量目标值（日期重叠校验默认放行）
        lenient().when(targetValueMapper.selectByPlanSubjectMetric(anyString(), anyString(), anyString(), anyString()))
                .thenReturn(java.util.Collections.emptyList());
    }

    // ------------------------------- 对象下拉（方案内去重 + 标签解析，2026-06-15） -------------------------------

    @Test
    @DisplayName("listSubjects: EMP→工号+姓名、ORG→部门编号+机构名称")
    void listSubjects_resolvesEmpAndOrgLabels() {
        PerfTargetValue emp = new PerfTargetValue();
        emp.setSubjectType("EMP");
        emp.setSubjectId("E001");
        PerfTargetValue org = new PerfTargetValue();
        org.setSubjectType("ORG");
        org.setSubjectId("ORG_X");
        when(targetValueMapper.selectDistinctSubjectsByPlan("P1")).thenReturn(List.of(emp, org));
        UserDTO u = new UserDTO();
        u.setUsername("E001");
        u.setDisplayName("张三");
        when(userApi.getUsersByUsernames(List.of("E001"))).thenReturn(List.of(u));
        OrgDTO o = new OrgDTO();
        o.setOrgCode("ORG_X");
        o.setDeptNo("D100");
        o.setOrgName("某支行");
        when(orgApi.getOrg("ORG_X")).thenReturn(o);

        List<com.bank.branch.platform.performance.api.dto.TargetSubjectDTO> subs = service.listSubjects("P1");

        assertThat(subs).hasSize(2);
        var empDto = subs.stream().filter(s -> "EMP".equals(s.getSubjectType())).findFirst().orElseThrow();
        assertThat(empDto.getSubjectId()).isEqualTo("E001");
        assertThat(empDto.getDisplayId()).isEqualTo("E001");
        assertThat(empDto.getName()).isEqualTo("张三");
        assertThat(empDto.getLabel()).contains("E001").contains("张三");
        var orgDto = subs.stream().filter(s -> "ORG".equals(s.getSubjectType())).findFirst().orElseThrow();
        assertThat(orgDto.getSubjectId()).isEqualTo("ORG_X"); // 入库口径保持内部编码
        assertThat(orgDto.getDisplayId()).isEqualTo("D100");
        assertThat(orgDto.getName()).isEqualTo("某支行");
        assertThat(orgDto.getLabel()).contains("D100").contains("某支行");
    }

    @Test
    @DisplayName("listSubjects: ORG subjectId=部门编号 → 按 DEPT_NO 解析机构名称")
    void listSubjects_orgByDeptNo_resolvesLabel() {
        PerfTargetValue org = new PerfTargetValue();
        org.setSubjectType("ORG");
        org.setSubjectId("174100"); // 入库即部门编号
        when(targetValueMapper.selectDistinctSubjectsByPlan("P1")).thenReturn(List.of(org));
        OrgDTO o = new OrgDTO();
        o.setOrgCode("130");
        o.setDeptNo("174100");
        o.setOrgName("榆林分行营业部");
        when(orgApi.getOrgByDeptNo("174100")).thenReturn(o);

        List<com.bank.branch.platform.performance.api.dto.TargetSubjectDTO> subs = service.listSubjects("P1");

        assertThat(subs).hasSize(1);
        assertThat(subs.get(0).getSubjectId()).isEqualTo("174100");
        assertThat(subs.get(0).getDisplayId()).isEqualTo("174100");
        assertThat(subs.get(0).getName()).isEqualTo("榆林分行营业部");
        assertThat(subs.get(0).getLabel()).isEqualTo("174100 榆林分行营业部");
    }

    @Test
    @DisplayName("listSubjects: planId 空 → 抛 VALIDATION_FAILED")
    void listSubjects_blankPlanId_throws() {
        assertThatThrownBy(() -> service.listSubjects(" "))
                .isInstanceOfSatisfying(PerfException.class,
                        ex -> assertThat(ex.getErrorCode()).isEqualTo(PerfErrorCode.VALIDATION_FAILED));
    }

    // ------------------------------- 指标必须属于目标方案关联的 KPI 方案（2026-06-15） -------------------------------

    @Test
    @DisplayName("upsertBatch: 指标不在目标方案关联的 KPI 方案中 → 抛 PERF-42204 且不落库")
    void upsertBatch_metricNotInKpiScheme_throwsAndNoUpsert() {
        PerfTargetPlan plan = new PerfTargetPlan();
        plan.setId("P1");
        plan.setKpiSchemeId("S1");
        when(targetPlanMapper.selectById("P1")).thenReturn(plan);
        PerfKpiItem good = new PerfKpiItem();
        good.setMetricCode("M_GOOD");
        when(kpiItemMapper.selectBySchemeId("S1")).thenReturn(List.of(good));

        List<PerfTargetValue> list = List.of(TargetTestDataBuilder.value(
                "P1", "EMP", "E001", "2026", "M_BAD", new BigDecimal("100")));

        assertThatThrownBy(() -> service.upsertBatch(list, "admin"))
                .isInstanceOfSatisfying(PerfException.class,
                        ex -> assertThat(ex.getErrorCode()).isEqualTo(PerfErrorCode.IMPORT_METRIC_NOT_IN_KPI))
                .hasMessageContaining("M_BAD");
        verify(targetValueMapper, never()).upsertBatch(anyList());
    }

    @Test
    @DisplayName("upsertBatch: 指标在目标方案关联的 KPI 方案中 → 正常落库")
    void upsertBatch_metricInKpiScheme_proceeds() {
        PerfTargetPlan plan = new PerfTargetPlan();
        plan.setId("P1");
        plan.setKpiSchemeId("S1");
        when(targetPlanMapper.selectById("P1")).thenReturn(plan);
        PerfKpiItem good = new PerfKpiItem();
        good.setMetricCode("M_GOOD");
        when(kpiItemMapper.selectBySchemeId("S1")).thenReturn(List.of(good));
        when(targetValueMapper.upsertBatch(anyList())).thenReturn(1);

        List<PerfTargetValue> list = List.of(TargetTestDataBuilder.value(
                "P1", "EMP", "E001", "2026", "M_GOOD", new BigDecimal("100")));

        int affected = service.upsertBatch(list, "admin");
        assertThat(affected).isEqualTo(1);
        verify(targetValueMapper).upsertBatch(anyList());
    }

    // ------------------------------- 主体存在性校验（2026-06-15 后端下沉） -------------------------------

    @Test
    @DisplayName("upsertBatch: EMP 工号在 PT_USER 不存在 → 抛 VALIDATION_FAILED 且不落库")
    void upsertBatch_empNotExist_throwsAndNoUpsert() {
        when(userApi.filterExistingUsernames(List.of("GHOST"))).thenReturn(Collections.emptyList());
        List<PerfTargetValue> list = List.of(TargetTestDataBuilder.value(
                "P1", "EMP", "GHOST", "2026", "M_A", new BigDecimal("100")));

        assertThatThrownBy(() -> service.upsertBatch(list, "admin"))
                .isInstanceOfSatisfying(PerfException.class,
                        ex -> assertThat(ex.getErrorCode()).isEqualTo(PerfErrorCode.VALIDATION_FAILED))
                .hasMessageContaining("GHOST");
        verify(targetValueMapper, never()).upsertBatch(anyList());
    }

    @SuppressWarnings("unchecked")
    @Test
    @DisplayName("upsertBatch: ORG 维度对象=部门编号(DEPT_NO) → 命中后归一为内部机构编码入库")
    void upsertBatch_orgByDeptNo_normalizedToOrgCode() {
        OrgDTO org = new OrgDTO();
        org.setOrgCode("107");
        org.setDeptNo("720100");
        when(orgApi.getOrgByDeptNo("720100")).thenReturn(org);
        when(targetValueMapper.upsertBatch(anyList())).thenReturn(1);
        List<PerfTargetValue> list = List.of(TargetTestDataBuilder.value(
                "P1", "ORG", "720100", "2026", "M_A", new BigDecimal("100")));

        service.upsertBatch(list, "admin");

        ArgumentCaptor<List<PerfTargetValue>> cap =
                (ArgumentCaptor<List<PerfTargetValue>>) (ArgumentCaptor) ArgumentCaptor.forClass(List.class);
        verify(targetValueMapper).upsertBatch(cap.capture());
        assertThat(cap.getValue().get(0).getSubjectId()).isEqualTo("107");
    }

    @SuppressWarnings("unchecked")
    @Test
    @DisplayName("upsertBatch: ORG 维度对象=内部机构编码 → getOrgByDeptNo 落空走 getOrg 兜底，编码保持不变")
    void upsertBatch_orgByCodeFallback_keepsSubjectId() {
        when(orgApi.getOrgByDeptNo("107")).thenReturn(null);
        OrgDTO org = new OrgDTO();
        org.setOrgCode("107");
        when(orgApi.getOrg("107")).thenReturn(org);
        when(targetValueMapper.upsertBatch(anyList())).thenReturn(1);
        List<PerfTargetValue> list = List.of(TargetTestDataBuilder.value(
                "P1", "ORG", "107", "2026", "M_A", new BigDecimal("100")));

        service.upsertBatch(list, "admin");

        ArgumentCaptor<List<PerfTargetValue>> cap =
                (ArgumentCaptor<List<PerfTargetValue>>) (ArgumentCaptor) ArgumentCaptor.forClass(List.class);
        verify(targetValueMapper).upsertBatch(cap.capture());
        assertThat(cap.getValue().get(0).getSubjectId()).isEqualTo("107");
    }

    @Test
    @DisplayName("upsertBatch: ORG 部门编号/编码均不存在 → 抛 VALIDATION_FAILED 且不落库")
    void upsertBatch_orgNotExist_throwsAndNoUpsert() {
        when(orgApi.getOrgByDeptNo("999999")).thenReturn(null);
        when(orgApi.getOrg("999999")).thenReturn(null);
        List<PerfTargetValue> list = List.of(TargetTestDataBuilder.value(
                "P1", "ORG", "999999", "2026", "M_A", new BigDecimal("100")));

        assertThatThrownBy(() -> service.upsertBatch(list, "admin"))
                .isInstanceOfSatisfying(PerfException.class,
                        ex -> assertThat(ex.getErrorCode()).isEqualTo(PerfErrorCode.VALIDATION_FAILED))
                .hasMessageContaining("999999");
        verify(targetValueMapper, never()).upsertBatch(anyList());
    }

    // ------------------------------- upsertBatch: 核心 5 场景 -------------------------------

    @Test
    @DisplayName("upsertBatch: size > 500 抛 TARGET_BATCH_TOO_BIG(PERF-40910), 不落库")
    void upsertBatch_whenSizeExceeds500_throws40910() {
        List<PerfTargetValue> list = buildValues(501);

        assertThatThrownBy(() -> service.upsertBatch(list, "admin"))
                .isInstanceOfSatisfying(PerfException.class,
                        ex -> assertThat(ex.getErrorCode()).isEqualTo(PerfErrorCode.BATCH_QUERY_EXCEEDS_LIMIT));
        verify(targetValueMapper, never()).upsertBatch(anyList());
    }

    @Test
    @DisplayName("upsertBatch: size == 500 为边界合法值, 透传到 Mapper")
    void upsertBatch_whenSizeEquals500_delegatesMapper() {
        List<PerfTargetValue> list = buildValues(500);
        when(targetValueMapper.upsertBatch(anyList())).thenReturn(500);

        int affected = service.upsertBatch(list, "admin");

        assertThat(affected).isEqualTo(500);
        verify(targetValueMapper, times(1)).upsertBatch(anyList());
    }

    @Test
    @DisplayName("upsertBatch: 正常场景返回 Mapper 的 MySQL 受影响行数")
    void upsertBatch_whenAllSuccess_returnsAffectedCount() {
        List<PerfTargetValue> list = buildValues(3);
        // MySQL ON DUPLICATE KEY UPDATE: 新增 1 / 更新 2, 3 条里 2 新 1 更 → 5
        when(targetValueMapper.upsertBatch(anyList())).thenReturn(5);

        int affected = service.upsertBatch(list, "admin");

        assertThat(affected).isEqualTo(5);
    }

    @Test
    @DisplayName("upsertBatch: 空列表 early return 0, 不调 Mapper (落实 I-3)")
    void upsertBatch_whenEmptyList_returnsZero() {
        int affected = service.upsertBatch(Collections.emptyList(), "admin");

        assertThat(affected).isEqualTo(0);
        verify(targetValueMapper, never()).upsertBatch(anyList());
    }

    @Test
    @DisplayName("upsertBatch: null 列表 early return 0, 不调 Mapper")
    void upsertBatch_whenNullList_returnsZero() {
        int affected = service.upsertBatch(null, "admin");

        assertThat(affected).isEqualTo(0);
        verify(targetValueMapper, never()).upsertBatch(anyList());
    }

    @SuppressWarnings("unchecked")
    @Test
    @DisplayName("upsertBatch: 覆写每个 entity.createdBy 为当前操作人, 防止调用方伪造 (落实 I-2)")
    void upsertBatch_overwritesCreatedByToCurrentUser() {
        // 伪造调用方传入 createdBy="hacker" 的场景
        PerfTargetValue v1 = TargetTestDataBuilder.value(
                "P1", "EMP", "E001", "2026", "M_A", new BigDecimal("100"));
        v1.setCreatedBy("hacker");
        PerfTargetValue v2 = TargetTestDataBuilder.value(
                "P1", "EMP", "E002", "2026", "M_A", new BigDecimal("200"));
        v2.setCreatedBy("hacker");
        List<PerfTargetValue> list = List.of(v1, v2);
        when(targetValueMapper.upsertBatch(anyList())).thenReturn(2);

        service.upsertBatch(list, "admin");

        ArgumentCaptor<List<PerfTargetValue>> captor =
                (ArgumentCaptor<List<PerfTargetValue>>) (ArgumentCaptor) ArgumentCaptor.forClass(List.class);
        verify(targetValueMapper).upsertBatch(captor.capture());
        assertThat(captor.getValue())
                .extracting(PerfTargetValue::getCreatedBy)
                .containsOnly("admin");
    }

    // ------------------------------- upsertOne 委托场景 -------------------------------

    @SuppressWarnings("unchecked")
    @Test
    @DisplayName("upsertOne: 单值命令委托到 upsertBatch (List.of(v), operator)")
    void upsertOne_singleCall_delegatesBatch() {
        UpsertTargetValueCmd cmd = UpsertTargetValueCmd.builder()
                .planId("P1")
                .subjectType("EMP")
                .subjectId("E001")
                .cycleKey("2026")
                .metricCode("M_A")
                .targetValue(new BigDecimal("1000"))
                .baseValue(new BigDecimal("100"))
                .stageName("一阶段")
                .startDate(java.time.LocalDate.of(2026, 1, 1))
                .endDate(java.time.LocalDate.of(2026, 6, 30))
                .operator("admin")
                .build();
        when(targetValueMapper.upsertBatch(anyList())).thenReturn(1);

        int affected = service.upsertOne(cmd);

        assertThat(affected).isEqualTo(1);
        ArgumentCaptor<List<PerfTargetValue>> captor =
                (ArgumentCaptor<List<PerfTargetValue>>) (ArgumentCaptor) ArgumentCaptor.forClass(List.class);
        verify(targetValueMapper).upsertBatch(captor.capture());
        assertThat(captor.getValue()).hasSize(1);
        PerfTargetValue actual = captor.getValue().get(0);
        assertThat(actual.getPlanId()).isEqualTo("P1");
        assertThat(actual.getSubjectType()).isEqualTo("EMP");
        assertThat(actual.getSubjectId()).isEqualTo("E001");
        assertThat(actual.getCycleKey()).isEqualTo("2026");
        assertThat(actual.getMetricCode()).isEqualTo("M_A");
        assertThat(actual.getTargetValue()).isEqualByComparingTo("1000");
        assertThat(actual.getBaseValue()).isEqualByComparingTo("100");
        assertThat(actual.getCreatedBy()).isEqualTo("admin");
        assertThat(actual.getId()).isNotBlank();
        // 新增：阶段名称 / 起止日期 透传到实体
        assertThat(actual.getStageName()).isEqualTo("一阶段");
        assertThat(actual.getStartDate()).isEqualTo(java.time.LocalDate.of(2026, 1, 1));
        assertThat(actual.getEndDate()).isEqualTo(java.time.LocalDate.of(2026, 6, 30));
    }

    // ------------------------------- upsertOne 阶段日期区间重叠校验（2026-06-17） -------------------------------

    @Test
    @DisplayName("upsertOne: 同对象同指标日期区间重叠 → 抛 TARGET_VALUE_DATE_OVERLAP 且不落库")
    void upsertOne_whenDateOverlap_throws() {
        PerfTargetValue exist = new PerfTargetValue();
        exist.setId("TV_OLD");
        exist.setCycleKey("2026Q1");
        exist.setStageName("一阶段");
        exist.setStartDate(java.time.LocalDate.of(2026, 1, 1));
        exist.setEndDate(java.time.LocalDate.of(2026, 6, 30));
        when(targetValueMapper.selectByPlanSubjectMetric("P1", "EMP", "E001", "M_A"))
                .thenReturn(List.of(exist));

        UpsertTargetValueCmd cmd = UpsertTargetValueCmd.builder()
                .planId("P1").subjectType("EMP").subjectId("E001").cycleKey("2026Q2").metricCode("M_A")
                .targetValue(new BigDecimal("1000"))
                .startDate(java.time.LocalDate.of(2026, 3, 1)).endDate(java.time.LocalDate.of(2026, 9, 30))
                .operator("admin").build();

        assertThatThrownBy(() -> service.upsertOne(cmd))
                .isInstanceOfSatisfying(PerfException.class,
                        ex -> assertThat(ex.getErrorCode()).isEqualTo(PerfErrorCode.TARGET_VALUE_DATE_OVERLAP));
        verify(targetValueMapper, never()).upsertBatch(anyList());
    }

    @Test
    @DisplayName("upsertOne: 同对象同指标日期不重叠 → 正常落库")
    void upsertOne_whenNoDateOverlap_proceeds() {
        PerfTargetValue exist = new PerfTargetValue();
        exist.setId("TV_OLD");
        exist.setCycleKey("2026Q1");
        exist.setStartDate(java.time.LocalDate.of(2026, 1, 1));
        exist.setEndDate(java.time.LocalDate.of(2026, 3, 31));
        when(targetValueMapper.selectByPlanSubjectMetric("P1", "EMP", "E001", "M_A"))
                .thenReturn(List.of(exist));
        when(targetValueMapper.upsertBatch(anyList())).thenReturn(1);

        UpsertTargetValueCmd cmd = UpsertTargetValueCmd.builder()
                .planId("P1").subjectType("EMP").subjectId("E001").cycleKey("2026Q2").metricCode("M_A")
                .targetValue(new BigDecimal("1000"))
                .startDate(java.time.LocalDate.of(2026, 4, 1)).endDate(java.time.LocalDate.of(2026, 6, 30))
                .operator("admin").build();

        assertThat(service.upsertOne(cmd)).isEqualTo(1);
        verify(targetValueMapper).upsertBatch(anyList());
    }

    @Test
    @DisplayName("upsertOne: 重叠行就是被修改行本身(同 cycleKey) → 不算冲突，正常更新")
    void upsertOne_whenOverlapIsSameRow_proceeds() {
        PerfTargetValue same = new PerfTargetValue();
        same.setId("TV_SAME");
        same.setCycleKey("2026Q2"); // 与 cmd 相同的 cycleKey = 同一行
        same.setStartDate(java.time.LocalDate.of(2026, 1, 1));
        same.setEndDate(java.time.LocalDate.of(2026, 12, 31));
        when(targetValueMapper.selectByPlanSubjectMetric("P1", "EMP", "E001", "M_A"))
                .thenReturn(List.of(same));
        when(targetValueMapper.upsertBatch(anyList())).thenReturn(1);

        UpsertTargetValueCmd cmd = UpsertTargetValueCmd.builder()
                .planId("P1").subjectType("EMP").subjectId("E001").cycleKey("2026Q2").metricCode("M_A")
                .targetValue(new BigDecimal("1000"))
                .startDate(java.time.LocalDate.of(2026, 3, 1)).endDate(java.time.LocalDate.of(2026, 9, 30))
                .operator("admin").build();

        assertThat(service.upsertOne(cmd)).isEqualTo(1);
        verify(targetValueMapper).upsertBatch(anyList());
    }

    // ------------------------------- getByUniqueKey / listByPlan 场景 -------------------------------

    @Test
    @DisplayName("getByUniqueKey: Mapper 返回 null 时包装为 Optional.empty")
    void getByUniqueKey_whenNotFound_returnsEmpty() {
        when(targetValueMapper.selectByUniqueKey("P1", "EMP", "E001", "2026", "M_A"))
                .thenReturn(null);

        Optional<PerfTargetValue> opt = service.getByUniqueKey("P1", "EMP", "E001", "2026", "M_A");

        assertThat(opt).isEmpty();
    }

    @Test
    @DisplayName("getByUniqueKey: Mapper 返回实体时包装为 Optional.of")
    void getByUniqueKey_whenFound_returnsOptional() {
        PerfTargetValue v = TargetTestDataBuilder.value(
                "P1", "EMP", "E001", "2026", "M_A", new BigDecimal("100"));
        when(targetValueMapper.selectByUniqueKey("P1", "EMP", "E001", "2026", "M_A"))
                .thenReturn(v);

        Optional<PerfTargetValue> opt = service.getByUniqueKey("P1", "EMP", "E001", "2026", "M_A");

        assertThat(opt).isPresent();
        assertThat(opt.get().getSubjectId()).isEqualTo("E001");
    }

    @Test
    @DisplayName("listByPlan: planId 空白抛 PARAM_INVALID (落实 I-3)")
    void listByPlan_whenPlanIdBlank_throws() {
        assertThatThrownBy(() -> service.listByPlan("  ", null, null, null, 1, 20))
                .isInstanceOfSatisfying(PerfException.class,
                        ex -> assertThat(ex.getErrorCode()).isEqualTo(PerfErrorCode.VALIDATION_FAILED));
        verify(targetValueMapper, never()).listByPlan(eq("  "), isNull(), isNull(), isNull(),
                org.mockito.ArgumentMatchers.anyInt(), org.mockito.ArgumentMatchers.anyInt());
    }

    @Test
    @DisplayName("listByPlan: planId 为 null 抛 PARAM_INVALID (落实 I-3)")
    void listByPlan_whenPlanIdNull_throws() {
        assertThatThrownBy(() -> service.listByPlan(null, null, null, null, 1, 20))
                .isInstanceOfSatisfying(PerfException.class,
                        ex -> assertThat(ex.getErrorCode()).isEqualTo(PerfErrorCode.VALIDATION_FAILED));
    }

    @Test
    @DisplayName("listByPlan: total=0 返回空分页, 跳过 select")
    void listByPlan_whenNoData_returnsEmptyPage() {
        when(targetValueMapper.countByPlan("P1", null, null, null)).thenReturn(0L);

        PageResult<PerfTargetValue> result = service.listByPlan("P1", null, null, null, 1, 20);

        assertThat(result.getTotal()).isEqualTo(0L);
        assertThat(result.getRecords()).isEmpty();
        verify(targetValueMapper, never()).listByPlan(org.mockito.ArgumentMatchers.anyString(),
                org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.anyInt(),
                org.mockito.ArgumentMatchers.anyInt());
    }

    @Test
    @DisplayName("listByPlan: total>0 返回分页结果 (pageNo/pageSize 透传)")
    void listByPlan_returnsPageResult() {
        when(targetValueMapper.countByPlan("P1", "EMP", null, "2026")).thenReturn(1L);
        PerfTargetValue v = TargetTestDataBuilder.value(
                "P1", "EMP", "E001", "2026", "M_A", new BigDecimal("100"));
        when(targetValueMapper.listByPlan("P1", "EMP", null, "2026", 0, 20))
                .thenReturn(List.of(v));

        PageResult<PerfTargetValue> result = service.listByPlan("P1", "EMP", null, "2026", 1, 20);

        assertThat(result.getTotal()).isEqualTo(1L);
        assertThat(result.getRecords()).hasSize(1);
    }

    // ------------------------------- deleteById: 物理删除 -------------------------------

    @Test
    @DisplayName("deleteById: 物理删除委托 Mapper.deleteById 并返回受影响行数")
    void deleteById_delegatesMapperAndReturnsAffected() {
        when(targetValueMapper.deleteById("TV_1")).thenReturn(1);

        int affected = service.deleteById("TV_1");

        assertThat(affected).isEqualTo(1);
        verify(targetValueMapper).deleteById("TV_1");
    }

    @Test
    @DisplayName("deleteById: id 不存在时返回 0（幂等，不抛异常）")
    void deleteById_notFound_returnsZero() {
        when(targetValueMapper.deleteById("TV_X")).thenReturn(0);

        assertThat(service.deleteById("TV_X")).isZero();
    }

    // ------------------------------- helpers -------------------------------

    private static List<PerfTargetValue> buildValues(int n) {
        List<PerfTargetValue> list = new ArrayList<>(n);
        for (int i = 0; i < n; i++) {
            list.add(TargetTestDataBuilder.value(
                    "P1", "EMP", "E" + i, "2026", "M_A", new BigDecimal(100 + i)));
        }
        return list;
    }
}
