package com.bank.branch.platform.performance.facade;

import com.bank.branch.platform.auth.api.OrgGroupApi;
import com.bank.branch.platform.performance.api.dto.BranchDashboardBatchDTO;
import com.bank.branch.platform.performance.api.dto.BranchDashboardBatchAttemptDTO;
import com.bank.branch.platform.performance.api.dto.BranchDashboardBatchRowDTO;
import com.bank.branch.platform.performance.api.dto.BranchDashboardQualityDTO;
import com.bank.branch.platform.performance.entity.PerfRunTask;
import com.bank.branch.platform.performance.service.BranchDashboardBatchService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.junit.jupiter.api.extension.ExtendWith;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class BranchDashboardBatchQueryApiImplTest {

    @Mock
    private BranchDashboardBatchService batchService;
    @Mock
    private OrgGroupApi orgGroupApi;

    private BranchDashboardBatchQueryApiImpl api;
    private PerfRunTask task;
    private BranchDashboardBatchDTO snapshot;

    @BeforeEach
    void setUp() {
        api = new BranchDashboardBatchQueryApiImpl(batchService, orgGroupApi);
        task = new PerfRunTask();
        task.setId("BATCH_1");
        task.setTaskType(BranchDashboardBatchService.TASK_TYPE);
        task.setTaskKey("G1");
        task.setStatus("SUCCESS");
        snapshot = BranchDashboardBatchDTO.builder()
                .batchId("BATCH_1")
                .groupCode("G1")
                .dataDate(LocalDate.of(2026, 8, 30))
                .version("V1")
                .status("COMPLETE")
                .memberOrgCodes(List.of("O1", "O2"))
                .quality(BranchDashboardQualityDTO.builder()
                        .expected(2).received(2).expectedSubjects(2).receivedSubjects(2).build())
                .rows(List.of(row("O1"), row("O2")))
                .build();
    }

    @Test
    @DisplayName("latest 返回不可变批次并附最近尝试状态")
    void latest_returnsImmutableSnapshotAndAttempt() {
        when(batchService.findLatestSuccess("G1")).thenReturn(Optional.of(task));
        when(orgGroupApi.listActiveMemberCodes("G1")).thenReturn(Set.of("O1", "O2"));
        when(batchService.readSnapshot(task)).thenReturn(Optional.of(snapshot));
        when(batchService.findLatestAttempt("G1")).thenReturn(Optional.of(BranchDashboardBatchAttemptDTO.builder()
                .attemptId("BATCH_1").status("COMPLETE").build()));

        Optional<BranchDashboardBatchDTO> result = api.latest("G1", List.of("O1", "O2"));

        assertThat(result).isPresent();
        assertThat(result.orElseThrow().getBatchId()).isEqualTo("BATCH_1");
        assertThat(result.orElseThrow().getRows()).extracting(BranchDashboardBatchRowDTO::getOrgCode)
                .containsExactly("O1", "O2");
        assertThat(result.orElseThrow().getLatestAttempt().getStatus()).isEqualTo("COMPLETE");
    }

    @Test
    @DisplayName("成员变化和授权缩小只返回当前交集，并标记 PARTIAL")
    void byId_filtersRemovedAndUnauthorizedMembers() {
        when(batchService.findTask("BATCH_1")).thenReturn(Optional.of(task));
        when(orgGroupApi.listActiveMemberCodes("G1")).thenReturn(Set.of("O1", "O3"));
        when(batchService.readSnapshot(task)).thenReturn(Optional.of(snapshot));

        Optional<BranchDashboardBatchDTO> result = api.byId("BATCH_1", List.of("O1"));

        assertThat(result).isPresent();
        BranchDashboardBatchDTO filtered = result.orElseThrow();
        assertThat(filtered.getStatus()).isEqualTo("PARTIAL");
        assertThat(filtered.getMemberOrgCodes()).containsExactly("O1");
        assertThat(filtered.getRows()).extracting(BranchDashboardBatchRowDTO::getOrgCode)
                .containsExactly("O1");
        assertThat(filtered.getQuality().getMissing())
                .contains("MEMBER_REMOVED:O2", "MEMBER_MISSING_FROM_BATCH:O3");
    }

    @Test
    @DisplayName("非本批次任务或空授权集合 fail-close")
    void query_failClosedForWrongTaskOrMissingAuthorization() {
        when(batchService.findTask("BATCH_1")).thenReturn(Optional.of(task));
        assertThat(api.byId("BATCH_1", List.of())).isEmpty();

        task.setTaskType("METRIC_RUN");
        assertThat(api.byId("BATCH_1", List.of("O1", "O2"))).isEmpty();
    }

    private BranchDashboardBatchRowDTO row(String orgCode) {
        return BranchDashboardBatchRowDTO.builder()
                .orgCode(orgCode)
                .dataDate(LocalDate.of(2026, 8, 30))
                .metricValues(java.util.Map.of("M", new BigDecimal("1")))
                .build();
    }
}
