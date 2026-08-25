package com.bank.branch.platform.performance.service;

import com.bank.branch.platform.governance.api.JobApi;
import com.bank.branch.platform.governance.api.dto.JobTriggerRespDTO;
import com.bank.branch.platform.performance.controller.dto.MetricLevelTriggerReqDTO;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** 按级别重算提交服务测试。 */
class MetricLevelTriggerServiceTest {

    private JobApi jobApi;
    private MetricLevelTriggerService service;

    @BeforeEach
    void setUp() {
        jobApi = mock(JobApi.class);
        service = new MetricLevelTriggerService(jobApi);
    }

    @Test
    void manualTrigger_mapsOnlyFixedLevelJobKey_andDelegatesToGovernance() {
        JobTriggerRespDTO expected = JobTriggerRespDTO.builder().jobKey("LEVEL1_METRIC_CALC").build();
        when(jobApi.triggerJobByKey(eq("LEVEL1_METRIC_CALC"), eq("MANUAL"), eq("补算"),
                eq("2026-08-17"), eq("2026-08-16"), eq("EMP001"))).thenReturn(expected);
        MetricLevelTriggerReqDTO req = request(1, LocalDate.of(2026, 8, 17), LocalDate.of(2026, 8, 16));

        JobTriggerRespDTO actual = service.trigger(req, "EMP001");

        assertThat(actual).isSameAs(expected);
        verify(jobApi).triggerJobByKey("LEVEL1_METRIC_CALC", "MANUAL", "补算",
                "2026-08-17", "2026-08-16", "EMP001");
    }

    @Test
    void coordinatorTrigger_supportsAutoAndNullOperator() {
        service.trigger(3, LocalDate.of(2026, 8, 17), null, null, "AUTO", null);

        verify(jobApi).triggerJobByKey("LEVEL3_METRIC_CALC", "AUTO", null,
                "2026-08-17", null, null);
    }

    @Test
    void nonLevelOne_allocDate_isRejected() {
        assertThatThrownBy(() -> service.trigger(2, LocalDate.of(2026, 8, 17), "补算",
                LocalDate.of(2026, 8, 16), "MANUAL", "EMP001"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("allocDate");
    }

    @Test
    void arbitraryLevel_isRejectedBeforeJobApi() {
        assertThatThrownBy(() -> service.resolveJobKey(4))
                .isInstanceOf(IllegalArgumentException.class);
    }

    private MetricLevelTriggerReqDTO request(int level, LocalDate dataDate, LocalDate allocDate) {
        MetricLevelTriggerReqDTO req = new MetricLevelTriggerReqDTO();
        req.setLevel(level);
        req.setDataDate(dataDate);
        req.setAllocDate(allocDate);
        req.setReason("补算");
        return req;
    }
}
