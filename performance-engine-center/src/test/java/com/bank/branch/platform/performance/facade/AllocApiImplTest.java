package com.bank.branch.platform.performance.facade;

import com.bank.branch.platform.performance.api.dto.AllocSummaryDTO;
import com.bank.branch.platform.performance.api.dto.AllocVersionDTO;
import com.bank.branch.platform.performance.api.dto.CustAllocRelationDTO;
import com.bank.branch.platform.performance.entity.CustAllocRelation;
import com.bank.branch.platform.performance.service.AllocRelationService;
import com.bank.branch.platform.performance.support.AllocTestDataBuilder;
import com.bank.branch.platform.performance.support.PerformanceServiceTestBase;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.when;

/**
 * AllocApiImpl 单元测试.
 *
 * <p>覆盖 Plan Task 5.3 (plan L1554-1568) DoD:
 * <ul>
 *   <li>10 个 V1.0 实现方法 (AllocApi 全部方法 V1.0 实现, 无 UOE)</li>
 *   <li>核心场景 (Plan L1559-1562):
 *     <ul>
 *       <li>getCurrentAllocations 委托 Service + Assembler 映射</li>
 *       <li>batchGetCurrentAllocations 正确将 Service Map&lt;custId, List&lt;Entity&gt;&gt;
 *           组装为 Map&lt;custId, List&lt;DTO&gt;&gt; (缓存合并本身在 Service Test 覆盖)</li>
 *       <li>getAllocationHistory / hasAllocation / countCustomersByEmps /
 *           batchSummaryByEmps / getLatestAllocVersion / getAllocVersionAt</li>
 *     </ul>
 *   </li>
 * </ul>
 *
 * <p>缓存策略决策: **Facade 层不加 {@code @Cacheable}** (Plan L1564 钦定 V1.0 不做 evict),
 * 故本测试类为纯 Mock 单元测试, 不依赖 Spring AOP; 所有断言聚焦于:
 * <ul>
 *   <li>Service 交互正确 (Mock 返回 Entity)</li>
 *   <li>Assembler 装配完整 (Entity → DTO 字段逐一映射)</li>
 *   <li>空/不存在场景的返回契约 (空列表 / 空 Map / null / false / 0)</li>
 * </ul>
 *
 * <p>测试数据前缀 {@code TEST_AR_} (由 {@link AllocTestDataBuilder} 统一约定)。
 *
 * <p>{@code countCustomersByEmps} 接口签名为 {@code countCustomersByEmps(Set&lt;String&gt; empIds)},
 * 无 bizKind 入参; Facade 实现统一传入 {@code null} 给 Service (任一业务种类匹配), 详见 Step 4 实现。
 */
class AllocApiImplTest extends PerformanceServiceTestBase {

    @Mock
    private AllocRelationService allocRelationService;

    @Mock
    private com.bank.branch.platform.performance.service.adjust.AllocAdjustPreviewService allocAdjustPreviewService;

    @InjectMocks
    private AllocApiImpl allocApi;

    // ------------------------- 基础查询: getCurrentAllocations -------------------------

    @Test
    @DisplayName("getCurrentAllocations: 委托 Service 并装配 DTO 列表")
    void getCurrentAllocations_delegatesService() {
        CustAllocRelation r1 = AllocTestDataBuilder.relation("CUR_1", "E001", "DEPOSIT");
        CustAllocRelation r2 = AllocTestDataBuilder.relation("CUR_1", "E002", "DEPOSIT");
        when(allocRelationService.getCurrentAllocations("TEST_AR_CUR_1", "DEPOSIT"))
                .thenReturn(List.of(r1, r2));

        List<CustAllocRelationDTO> result = allocApi.getCurrentAllocations("TEST_AR_CUR_1", "DEPOSIT");

        assertThat(result).hasSize(2);
        assertThat(result).extracting(CustAllocRelationDTO::getId)
                .containsExactly(r1.getId(), r2.getId());
        assertThat(result).extracting(CustAllocRelationDTO::getCustId)
                .containsExactly("TEST_AR_CUR_1", "TEST_AR_CUR_1");
        assertThat(result).extracting(CustAllocRelationDTO::getEmpId)
                .containsExactly("E001", "E002");
        assertThat(result).extracting(CustAllocRelationDTO::getBizKind)
                .containsExactly("DEPOSIT", "DEPOSIT");
    }

    @Test
    @DisplayName("getCurrentAllocations: Service 返回空列表时返回空列表")
    void getCurrentAllocations_whenEmpty_returnsEmpty() {
        when(allocRelationService.getCurrentAllocations("TEST_AR_NO_SUCH", null))
                .thenReturn(Collections.emptyList());

        List<CustAllocRelationDTO> result = allocApi.getCurrentAllocations("TEST_AR_NO_SUCH", null);

        assertThat(result).isEmpty();
    }

    @Test
    @DisplayName("getCurrentAllocations: Assembler 完整映射 Entity 全部对外字段到 DTO")
    void getCurrentAllocations_assemblesAllFields() {
        CustAllocRelation entity = AllocTestDataBuilder.relation(
                "FULL", "E010", "LOAN",
                LocalDate.of(2026, 1, 1), LocalDate.of(2026, 12, 31),
                new BigDecimal("50.00"));
        entity.setAllocDim("ACCOUNT");
        entity.setAccountNo("ACC_001");
        when(allocRelationService.getCurrentAllocations("TEST_AR_FULL", "LOAN"))
                .thenReturn(List.of(entity));

        List<CustAllocRelationDTO> result = allocApi.getCurrentAllocations("TEST_AR_FULL", "LOAN");

        assertThat(result).hasSize(1);
        CustAllocRelationDTO dto = result.get(0);
        assertThat(dto.getId()).isEqualTo(entity.getId());
        assertThat(dto.getCustId()).isEqualTo("TEST_AR_FULL");
        // 对外可选字段 custName / empName 来源 Mapper 暂不 join, 统一映射为 null
        assertThat(dto.getCustName()).isNull();
        assertThat(dto.getAllocDim()).isEqualTo("ACCOUNT");
        assertThat(dto.getBizKind()).isEqualTo("LOAN");
        assertThat(dto.getAccountNo()).isEqualTo("ACC_001");
        assertThat(dto.getEmpId()).isEqualTo("E010");
        assertThat(dto.getEmpName()).isNull();
        assertThat(dto.getRatio()).isEqualByComparingTo("50.00");
        assertThat(dto.getEffectiveDate()).isEqualTo(LocalDate.of(2026, 1, 1));
        assertThat(dto.getEndDate()).isEqualTo(LocalDate.of(2026, 12, 31));
    }

    // ------------------------- 基础查询: getAllocationHistory -------------------------

    @Test
    @DisplayName("getAllocationHistory: 委托 Service 并装配 DTO 列表")
    void getAllocationHistory_returnsList() {
        CustAllocRelation r = AllocTestDataBuilder.relation(
                "HIST", "E001", "DEPOSIT",
                LocalDate.of(2025, 6, 1), LocalDate.of(2025, 12, 31));
        when(allocRelationService.getAllocationHistory(
                eq("TEST_AR_HIST"), eq(LocalDate.of(2025, 10, 1))))
                .thenReturn(List.of(r));

        List<CustAllocRelationDTO> result = allocApi.getAllocationHistory(
                "TEST_AR_HIST", LocalDate.of(2025, 10, 1));

        assertThat(result).hasSize(1);
        assertThat(result.get(0).getId()).isEqualTo(r.getId());
        assertThat(result.get(0).getCustId()).isEqualTo("TEST_AR_HIST");
        assertThat(result.get(0).getEffectiveDate()).isEqualTo(LocalDate.of(2025, 6, 1));
        assertThat(result.get(0).getEndDate()).isEqualTo(LocalDate.of(2025, 12, 31));
    }

    // ------------------------- 基础查询: listCustomersByEmp -------------------------

    @Test
    @DisplayName("listCustomersByEmp: 委托 Service 并装配 DTO 列表")
    void listCustomersByEmp_returnsList() {
        CustAllocRelation r1 = AllocTestDataBuilder.relation("EMP_1", "E001", "DEPOSIT");
        CustAllocRelation r2 = AllocTestDataBuilder.relation("EMP_2", "E001", "DEPOSIT");
        when(allocRelationService.listCustomersByEmp("E001", "DEPOSIT"))
                .thenReturn(List.of(r1, r2));

        List<CustAllocRelationDTO> result = allocApi.listCustomersByEmp("E001", "DEPOSIT");

        assertThat(result).hasSize(2);
        assertThat(result).extracting(CustAllocRelationDTO::getCustId)
                .containsExactly("TEST_AR_EMP_1", "TEST_AR_EMP_2");
    }

    // ------------------------- 批量查询: batchGetCurrentAllocations -------------------------

    @Test
    @DisplayName("batchGetCurrentAllocations: 正确将 Service Map 装配为 Map<custId, List<DTO>>")
    void batchGetCurrentAllocations_cacheMergesBatchQuery() {
        CustAllocRelation r1 = AllocTestDataBuilder.relation("B1", "E001", "DEPOSIT");
        CustAllocRelation r2 = AllocTestDataBuilder.relation("B2", "E002", "DEPOSIT");
        // Service 返回 Map<custId, List<Entity>>, Facade 负责映射 DTO
        // 业务语义: 缓存合并由 Service 层保证 (见 AllocRelationServiceTest),
        // Facade 只验证 "Service 返回 Map 被正确 DTO 化"
        when(allocRelationService.batchGetCurrentAllocations(
                eq(Set.of("TEST_AR_B1", "TEST_AR_B2", "TEST_AR_B3")), eq("DEPOSIT")))
                .thenReturn(Map.of(
                        "TEST_AR_B1", List.of(r1),
                        "TEST_AR_B2", List.of(r2),
                        "TEST_AR_B3", Collections.emptyList()));

        Map<String, List<CustAllocRelationDTO>> result = allocApi.batchGetCurrentAllocations(
                Set.of("TEST_AR_B1", "TEST_AR_B2", "TEST_AR_B3"), "DEPOSIT");

        assertThat(result).hasSize(3);
        assertThat(result.get("TEST_AR_B1")).hasSize(1);
        assertThat(result.get("TEST_AR_B1").get(0).getCustId()).isEqualTo("TEST_AR_B1");
        assertThat(result.get("TEST_AR_B1").get(0).getEmpId()).isEqualTo("E001");
        assertThat(result.get("TEST_AR_B2")).hasSize(1);
        assertThat(result.get("TEST_AR_B2").get(0).getCustId()).isEqualTo("TEST_AR_B2");
        assertThat(result.get("TEST_AR_B2").get(0).getEmpId()).isEqualTo("E002");
        // 空列表防穿透场景仍保留 key
        assertThat(result.get("TEST_AR_B3")).isEmpty();
    }

    @Test
    @DisplayName("batchGetCurrentAllocations: Service 返回空 Map 时返回空 Map")
    void batchGetCurrentAllocations_whenEmpty_returnsEmpty() {
        when(allocRelationService.batchGetCurrentAllocations(
                eq(Set.of("TEST_AR_NOPE")), isNull()))
                .thenReturn(Collections.emptyMap());

        Map<String, List<CustAllocRelationDTO>> result = allocApi.batchGetCurrentAllocations(
                Set.of("TEST_AR_NOPE"), null);

        assertThat(result).isEmpty();
    }

    // ------------------------- 批量查询: countCustomersByEmps -------------------------

    @Test
    @DisplayName("countCustomersByEmps: 委托 Service (bizKind 统一传 null) 返回原 Map")
    void countCustomersByEmps_ok() {
        // AllocApi 签名无 bizKind, Facade 统一传 null (任一业务种类匹配)
        when(allocRelationService.countCustomersByEmps(
                eq(Set.of("E001", "E002", "E003")), isNull()))
                .thenReturn(Map.of("E001", 10L, "E002", 5L, "E003", 0L));

        Map<String, Long> result = allocApi.countCustomersByEmps(Set.of("E001", "E002", "E003"));

        assertThat(result).hasSize(3);
        assertThat(result).containsEntry("E001", 10L);
        assertThat(result).containsEntry("E002", 5L);
        assertThat(result).containsEntry("E003", 0L);
    }

    // ------------------------- 批量查询: batchSummaryByEmps -------------------------

    @Test
    @DisplayName("batchSummaryByEmps: 委托 Service 直接返回 AllocSummaryDTO 列表")
    void batchSummaryByEmps_returnsList() {
        AllocSummaryDTO s1 = AllocSummaryDTO.builder()
                .empId("E001")
                .bizKind("DEPOSIT")
                .custCount(5L)
                .avgAllocRatio(new BigDecimal("80.00"))
                .asOfDate(LocalDate.of(2026, 4, 20))
                .build();
        AllocSummaryDTO s2 = AllocSummaryDTO.builder()
                .empId("E002")
                .bizKind("DEPOSIT")
                .custCount(3L)
                .avgAllocRatio(new BigDecimal("100.00"))
                .asOfDate(LocalDate.of(2026, 4, 20))
                .build();
        when(allocRelationService.batchSummaryByEmps(
                eq(Set.of("E001", "E002")), eq("DEPOSIT"), eq(LocalDate.of(2026, 4, 20))))
                .thenReturn(List.of(s1, s2));

        List<AllocSummaryDTO> result = allocApi.batchSummaryByEmps(
                Set.of("E001", "E002"), "DEPOSIT", LocalDate.of(2026, 4, 20));

        assertThat(result).hasSize(2);
        assertThat(result).extracting(AllocSummaryDTO::getEmpId).containsExactly("E001", "E002");
        assertThat(result).extracting(AllocSummaryDTO::getCustCount).containsExactly(5L, 3L);
    }

    // ------------------------- 快速判定: hasAllocation -------------------------

    @Test
    @DisplayName("hasAllocation: Service true 时返回 true")
    void hasAllocation_whenTrue_returnsTrue() {
        when(allocRelationService.hasAllocation("E001", "TEST_AR_C1", "DEPOSIT"))
                .thenReturn(true);

        assertThat(allocApi.hasAllocation("E001", "TEST_AR_C1", "DEPOSIT")).isTrue();
    }

    @Test
    @DisplayName("hasAllocation: Service false 时返回 false")
    void hasAllocation_whenFalse_returnsFalse() {
        when(allocRelationService.hasAllocation("E001", "TEST_AR_C1", "DEPOSIT"))
                .thenReturn(false);

        assertThat(allocApi.hasAllocation("E001", "TEST_AR_C1", "DEPOSIT")).isFalse();
    }

    // ------------------------- 快速判定: countCustomersOfEmp -------------------------

    @Test
    @DisplayName("countCustomersOfEmp: 委托 Service 返回 long 计数")
    void countCustomersOfEmp_ok() {
        when(allocRelationService.countCustomersOfEmp("E001", "DEPOSIT")).thenReturn(7L);

        assertThat(allocApi.countCustomersOfEmp("E001", "DEPOSIT")).isEqualTo(7L);
    }

    // ------------------------- 版本查询: getLatestAllocVersion -------------------------

    @Test
    @DisplayName("getLatestAllocVersion: 委托 Service 返回 DTO")
    void getLatestAllocVersion_ok() {
        AllocVersionDTO v = AllocVersionDTO.builder()
                .bizKind("DEPOSIT")
                .scopeDim("CUST")
                .currentVersion("v5")
                .latestDataDate(LocalDate.of(2026, 4, 20))
                .publishedAt(LocalDateTime.of(2026, 4, 20, 10, 30))
                .publishedBy(null)
                .build();
        when(allocRelationService.getLatestAllocVersion("DEPOSIT")).thenReturn(v);

        AllocVersionDTO result = allocApi.getLatestAllocVersion("DEPOSIT");

        assertThat(result).isNotNull();
        assertThat(result.getBizKind()).isEqualTo("DEPOSIT");
        assertThat(result.getScopeDim()).isEqualTo("CUST");
        assertThat(result.getCurrentVersion()).isEqualTo("v5");
        assertThat(result.getLatestDataDate()).isEqualTo(LocalDate.of(2026, 4, 20));
    }

    @Test
    @DisplayName("getLatestAllocVersion: Service 返回 null 时透传 null")
    void getLatestAllocVersion_whenNull_returnsNull() {
        when(allocRelationService.getLatestAllocVersion(any())).thenReturn(null);

        assertThat(allocApi.getLatestAllocVersion("DEPOSIT")).isNull();
    }

    // ------------------------- 版本查询: getAllocVersionAt -------------------------

    @Test
    @DisplayName("getAllocVersionAt: 委托 Service 返回 DTO")
    void getAllocVersionAt_ok() {
        AllocVersionDTO v = AllocVersionDTO.builder()
                .bizKind("LOAN")
                .scopeDim("CUST")
                .currentVersion("v3")
                .latestDataDate(LocalDate.of(2026, 3, 1))
                .publishedAt(LocalDateTime.of(2026, 3, 1, 9, 0))
                .publishedBy(null)
                .build();
        when(allocRelationService.getAllocVersionAt("LOAN", LocalDate.of(2026, 3, 15)))
                .thenReturn(v);

        AllocVersionDTO result = allocApi.getAllocVersionAt("LOAN", LocalDate.of(2026, 3, 15));

        assertThat(result).isNotNull();
        assertThat(result.getBizKind()).isEqualTo("LOAN");
        assertThat(result.getCurrentVersion()).isEqualTo("v3");
        assertThat(result.getLatestDataDate()).isEqualTo(LocalDate.of(2026, 3, 1));
    }

    @Test
    @DisplayName("getAllocVersionAt: Service 返回 null 时透传 null")
    void getAllocVersionAt_whenNull_returnsNull() {
        when(allocRelationService.getAllocVersionAt(eq("LOAN"), eq(LocalDate.of(2024, 1, 1))))
                .thenReturn(null);

        assertThat(allocApi.getAllocVersionAt("LOAN", LocalDate.of(2024, 1, 1))).isNull();
    }

    @Test
    @DisplayName("getLastApprovedAllocPreview: 委托 AllocAdjustPreviewService 并透传结果")
    void getLastApprovedAllocPreview_delegates() {
        var dto = new com.bank.branch.platform.performance.api.dto.AllocAdjustPreviewItemDTO();
        dto.setAllocDim("RULE");
        when(allocAdjustPreviewService.getLastApprovedAllocPreview("C001")).thenReturn(List.of(dto));

        var result = allocApi.getLastApprovedAllocPreview("C001");

        assertThat(result).hasSize(1);
        assertThat(result.get(0).getAllocDim()).isEqualTo("RULE");
    }
}
