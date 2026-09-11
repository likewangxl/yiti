package com.bank.branch.platform.performance.service;

import com.bank.branch.platform.auth.api.OrgGroupApi;
import com.bank.branch.platform.customer.api.MarketingOrgSnapshotQueryApi;
import com.bank.branch.platform.customer.api.dto.MarketingOrgSnapshotDTO;
import com.bank.branch.platform.performance.config.BranchDashboardBatchProperties;
import com.bank.branch.platform.performance.api.dto.BranchDashboardBatchDTO;
import com.bank.branch.platform.performance.entity.PerfMetricDef;
import com.bank.branch.platform.performance.entity.PerfRunTask;
import com.bank.branch.platform.performance.entity.SysControl;
import com.bank.branch.platform.performance.mapper.OrgIndexResultMapper;
import com.bank.branch.platform.performance.mapper.OrgMetricValueRow;
import com.bank.branch.platform.performance.mapper.PerfRunTaskMapper;
import com.bank.branch.platform.performance.api.TargetApi;
import com.bank.branch.platform.performance.api.dto.TargetPlanDTO;
import com.bank.branch.platform.performance.api.dto.TargetValueDTO;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 分行大屏不可变批次编排的 Red/Green 契约测试。
 *
 * <p>先锁定完整批次、集团贡献率和目标失败时不写 SUCCESS 的行为，避免实现先于口径。
 */
@ExtendWith(MockitoExtension.class)
class BranchDashboardBatchServiceTest {

    private static final String PROD_GROUP_CODE = "ORG_GRP_PRIMARY_OPERATING_UNITS";
    private static final String PROD_TARGET_PLAN_CODE = "PROD_TARGET_PLAN";

    @Mock
    private OrgGroupApi orgGroupApi;
    @Mock
    private MarketingOrgSnapshotQueryApi marketingApi;
    @Mock
    private TargetApi targetApi;
    @Mock
    private MetricDefService metricDefService;
    @Mock
    private SysControlService sysControlService;
    @Mock
    private OrgIndexResultMapper orgIndexResultMapper;
    @Mock
    private PerfRunTaskMapper runTaskMapper;

    private BranchDashboardBatchService service;
    private BranchDashboardBatchProperties properties;

    @BeforeEach
    void setUp() {
        properties = new BranchDashboardBatchProperties();
        properties.setEnabled(true);
        properties.setDataClassification("TEST");
        properties.setGroupCode("TEST_BRANCH_GROUP");
        properties.setTargetPlanCode("TEST_TARGET_PLAN");
        properties.setActualMetricCode("ACTUAL");
        properties.setUpstreamMetricCodes(List.of("ACTUAL"));
        properties.setCustomerMetricCode("CUSTOMER");
        properties.setAttentionMetricCode("ATTENTION");
        properties.setTargetMetricCode("TARGET");
        properties.setOrgRateMetricCode("ORG_RATE");
        properties.setGroupContributionMetricCode("GROUP_CONTRIBUTION");
        properties.setHistoryDays(7);

        ObjectMapper mapper = new ObjectMapper().registerModule(new JavaTimeModule());
        service = new BranchDashboardBatchService(
                orgGroupApi, marketingApi, targetApi, metricDefService, sysControlService,
                orgIndexResultMapper, runTaskMapper, properties, mapper);
    }

    @Test
    @DisplayName("完整批次使用实际/目标计算，机构贡献率按总目标计算")
    void completeBatch_usesActualAndTargetAndGroupContributionFormula() {
        LocalDate date = LocalDate.of(2026, 8, 30);
        when(orgGroupApi.listActiveMemberCodes("TEST_BRANCH_GROUP"))
                .thenReturn(Set.of("O1", "O2"));
        when(sysControlService.getCurrentVersion("ORG")).thenReturn(control("V1"));
        when(orgIndexResultMapper.selectDistinctDataDatesBefore(eq("V1"), any(), anyInt()))
                .thenReturn(List.of(date));
        when(metricDefService.getByCodes(anyList())).thenReturn(defs());
        when(orgIndexResultMapper.selectSlotValuesByOrgs(anyList(), eq(date), eq("V1"), anyInt()))
                .thenAnswer(invocation -> List.of(
                        row("O1", "ACTUAL", "100"), row("O2", "ACTUAL", "300")));
        when(marketingApi.batchQueryOrgSnapshots(List.of("O1", "O2"), date))
                .thenReturn(List.of(marketing("O1", 2, 1), marketing("O2", 3, 0)));
        when(targetApi.getTargetPlan("TEST_TARGET_PLAN"))
                .thenReturn(Optional.of(plan("P1")));
        when(targetApi.listTargetValues(eq("P1"), eq("ORG"), eq("O1"), eq("2026Q3")))
                .thenReturn(List.of(target("O1", "100")));
        when(targetApi.listTargetValues(eq("P1"), eq("ORG"), eq("O2"), eq("2026Q3")))
                .thenReturn(List.of(target("O2", "500")));

        BranchDashboardBatchDTO result = service.runBatch(date, "MANUAL", "tester");

        assertThat(result.getStatus()).isEqualTo("COMPLETE");
        assertThat(result.getDataClassification()).isEqualTo("TEST");
        assertThat(result.getRows()).hasSize(2);
        assertThat(result.getQuality().getExpected()).isEqualTo(12);
        assertThat(result.getQuality().getReceived()).isEqualTo(12);
        assertThat(result.getRows().get(0).getMetricValues().get("ORG_RATE"))
                .isEqualByComparingTo("100.00");
        assertThat(result.getRows().get(0).getMetricValues().get("GROUP_CONTRIBUTION"))
                .isEqualByComparingTo("16.6667");
        assertThat(result.getRows().get(1).getMetricValues().get("ORG_RATE"))
                .isEqualByComparingTo("60.00");
        assertThat(result.getRows().get(1).getMetricValues().get("GROUP_CONTRIBUTION"))
                .isEqualByComparingTo("50.0000");
        assertThat(result.getHistoryCoverage()).singleElement()
                .satisfies(coverage -> {
                    assertThat(coverage.getDataDate()).isEqualTo(date);
                    assertThat(coverage.getExpectedSubjects()).isEqualTo(2);
                    assertThat(coverage.getReceivedSubjects()).isEqualTo(2);
                    assertThat(coverage.isComplete()).isTrue();
                });
        assertThat(result.getDefinitionDigest()).hasSize(64);
        assertThat(result.getMetricContracts().get("ORG_RATE").getNumeratorMetricCode())
                .isEqualTo("ACTUAL");
        assertThat(result.getMetricContracts().get("ORG_RATE").getDenominatorMetricCode())
                .isEqualTo("TARGET");
        ArgumentCaptor<String> snapshotJson = ArgumentCaptor.forClass(String.class);
        verify(runTaskMapper).updateResultPreviewJson(any(), snapshotJson.capture());
        assertThat(snapshotJson.getValue()).doesNotContain("customerId", "mobile", "phone");
        verify(orgIndexResultMapper, times(5)).insertSlotValue(eq("O1"), eq(date), eq("V1"), anyInt(), any());
        verify(orgIndexResultMapper, times(5)).insertSlotValue(eq("O2"), eq(date), eq("V1"), anyInt(), any());
        verify(runTaskMapper).updateStatusWithParams(any(), eq("SUCCESS"), eq(null), any());
    }

    @Test
    @DisplayName("目标为零或缺失时任务 FAILED 且不得写入结果槽")
    void invalidTarget_marksTaskFailedAndDoesNotWriteSlots() {
        LocalDate date = LocalDate.of(2026, 8, 30);
        when(orgGroupApi.listActiveMemberCodes("TEST_BRANCH_GROUP"))
                .thenReturn(Set.of("O1"));
        when(sysControlService.getCurrentVersion("ORG")).thenReturn(control("V1"));
        when(orgIndexResultMapper.selectDistinctDataDatesBefore(eq("V1"), any(), anyInt()))
                .thenReturn(List.of(date));
        when(metricDefService.getByCodes(anyList())).thenReturn(defs());
        when(orgIndexResultMapper.selectSlotValuesByOrgs(anyList(), eq(date), eq("V1"), anyInt()))
                .thenReturn(List.of(row("O1", "ACTUAL", "100")));
        when(targetApi.getTargetPlan("TEST_TARGET_PLAN"))
                .thenReturn(Optional.of(plan("P1")));
        when(targetApi.listTargetValues(eq("P1"), eq("ORG"), eq("O1"), eq("2026Q3")))
                .thenReturn(List.of(target("O1", "0")));

        BranchDashboardBatchDTO result = service.runBatch(date, "MANUAL", "tester");

        assertThat(result.getStatus()).isEqualTo("FAILED");
        verify(runTaskMapper).updateStatusWithParams(any(), eq("FAILED"), any(), any());
        verify(runTaskMapper).updateBatchContext(any(), eq(date), eq("V1"));
        verify(orgIndexResultMapper, never()).insertSlotValue(any(), any(), any(), anyInt(), any());
        ArgumentCaptor<PerfRunTask> taskCaptor = ArgumentCaptor.forClass(PerfRunTask.class);
        verify(runTaskMapper).insert(taskCaptor.capture());
        assertThat(taskCaptor.getValue().getTaskType()).isEqualTo("BRANCH_DASHBOARD_BATCH");
    }

    @Test
    @DisplayName("AUTO 日期为空时不在建任务阶段序列化 null，服务自动选择完整日")
    void autoRunWithoutRequestedDate_selectsLatestCompleteDateAndSystemOperator() {
        LocalDate date = LocalDate.of(2026, 8, 30);
        when(orgGroupApi.listActiveMemberCodes("TEST_BRANCH_GROUP")).thenReturn(Set.of("O1"));
        when(sysControlService.getCurrentVersion("ORG")).thenReturn(control("V1"));
        when(orgIndexResultMapper.selectDistinctDataDatesBefore(eq("V1"), any(), anyInt()))
                .thenReturn(List.of(date));
        when(metricDefService.getByCodes(anyList())).thenReturn(defs());
        when(orgIndexResultMapper.selectSlotValuesByOrgs(anyList(), eq(date), eq("V1"), anyInt()))
                .thenReturn(List.of(row("O1", "ACTUAL", "100")));
        when(marketingApi.batchQueryOrgSnapshots(List.of("O1"), date))
                .thenReturn(List.of(marketing("O1", 2, 1)));
        when(targetApi.getTargetPlan("TEST_TARGET_PLAN")).thenReturn(Optional.of(plan("P1")));
        when(targetApi.listTargetValues(eq("P1"), eq("ORG"), eq("O1"), eq("2026Q3")))
                .thenReturn(List.of(target("O1", "100")));

        BranchDashboardBatchDTO result = service.runBatch(null, "AUTO", null);

        assertThat(result.getStatus()).isEqualTo("COMPLETE");
        assertThat(result.getDataDate()).isEqualTo(date);
        assertThat(result.getSourceAsOf().getTarget()).isEqualTo(LocalDate.of(2026, 1, 1));
        assertThat(result.getSourceModes()).containsEntry("revenue", "TEST_MANUAL");
        assertThat(result.getQuality().getExpectedSubjects()).isEqualTo(1);
        ArgumentCaptor<PerfRunTask> taskCaptor = ArgumentCaptor.forClass(PerfRunTask.class);
        verify(runTaskMapper).insert(taskCaptor.capture());
        assertThat(taskCaptor.getValue().getStartedBy()).isEqualTo("SYSTEM_BRANCH_DASHBOARD");
        assertThat(taskCaptor.getValue().getDataDate()).isNull();
    }

    @Test
    @DisplayName("客户快照缺少组成员时 FAILED 且旧槽位不被部分写入")
    void incompleteMarketingSnapshot_failsBeforeSlotCommit() {
        LocalDate date = LocalDate.of(2026, 8, 30);
        when(orgGroupApi.listActiveMemberCodes("TEST_BRANCH_GROUP")).thenReturn(Set.of("O1", "O2"));
        when(sysControlService.getCurrentVersion("ORG")).thenReturn(control("V1"));
        when(orgIndexResultMapper.selectDistinctDataDatesBefore(eq("V1"), any(), anyInt()))
                .thenReturn(List.of(date));
        when(metricDefService.getByCodes(anyList())).thenReturn(defs());
        when(orgIndexResultMapper.selectSlotValuesByOrgs(anyList(), eq(date), eq("V1"), anyInt()))
                .thenReturn(List.of(row("O1", "ACTUAL", "100"), row("O2", "ACTUAL", "300")));
        when(targetApi.getTargetPlan("TEST_TARGET_PLAN")).thenReturn(Optional.of(plan("P1")));
        when(targetApi.listTargetValues(eq("P1"), eq("ORG"), eq("O1"), eq("2026Q3")))
                .thenReturn(List.of(target("O1", "100")));
        when(targetApi.listTargetValues(eq("P1"), eq("ORG"), eq("O2"), eq("2026Q3")))
                .thenReturn(List.of(target("O2", "500")));
        when(marketingApi.batchQueryOrgSnapshots(List.of("O1", "O2"), date))
                .thenReturn(List.of(marketing("O1", 2, 1)));

        BranchDashboardBatchDTO result = service.runBatch(date, "MANUAL", "tester");

        assertThat(result.getStatus()).isEqualTo("FAILED");
        verify(orgIndexResultMapper, never()).insertSlotValue(any(), any(), any(), anyInt(), any());
        verify(runTaskMapper).updateStatusWithParams(any(), eq("FAILED"), any(), any());
    }

    @Test
    @DisplayName("客户快照存在重复机构时 FAILED 且不得写入结果槽")
    void duplicateMarketingOrg_failsBeforeSlotCommit() {
        MarketingOrgSnapshotDTO duplicate = marketing("O1", 3, 0);
        LocalDate date = stubTwoMemberBatch(
                List.of(marketing("O1", 2, 1), duplicate, marketing("O2", 3, 0)));

        BranchDashboardBatchDTO result = service.runBatch(date, "MANUAL", "tester");

        assertThat(result.getStatus()).isEqualTo("FAILED");
        assertThat(result.getQuality().getMissing())
                .anyMatch(message -> message.contains("客户营销快照机构重复"));
        verify(orgIndexResultMapper, never()).insertSlotValue(any(), any(), any(), anyInt(), any());
    }

    @Test
    @DisplayName("客户快照缺 sourceAsOfDate 时 FAILED 且不得写入结果槽")
    void marketingSnapshotWithoutSourceAsOfDate_failsBeforeSlotCommit() {
        MarketingOrgSnapshotDTO missingDate = marketing("O2", 3, 0);
        missingDate.setSourceAsOfDate(null);
        LocalDate date = stubTwoMemberBatch(List.of(marketing("O1", 2, 1), missingDate));

        BranchDashboardBatchDTO result = service.runBatch(date, "MANUAL", "tester");

        assertThat(result.getStatus()).isEqualTo("FAILED");
        assertThat(result.getQuality().getMissing())
                .anyMatch(message -> message.contains("sourceAsOfDate"));
        verify(orgIndexResultMapper, never()).insertSlotValue(any(), any(), any(), anyInt(), any());
    }

    @Test
    @DisplayName("客户快照缺 sourceMode 时 FAILED 且不得写入结果槽")
    void marketingSnapshotWithoutSourceMode_failsBeforeSlotCommit() {
        MarketingOrgSnapshotDTO missingMode = marketing("O2", 3, 0);
        missingMode.setSourceMode(" ");
        LocalDate date = stubTwoMemberBatch(List.of(marketing("O1", 2, 1), missingMode));

        BranchDashboardBatchDTO result = service.runBatch(date, "MANUAL", "tester");

        assertThat(result.getStatus()).isEqualTo("FAILED");
        assertThat(result.getQuality().getMissing())
                .anyMatch(message -> message.contains("sourceMode"));
        verify(orgIndexResultMapper, never()).insertSlotValue(any(), any(), any(), anyInt(), any());
    }

    @Test
    @DisplayName("客户快照 sourceMode 不一致时 FAILED 且不得写入结果槽")
    void mixedMarketingSourceMode_failsBeforeSlotCommit() {
        MarketingOrgSnapshotDTO mixedMode = marketing("O2", 3, 0);
        mixedMode.setSourceMode("HISTORICAL");
        LocalDate date = stubTwoMemberBatch(List.of(marketing("O1", 2, 1), mixedMode));

        BranchDashboardBatchDTO result = service.runBatch(date, "MANUAL", "tester");

        assertThat(result.getStatus()).isEqualTo("FAILED");
        assertThat(result.getQuality().getMissing())
                .anyMatch(message -> message.contains("sourceMode 不一致"));
        verify(orgIndexResultMapper, never()).insertSlotValue(any(), any(), any(), anyInt(), any());
    }

    @Test
    @DisplayName("数据分类必须严格为 TEST/PROD")
    void unsupportedDataClassification_failsClosed() {
        properties.setDataClassification("STAGING");

        BranchDashboardBatchDTO result = service.runBatch(
                LocalDate.of(2026, 8, 30), "MANUAL", "tester");

        assertThat(result.getStatus()).isEqualTo("FAILED");
        verify(orgGroupApi, never()).listActiveMemberCodes(any());
    }

    @Test
    @DisplayName("PROD 拒绝指标定义中的 RAND 演示来源")
    void productionRandomMetricSource_failsClosed() {
        properties.setDataClassification("PROD");
        properties.setGroupCode(PROD_GROUP_CODE);
        properties.setTargetPlanCode(PROD_TARGET_PLAN_CODE);
        List<PerfMetricDef> randomDefinitions = defs();
        randomDefinitions.forEach(def -> def.setSqlText("select FLOOR(RAND()*9000000)"));
        when(orgGroupApi.listActiveMemberCodes(PROD_GROUP_CODE)).thenReturn(Set.of("O1"));
        when(sysControlService.getCurrentVersion("ORG")).thenReturn(control("V1"));
        when(metricDefService.getByCodes(anyList())).thenReturn(randomDefinitions);

        BranchDashboardBatchDTO result = service.runBatch(
                LocalDate.of(2026, 8, 30), "MANUAL", "tester");

        assertThat(result.getStatus()).isEqualTo("FAILED");
        assertThat(result.getQuality().getMissing())
                .contains("PROD 指标来源包含测试/演示逻辑: ACTUAL");
        verify(orgIndexResultMapper, never()).selectDistinctDataDatesBefore(any(), any(), anyInt());
    }

    @Test
    @DisplayName("最新失败尝试只返回固定用户提示，不泄漏 SQL/JDBC 详情")
    void latestAttempt_sanitizesInternalError() {
        PerfRunTask failed = new PerfRunTask();
        failed.setId("B-FAILED");
        failed.setStatus("FAILED");
        failed.setErrorMsg("jdbc password=secret SQLSyntaxErrorException");
        when(runTaskMapper.selectByCondition(any(), any(), anyInt(), anyInt()))
                .thenReturn(List.of(failed));

        var attempt = service.findLatestAttempt("TEST_BRANCH_GROUP").orElseThrow();

        assertThat(attempt.getMessage()).isEqualTo("本次计算失败，请根据批次编号查看任务日志");
        assertThat(attempt.getMessage()).doesNotContainIgnoringCase("jdbc", "password", "sql");
    }

    @Test
    @DisplayName("客户营销指标为负数时 fail-close，不把异常值静默归零")
    void negativeMarketingMetric_failsClosed() {
        LocalDate date = LocalDate.of(2026, 8, 30);
        when(orgGroupApi.listActiveMemberCodes("TEST_BRANCH_GROUP")).thenReturn(Set.of("O1"));
        when(sysControlService.getCurrentVersion("ORG")).thenReturn(control("V1"));
        when(orgIndexResultMapper.selectDistinctDataDatesBefore(eq("V1"), any(), anyInt()))
                .thenReturn(List.of(date));
        when(metricDefService.getByCodes(anyList())).thenReturn(defs());
        when(orgIndexResultMapper.selectSlotValuesByOrgs(anyList(), eq(date), eq("V1"), anyInt()))
                .thenReturn(List.of(row("O1", "ACTUAL", "100")));
        when(marketingApi.batchQueryOrgSnapshots(List.of("O1"), date))
                .thenReturn(List.of(marketing("O1", -1, 1)));
        when(targetApi.getTargetPlan("TEST_TARGET_PLAN")).thenReturn(Optional.of(plan("P1")));
        when(targetApi.listTargetValues(eq("P1"), eq("ORG"), eq("O1"), eq("2026Q3")))
                .thenReturn(List.of(target("O1", "100")));

        BranchDashboardBatchDTO result = service.runBatch(date, "MANUAL", "tester");

        assertThat(result.getStatus()).isEqualTo("FAILED");
        verify(orgIndexResultMapper, never()).insertSlotValue(any(), any(), any(), anyInt(), any());
    }

    @Test
    @DisplayName("同机构同周期重复有效目标失败，不产生部分槽位")
    void duplicateTargetValues_failClosed() {
        LocalDate date = LocalDate.of(2026, 8, 30);
        when(orgGroupApi.listActiveMemberCodes("TEST_BRANCH_GROUP")).thenReturn(Set.of("O1"));
        when(sysControlService.getCurrentVersion("ORG")).thenReturn(control("V1"));
        when(orgIndexResultMapper.selectDistinctDataDatesBefore(eq("V1"), any(), anyInt()))
                .thenReturn(List.of(date));
        when(metricDefService.getByCodes(anyList())).thenReturn(defs());
        when(orgIndexResultMapper.selectSlotValuesByOrgs(anyList(), eq(date), eq("V1"), anyInt()))
                .thenReturn(List.of(row("O1", "ACTUAL", "100")));
        when(targetApi.getTargetPlan("TEST_TARGET_PLAN")).thenReturn(Optional.of(plan("P1")));
        when(targetApi.listTargetValues(eq("P1"), eq("ORG"), eq("O1"), eq("2026Q3")))
                .thenReturn(List.of(target("O1", "100"), target("O1", "200")));

        BranchDashboardBatchDTO result = service.runBatch(date, "MANUAL", "tester");

        assertThat(result.getStatus()).isEqualTo("FAILED");
        verify(orgIndexResultMapper, never()).insertSlotValue(any(), any(), any(), anyInt(), any());
    }

    @Test
    @DisplayName("生产分类缺单位 fail-close；TEST 才允许 expectedUnits 补足")
    void productionMissingUnit_failsClosed() {
        properties.setDataClassification("PROD");
        properties.setGroupCode(PROD_GROUP_CODE);
        properties.setTargetPlanCode(PROD_TARGET_PLAN_CODE);
        List<PerfMetricDef> noUnit = defs();
        noUnit.forEach(def -> def.setUnit(null));
        when(orgGroupApi.listActiveMemberCodes(PROD_GROUP_CODE)).thenReturn(Set.of("O1"));
        when(sysControlService.getCurrentVersion("ORG")).thenReturn(control("V1"));
        when(metricDefService.getByCodes(anyList())).thenReturn(noUnit);

        BranchDashboardBatchDTO result = service.runBatch(LocalDate.of(2026, 8, 30), "MANUAL", "tester");

        assertThat(result.getStatus()).isEqualTo("FAILED");
        assertThat(result.getQuality().getMissing()).anyMatch(reason ->
                reason.toLowerCase().contains("unit"));
        verify(orgIndexResultMapper, never()).selectDistinctDataDatesBefore(any(), any(), anyInt());
    }

    private LocalDate stubTwoMemberBatch(List<MarketingOrgSnapshotDTO> marketingRows) {
        LocalDate date = LocalDate.of(2026, 8, 30);
        List<String> members = List.of("O1", "O2");
        when(orgGroupApi.listActiveMemberCodes("TEST_BRANCH_GROUP"))
                .thenReturn(Set.copyOf(members));
        when(sysControlService.getCurrentVersion("ORG")).thenReturn(control("V1"));
        when(orgIndexResultMapper.selectDistinctDataDatesBefore(eq("V1"), any(), anyInt()))
                .thenReturn(List.of(date));
        when(metricDefService.getByCodes(anyList())).thenReturn(defs());
        when(orgIndexResultMapper.selectSlotValuesByOrgs(eq(members), eq(date), eq("V1"), anyInt()))
                .thenReturn(List.of(row("O1", "ACTUAL", "100"), row("O2", "ACTUAL", "300")));
        when(targetApi.getTargetPlan("TEST_TARGET_PLAN"))
                .thenReturn(Optional.of(plan("P1")));
        when(targetApi.listTargetValues(eq("P1"), eq("ORG"), eq("O1"), eq("2026Q3")))
                .thenReturn(List.of(target("O1", "100")));
        when(targetApi.listTargetValues(eq("P1"), eq("ORG"), eq("O2"), eq("2026Q3")))
                .thenReturn(List.of(target("O2", "500")));
        when(marketingApi.batchQueryOrgSnapshots(eq(members), eq(date))).thenReturn(marketingRows);
        return date;
    }

    private SysControl control(String version) {
        SysControl control = new SysControl();
        control.setScopeDim("ORG");
        control.setCurrentVersion(version);
        return control;
    }

    private List<PerfMetricDef> defs() {
        return List.of(
                def("ACTUAL", 1), def("CUSTOMER", 2), def("ATTENTION", 3),
                def("TARGET", 4), def("ORG_RATE", 5), def("GROUP_CONTRIBUTION", 6));
    }

    private PerfMetricDef def(String code, int slot) {
        PerfMetricDef def = new PerfMetricDef();
        def.setMetricCode(code);
        def.setBaseDim("ORG");
        def.setValSlot(slot);
        def.setStatus("ACTIVE");
        def.setUnit("元");
        def.setDecimalPlaces(4);
        def.setMetricDesc(code + " description");
        def.setDescription(code + " detailed description");
        return def;
    }

    private OrgMetricValueRow row(String orgCode, String code, String value) {
        OrgMetricValueRow row = new OrgMetricValueRow();
        row.setOrgCode(orgCode);
        row.setMetricValue(new BigDecimal(value));
        return row;
    }

    private MarketingOrgSnapshotDTO marketing(String orgCode, long customers, long attention) {
        return MarketingOrgSnapshotDTO.builder()
                .orgCode(orgCode)
                .validCustomerCount(customers)
                .pendingFollowUpTaskCount(attention)
                .asOfDate(LocalDate.of(2026, 8, 30))
                .sourceAsOfDate(LocalDate.of(2026, 8, 30))
                .sourceMode("CURRENT_STATE")
                .build();
    }

    private TargetPlanDTO plan(String id) {
        return TargetPlanDTO.builder()
                .id(id)
                .planCode("TEST_TARGET_PLAN")
                .targetDim("ORG")
                .targetCycle("QUARTER")
                .effectiveDate(LocalDate.of(2026, 1, 1))
                .startDate(LocalDate.of(2026, 1, 1))
                .endDate(LocalDate.of(2026, 12, 31))
                .status("ACTIVE")
                .build();
    }

    private TargetValueDTO target(String orgCode, String value) {
        return TargetValueDTO.builder()
                .subjectType("ORG")
                .subjectId(orgCode)
                .cycleKey("2026Q3")
                .metricCode("ACTUAL")
                .targetValue(new BigDecimal(value))
                .startDate(LocalDate.of(2026, 7, 1))
                .endDate(LocalDate.of(2026, 9, 30))
                .build();
    }
}
