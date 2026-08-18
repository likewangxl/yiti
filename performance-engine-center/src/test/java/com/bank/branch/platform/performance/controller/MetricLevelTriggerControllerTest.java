package com.bank.branch.platform.performance.controller;

import com.bank.branch.platform.auth.api.CurrentUserApi;
import com.bank.branch.platform.common.aop.annotation.AuditLog;
import com.bank.branch.platform.common.security.annotation.BizAuth;
import com.bank.branch.platform.common.security.enums.BizAction;
import com.bank.branch.platform.common.security.enums.BizType;
import com.bank.branch.platform.common.web.ResponseWrapper;
import com.bank.branch.platform.governance.api.dto.JobTriggerRespDTO;
import com.bank.branch.platform.performance.controller.dto.MetricLevelTriggerReqDTO;
import com.bank.branch.platform.performance.service.MetricBatchCalcService;
import com.bank.branch.platform.performance.service.MetricLevelTriggerService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.lang.reflect.Method;
import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** 按级别重算 HTTP 入口契约测试。 */
@ExtendWith(MockitoExtension.class)
class MetricLevelTriggerControllerTest {

    @Mock
    MetricBatchCalcService legacyService;
    @Mock
    MetricLevelTriggerService triggerService;
    @Mock
    CurrentUserApi currentUserApi;
    @InjectMocks
    MetricBatchCalcController controller;

    @Test
    void levelTrigger_delegatesToAsyncSubmissionService_notDirectCalculation() {
        MetricLevelTriggerReqDTO req = new MetricLevelTriggerReqDTO();
        req.setLevel(2);
        req.setDataDate(LocalDate.of(2026, 8, 17));
        req.setReason("补算");
        when(currentUserApi.getCurrentEmpId()).thenReturn("EMP001");
        JobTriggerRespDTO response = JobTriggerRespDTO.builder().jobKey("LEVEL2_METRIC_CALC").build();
        when(triggerService.trigger(req, "EMP001")).thenReturn(response);

        ResponseWrapper<JobTriggerRespDTO> actual = controller.triggerLevel(req);

        assertThat(actual.getData()).isSameAs(response);
        verify(triggerService).trigger(req, "EMP001");
        verify(legacyService, never()).execute(any(Integer.class), any(LocalDate.class),
                any(LocalDate.class), any(String.class));
    }

    @Test
    void levelTrigger_hasIndependentAuthAndAuditContract() throws Exception {
        Method method = MetricBatchCalcController.class.getDeclaredMethod("triggerLevel",
                MetricLevelTriggerReqDTO.class);
        BizAuth auth = method.getAnnotation(BizAuth.class);
        AuditLog audit = method.getAnnotation(AuditLog.class);

        assertThat(auth).isNotNull();
        assertThat(auth.bizType()).isEqualTo(BizType.PERF_CONFIG);
        assertThat(auth.action()).isEqualTo(BizAction.EXECUTE);
        assertThat(audit).isNotNull();
        assertThat(audit.reasonRequired()).isTrue();
        assertThat(audit.action()).isNotEqualTo("METRIC_BATCH_EXECUTE");
    }
}
