package com.bank.branch.platform.performance.service;

import com.bank.branch.platform.auth.api.BizScopeApi;
import com.bank.branch.platform.auth.api.CurrentUserApi;
import com.bank.branch.platform.common.security.enums.BizType;
import com.bank.branch.platform.common.security.enums.DataScopeType;
import com.bank.branch.platform.performance.api.dto.AllocSummaryDTO;
import com.bank.branch.platform.performance.api.dto.AllocVersionDTO;
import com.bank.branch.platform.performance.entity.CustAllocRelation;
import com.bank.branch.platform.performance.entity.SysControl;
import com.bank.branch.platform.performance.mapper.CustAllocRelationMapper;
import com.bank.branch.platform.performance.support.AllocTestDataBuilder;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * AllocRelationService 单元测试.
 *
 * <p>Task 5.2 UT 覆盖面（对应 plan §Task 5.2 DoD 与补充场景）:
 * <ul>
 *   <li>batchGetCurrentAllocations 的缓存合并核心逻辑（部分命中 + 缺失回查 DB + 空列表回填防穿透）</li>
 *   <li>hasAllocation 存在/不存在两种分支</li>
 *   <li>countCustomersByEmps 批量聚合</li>
 *   <li>getLatestAllocVersion 派生自 SysControlService.getCurrentVersion("CUST")</li>
 *   <li>getAllocVersionAt 无精确匹配时退回到 {@code <= asOfDate} 的最近版本</li>
 *   <li>listCustomersByEmp 数据范围过滤 (ALL / SELF_CREATED)</li>
 *   <li>getCurrentAllocations / getAllocationHistory / batchSummaryByEmps / countCustomersOfEmp 透传</li>
 * </ul>
 *
 * <p>本测试为纯 Mock UT，不触发 Spring 容器/DB/Redis.
 * 缓存 key 约定: {@code perf:alloc:cust:{custId}:{bizKind}} （bizKind null 时占位 "ALL"）.
 */
@ExtendWith(MockitoExtension.class)
class AllocRelationServiceTest {

    @Mock
    private CustAllocRelationMapper allocMapper;

    @Mock
    private SysControlService sysControlService;

    @Mock
    private CurrentUserApi currentUserApi;

    @Mock
    private BizScopeApi bizScopeApi;

    @Mock
    private RedisTemplate<String, Object> redisTemplate;

    @Mock
    private ValueOperations<String, Object> valueOperations;

    @InjectMocks
    private AllocRelationService service;

    // ------------------------------- getCurrentAllocations -------------------------------

    @Test
    @DisplayName("getCurrentAllocations: 以 today 为基准透传 Mapper.selectCurrentByCustAndBiz")
    void getCurrentAllocations_delegatesMapperWithToday() {
        CustAllocRelation rel = AllocTestDataBuilder.relation("C_001", "EMP_A", "LOAN");
        when(allocMapper.selectCurrentByCustAndBiz(eq("TEST_AR_C_001"), eq("LOAN"), any(LocalDate.class)))
                .thenReturn(List.of(rel));

        List<CustAllocRelation> result = service.getCurrentAllocations("TEST_AR_C_001", "LOAN");

        assertThat(result).hasSize(1);
        assertThat(result.get(0).getEmpId()).isEqualTo("EMP_A");
        ArgumentCaptor<LocalDate> dateCaptor = ArgumentCaptor.forClass(LocalDate.class);
        verify(allocMapper).selectCurrentByCustAndBiz(eq("TEST_AR_C_001"), eq("LOAN"), dateCaptor.capture());
        assertThat(dateCaptor.getValue()).isEqualTo(LocalDate.now());
    }

    // ------------------------------- getAllocationHistory -------------------------------

    @Test
    @DisplayName("getAllocationHistory: asOfDate 传 Mapper.selectHistoryByCustAsOf")
    void getAllocationHistory_delegatesMapper() {
        LocalDate asOf = LocalDate.of(2025, 6, 1);
        CustAllocRelation rel = AllocTestDataBuilder.relation(
                "C_002", "EMP_B", "DEPOSIT", asOf.minusMonths(1), null);
        when(allocMapper.selectHistoryByCustAsOf("TEST_AR_C_002", asOf))
                .thenReturn(List.of(rel));

        List<CustAllocRelation> result = service.getAllocationHistory("TEST_AR_C_002", asOf);

        assertThat(result).hasSize(1);
        verify(allocMapper).selectHistoryByCustAsOf("TEST_AR_C_002", asOf);
    }

    // ------------------------------- listCustomersByEmp (数据范围) -------------------------------

    @Test
    @DisplayName("listCustomersByEmp: 管理员 (scope=ALL) filter=null")
    void listCustomersByEmp_whenAdmin_filterNull() {
        when(currentUserApi.getCurrentEmpId()).thenReturn("admin");
        when(bizScopeApi.resolveScope("admin", BizType.PERF_CONFIG)).thenReturn(DataScopeType.ALL);
        CustAllocRelation rel = AllocTestDataBuilder.relation("C_10", "EMP_X", "LOAN");
        when(allocMapper.selectByEmpAndBiz(eq("EMP_X"), eq("LOAN"), any(LocalDate.class), isNull()))
                .thenReturn(List.of(rel));

        List<CustAllocRelation> result = service.listCustomersByEmp("EMP_X", "LOAN");

        assertThat(result).hasSize(1);
        verify(allocMapper).selectByEmpAndBiz(eq("EMP_X"), eq("LOAN"), any(LocalDate.class), isNull());
    }

    @Test
    @DisplayName("listCustomersByEmp: 普通用户 (scope=SELF_CREATED) filter=\"AND emp_id = '<empId>'\"")
    void listCustomersByEmp_appliesDataScopeFilter() {
        when(currentUserApi.getCurrentEmpId()).thenReturn("USER_A");
        when(bizScopeApi.resolveScope("USER_A", BizType.PERF_CONFIG))
                .thenReturn(DataScopeType.SELF_CREATED);
        String expectedFilter = "AND emp_id = 'USER_A'";
        when(allocMapper.selectByEmpAndBiz(eq("USER_A"), isNull(), any(LocalDate.class), eq(expectedFilter)))
                .thenReturn(Collections.emptyList());

        List<CustAllocRelation> result = service.listCustomersByEmp("USER_A", null);

        assertThat(result).isEmpty();
        ArgumentCaptor<String> filterCaptor = ArgumentCaptor.forClass(String.class);
        verify(allocMapper).selectByEmpAndBiz(eq("USER_A"), isNull(), any(LocalDate.class), filterCaptor.capture());
        assertThat(filterCaptor.getValue()).isEqualTo(expectedFilter);
    }

    // ------------------------------- batchGetCurrentAllocations (核心缓存合并) -------------------------------

    @Test
    @DisplayName("batchGetCurrentAllocations: 2 缓存命中 + 1 未命中走 DB, 结果合并 + 缺失者回写空列表防穿透")
    @SuppressWarnings("unchecked")
    void batchGetCurrentAllocations_hitsCacheForSomeMissesDb_fillsBoth() {
        // Given: 3 客户 C1/C2/C3, C1/C2 命中缓存, C3 未命中 → 走 DB
        Set<String> custIds = new HashSet<>(List.of("C1", "C2", "C3"));
        String bizKind = "LOAN";

        CustAllocRelation c1Rel = AllocTestDataBuilder.relation("dummy1", "EMP_A", bizKind);
        c1Rel.setCustId("C1");
        CustAllocRelation c2Rel = AllocTestDataBuilder.relation("dummy2", "EMP_B", bizKind);
        c2Rel.setCustId("C2");
        CustAllocRelation c3Rel = AllocTestDataBuilder.relation("dummy3", "EMP_C", bizKind);
        c3Rel.setCustId("C3");

        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.get("perf:alloc:cust:C1:LOAN")).thenReturn(List.of(c1Rel));
        when(valueOperations.get("perf:alloc:cust:C2:LOAN")).thenReturn(List.of(c2Rel));
        when(valueOperations.get("perf:alloc:cust:C3:LOAN")).thenReturn(null);

        // C3 走 DB: selectCurrentByCustIds 仅传 {C3}
        when(allocMapper.selectCurrentByCustIds(any(Set.class), eq(bizKind), any(LocalDate.class), isNull()))
                .thenReturn(List.of(c3Rel));

        // When
        Map<String, List<CustAllocRelation>> result = service.batchGetCurrentAllocations(custIds, bizKind);

        // Then: 3 客户全部有结果
        assertThat(result).hasSize(3);
        assertThat(result.get("C1")).extracting(CustAllocRelation::getEmpId).containsExactly("EMP_A");
        assertThat(result.get("C2")).extracting(CustAllocRelation::getEmpId).containsExactly("EMP_B");
        assertThat(result.get("C3")).extracting(CustAllocRelation::getEmpId).containsExactly("EMP_C");

        // 验证 DB 只被调用一次，且查询集合恰好只包含 C3
        ArgumentCaptor<Set<String>> missingCaptor = ArgumentCaptor.forClass(Set.class);
        verify(allocMapper).selectCurrentByCustIds(
                missingCaptor.capture(), eq(bizKind), any(LocalDate.class), isNull());
        assertThat(missingCaptor.getValue()).containsExactly("C3");

        // 验证 C3 的结果被回写到缓存 (TTL 5 分钟)
        ArgumentCaptor<String> keyCaptor = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<Duration> ttlCaptor = ArgumentCaptor.forClass(Duration.class);
        verify(valueOperations).set(keyCaptor.capture(), any(), ttlCaptor.capture());
        assertThat(keyCaptor.getValue()).isEqualTo("perf:alloc:cust:C3:LOAN");
        assertThat(ttlCaptor.getValue()).isEqualTo(Duration.ofMinutes(5));
    }

    @Test
    @DisplayName("batchGetCurrentAllocations: 空列表也回写缓存防穿透")
    @SuppressWarnings("unchecked")
    void batchGetCurrentAllocations_emptyResultStillCached() {
        Set<String> custIds = new HashSet<>(List.of("CMISS"));
        String bizKind = "LOAN";

        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.get("perf:alloc:cust:CMISS:LOAN")).thenReturn(null);
        when(allocMapper.selectCurrentByCustIds(any(Set.class), eq(bizKind), any(LocalDate.class), isNull()))
                .thenReturn(Collections.emptyList());

        Map<String, List<CustAllocRelation>> result = service.batchGetCurrentAllocations(custIds, bizKind);

        assertThat(result).containsKey("CMISS");
        assertThat(result.get("CMISS")).isEmpty();
        verify(valueOperations).set(
                eq("perf:alloc:cust:CMISS:LOAN"),
                eq(Collections.emptyList()),
                eq(Duration.ofMinutes(5)));
    }

    @Test
    @DisplayName("batchGetCurrentAllocations: bizKind=null 时 key 以 ALL 占位")
    @SuppressWarnings("unchecked")
    void batchGetCurrentAllocations_whenBizKindNull_usesAllPlaceholder() {
        Set<String> custIds = new HashSet<>(List.of("CNULL"));
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.get("perf:alloc:cust:CNULL:ALL")).thenReturn(null);
        when(allocMapper.selectCurrentByCustIds(any(Set.class), isNull(), any(LocalDate.class), isNull()))
                .thenReturn(Collections.emptyList());

        Map<String, List<CustAllocRelation>> result = service.batchGetCurrentAllocations(custIds, null);

        assertThat(result).containsOnlyKeys("CNULL");
        verify(valueOperations).get("perf:alloc:cust:CNULL:ALL");
        verify(valueOperations).set(
                eq("perf:alloc:cust:CNULL:ALL"), any(), eq(Duration.ofMinutes(5)));
    }

    @Test
    @DisplayName("batchGetCurrentAllocations: 入参 custIds 为空 → 直接返回空 Map, 不查 Redis/DB")
    void batchGetCurrentAllocations_whenEmptyCustIds_returnsEmptyMap() {
        Map<String, List<CustAllocRelation>> result = service.batchGetCurrentAllocations(
                Collections.emptySet(), "LOAN");

        assertThat(result).isEmpty();
        verifyNoInteractions(redisTemplate);
        verifyNoInteractions(allocMapper);
    }

    @Test
    @DisplayName("batchGetCurrentAllocations: 3 客户全部缓存命中 → 不走 DB")
    @SuppressWarnings("unchecked")
    void batchGetCurrentAllocations_allCacheHits_skipsDb() {
        Set<String> custIds = new HashSet<>(List.of("H1", "H2"));
        CustAllocRelation r1 = AllocTestDataBuilder.relation("x1", "EMP_1", "LOAN");
        r1.setCustId("H1");
        CustAllocRelation r2 = AllocTestDataBuilder.relation("x2", "EMP_2", "LOAN");
        r2.setCustId("H2");

        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.get("perf:alloc:cust:H1:LOAN")).thenReturn(List.of(r1));
        when(valueOperations.get("perf:alloc:cust:H2:LOAN")).thenReturn(List.of(r2));

        Map<String, List<CustAllocRelation>> result = service.batchGetCurrentAllocations(custIds, "LOAN");

        assertThat(result).hasSize(2);
        // DB mapper 无任何调用
        verify(allocMapper, never()).selectCurrentByCustIds(any(), anyString(), any(), any());
    }

    // ------------------------------- countCustomersByEmps -------------------------------

    @Test
    @DisplayName("countCustomersByEmps: Mapper 聚合结果转 Map<empId,Long>")
    void countCustomersByEmps_batchOk() {
        Set<String> empIds = new HashSet<>(List.of("E1", "E2"));
        Map<String, Object> row1 = new HashMap<>();
        row1.put("emp_id", "E1");
        row1.put("cust_count", 5L);
        Map<String, Object> row2 = new HashMap<>();
        row2.put("emp_id", "E2");
        row2.put("cust_count", 3L);
        when(allocMapper.countCustomersByEmps(eq(empIds), isNull(), any(LocalDate.class)))
                .thenReturn(List.of(row1, row2));

        Map<String, Long> result = service.countCustomersByEmps(empIds, null);

        assertThat(result).containsEntry("E1", 5L).containsEntry("E2", 3L);
    }

    @Test
    @DisplayName("countCustomersByEmps: 入参空集合 → 返回空 Map, 不调 Mapper")
    void countCustomersByEmps_whenEmptyEmpIds_returnsEmpty() {
        Map<String, Long> result = service.countCustomersByEmps(Collections.emptySet(), "LOAN");

        assertThat(result).isEmpty();
        verify(allocMapper, never()).countCustomersByEmps(any(), any(), any());
    }

    // ------------------------------- batchSummaryByEmps -------------------------------

    @Test
    @DisplayName("batchSummaryByEmps: 按员工分组构造 AllocSummaryDTO, asOfDate=null 时用 today")
    void batchSummaryByEmps_groupsAndDefaultsAsOfDateToToday() {
        Set<String> empIds = new HashSet<>(List.of("ES1"));
        CustAllocRelation r1 = AllocTestDataBuilder.relation("S1", "ES1", "LOAN",
                LocalDate.now().minusDays(10), null, new BigDecimal("60.00"));
        r1.setCustId("CX1");
        CustAllocRelation r2 = AllocTestDataBuilder.relation("S2", "ES1", "LOAN",
                LocalDate.now().minusDays(5), null, new BigDecimal("40.00"));
        r2.setCustId("CX2");

        // Service 用 selectByEmpAndBiz 逐员工查询 (无 dataScopeFilter 需求, 因为 batchSummary 是聚合)
        when(allocMapper.selectByEmpAndBiz(eq("ES1"), eq("LOAN"), any(LocalDate.class), isNull()))
                .thenReturn(List.of(r1, r2));

        List<AllocSummaryDTO> result = service.batchSummaryByEmps(empIds, "LOAN", null);

        assertThat(result).hasSize(1);
        AllocSummaryDTO dto = result.get(0);
        assertThat(dto.getEmpId()).isEqualTo("ES1");
        assertThat(dto.getBizKind()).isEqualTo("LOAN");
        assertThat(dto.getCustCount()).isEqualTo(2L);
        // 平均比例 = (60+40)/2 = 50
        assertThat(dto.getAvgAllocRatio()).isEqualByComparingTo(new BigDecimal("50.00"));
    }

    @Test
    @DisplayName("batchSummaryByEmps: 入参空集合 → 返回空列表")
    void batchSummaryByEmps_whenEmptyEmpIds_returnsEmpty() {
        List<AllocSummaryDTO> result = service.batchSummaryByEmps(
                Collections.emptySet(), "LOAN", LocalDate.now());

        assertThat(result).isEmpty();
    }

    // ------------------------------- hasAllocation -------------------------------

    @Test
    @DisplayName("hasAllocation: Mapper 命中 (list 非空) → true")
    void hasAllocation_whenExists_returnsTrue() {
        CustAllocRelation rel = AllocTestDataBuilder.relation("HA", "EMP_HA", "LOAN");
        when(allocMapper.selectByEmpAndBiz(eq("EMP_HA"), eq("LOAN"), any(LocalDate.class), isNull()))
                .thenReturn(List.of(rel));

        boolean exists = service.hasAllocation("EMP_HA", "TEST_AR_HA", "LOAN");

        assertThat(exists).isTrue();
    }

    @Test
    @DisplayName("hasAllocation: Mapper 空列表 → false")
    void hasAllocation_whenNotExists_returnsFalse() {
        when(allocMapper.selectByEmpAndBiz(eq("EMP_NONE"), eq("LOAN"), any(LocalDate.class), isNull()))
                .thenReturn(Collections.emptyList());

        boolean exists = service.hasAllocation("EMP_NONE", "TEST_AR_X", "LOAN");

        assertThat(exists).isFalse();
    }

    @Test
    @DisplayName("hasAllocation: 只返回与 custId 匹配的记录才计 true")
    void hasAllocation_onlyMatchingCustIdCounts() {
        CustAllocRelation other = AllocTestDataBuilder.relation("OTHER", "EMP_A", "LOAN");
        // Mapper 返回 EMP_A 名下所有客户, 其中不含目标 custId
        when(allocMapper.selectByEmpAndBiz(eq("EMP_A"), eq("LOAN"), any(LocalDate.class), isNull()))
                .thenReturn(List.of(other));

        boolean exists = service.hasAllocation("EMP_A", "TEST_AR_MISS", "LOAN");

        assertThat(exists).isFalse();
    }

    // ------------------------------- countCustomersOfEmp -------------------------------

    @Test
    @DisplayName("countCustomersOfEmp: 透传 Mapper.countDistinctCustomers")
    void countCustomersOfEmp_delegatesMapper() {
        when(allocMapper.countDistinctCustomers(eq("EMP_C"), eq("LOAN"), any(LocalDate.class)))
                .thenReturn(12L);

        long count = service.countCustomersOfEmp("EMP_C", "LOAN");

        assertThat(count).isEqualTo(12L);
    }

    // ------------------------------- getLatestAllocVersion -------------------------------

    @Test
    @DisplayName("getLatestAllocVersion: 派生自 SysControlService.getCurrentVersion(\"CUST\")")
    void getLatestAllocVersion_derivesFromSysControlCust() {
        SysControl sc = new SysControl();
        sc.setId("SCID");
        sc.setScopeDim("CUST");
        sc.setLatestDataDate(LocalDate.of(2026, 3, 15));
        sc.setCurrentVersion("v7");
        sc.setIsValid(1);
        sc.setCreatedTime(LocalDateTime.of(2026, 3, 15, 10, 0));
        sc.setUpdatedTime(LocalDateTime.of(2026, 3, 15, 10, 0));
        when(sysControlService.getCurrentVersion("CUST")).thenReturn(sc);

        AllocVersionDTO dto = service.getLatestAllocVersion("LOAN");

        assertThat(dto).isNotNull();
        assertThat(dto.getScopeDim()).isEqualTo("CUST");
        assertThat(dto.getCurrentVersion()).isEqualTo("v7");
        assertThat(dto.getLatestDataDate()).isEqualTo(LocalDate.of(2026, 3, 15));
        assertThat(dto.getBizKind()).isEqualTo("LOAN");
        assertThat(dto.getPublishedAt()).isEqualTo(LocalDateTime.of(2026, 3, 15, 10, 0));
    }

    // ------------------------------- getAllocVersionAt -------------------------------

    @Test
    @DisplayName("getAllocVersionAt: 无精确匹配时取 latestDataDate <= asOfDate 的最近版本")
    void getAllocVersionAt_whenNoExactMatch_returnsMostRecentBeforeAsOfDate() {
        SysControl v1 = new SysControl();
        v1.setId("A1");
        v1.setScopeDim("CUST");
        v1.setLatestDataDate(LocalDate.of(2026, 1, 1));
        v1.setCurrentVersion("v1");
        SysControl v2 = new SysControl();
        v2.setId("A2");
        v2.setScopeDim("CUST");
        v2.setLatestDataDate(LocalDate.of(2026, 2, 1));
        v2.setCurrentVersion("v2");
        SysControl v3 = new SysControl();
        v3.setId("A3");
        v3.setScopeDim("CUST");
        v3.setLatestDataDate(LocalDate.of(2026, 3, 1));
        v3.setCurrentVersion("v3");
        // 历史倒序 (Service 约定 listVersionHistory 按 latest_data_date 倒序)
        when(sysControlService.listVersionHistory(eq("CUST"), eq(100)))
                .thenReturn(List.of(v3, v2, v1));

        AllocVersionDTO dto = service.getAllocVersionAt("LOAN", LocalDate.of(2026, 2, 15));

        // 2 月 15 日应落在 v2 (2/1) 生效区间, 小于 v3 (3/1)
        assertThat(dto).isNotNull();
        assertThat(dto.getCurrentVersion()).isEqualTo("v2");
        assertThat(dto.getLatestDataDate()).isEqualTo(LocalDate.of(2026, 2, 1));
    }

    @Test
    @DisplayName("getAllocVersionAt: asOfDate 早于所有历史版本 → 返回 null")
    void getAllocVersionAt_whenBeforeAllVersions_returnsNull() {
        SysControl v1 = new SysControl();
        v1.setId("A1");
        v1.setScopeDim("CUST");
        v1.setLatestDataDate(LocalDate.of(2026, 1, 1));
        v1.setCurrentVersion("v1");
        when(sysControlService.listVersionHistory(eq("CUST"), eq(100)))
                .thenReturn(List.of(v1));

        AllocVersionDTO dto = service.getAllocVersionAt("LOAN", LocalDate.of(2025, 12, 1));

        assertThat(dto).isNull();
    }

    @Test
    @DisplayName("getAllocVersionAt: 精确匹配某一版本的 latestDataDate")
    void getAllocVersionAt_whenExactMatch_returnsThatVersion() {
        SysControl v2 = new SysControl();
        v2.setId("A2");
        v2.setScopeDim("CUST");
        v2.setLatestDataDate(LocalDate.of(2026, 2, 1));
        v2.setCurrentVersion("v2");
        when(sysControlService.listVersionHistory(eq("CUST"), eq(100)))
                .thenReturn(List.of(v2));

        AllocVersionDTO dto = service.getAllocVersionAt(null, LocalDate.of(2026, 2, 1));

        assertThat(dto).isNotNull();
        assertThat(dto.getCurrentVersion()).isEqualTo("v2");
    }
}
