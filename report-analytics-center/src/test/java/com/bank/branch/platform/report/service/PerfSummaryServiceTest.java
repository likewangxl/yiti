package com.bank.branch.platform.report.service;

import com.bank.branch.platform.auth.api.CurrentUserApi;
import com.bank.branch.platform.common.web.PageRequest;
import com.bank.branch.platform.common.web.PageResult;
import com.bank.branch.platform.performance.api.KpiApi;
import com.bank.branch.platform.report.dto.req.PerfSummaryReqDTO;
import com.bank.branch.platform.report.dto.resp.PerfSummaryRowVO;
import com.bank.branch.platform.report.enums.RptErrorCode;
import com.bank.branch.platform.report.exception.RptException;
import com.bank.branch.platform.report.service.impl.PerfSummaryServiceImpl;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.math.BigDecimal;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

/**
 * PerfSummaryService 单元测试（Task M3.2.1，Red）.
 *
 * <p>覆盖 C.3 GET /perf-summary 的 4 个分支：
 * <ol>
 *   <li>EMP 维度 happy path：单条循环 KpiApi.getCurrentKpiTotal 返回 BigDecimal 总分</li>
 *   <li>subjectIds &gt; 100 → RPT-40007</li>
 *   <li>KpiApi.getCurrentKpiTotal 返回 null（员工无 KPI 数据）→ 跳过该 subject 不计入结果</li>
 *   <li>KpiApi 抛异常 → 包装 RPT-50001（R4 fail-close）</li>
 * </ol>
 *
 * <p>注：performance.KpiApi.getCurrentKpiTotal 实际签名 {@code (String, String) → BigDecimal}
 * （非 plan L2030 的 KpiTotalDTO，按 V1.1 P4.3 真实签名适配）.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class PerfSummaryServiceTest {

    @Mock
    private KpiApi kpiApi;
    @Mock
    private CurrentUserApi currentUserApi;

    @InjectMocks
    private PerfSummaryServiceImpl service;

    @Test
    void getPerfSummary_empSingle_returnsKpiTotal() {
        when(kpiApi.getCurrentKpiTotal(eq("E001"), eq("MONTHLY")))
                .thenReturn(new BigDecimal("85.5"));

        PerfSummaryReqDTO req = new PerfSummaryReqDTO();
        req.setDim("EMP");
        req.setSubjectIds(List.of("E001"));
        req.setCycleType("MONTHLY");

        PageResult<PerfSummaryRowVO> result = service.getPerfSummary(req, new PageRequest());

        assertThat(result.getRecords()).hasSize(1);
        PerfSummaryRowVO row = result.getRecords().get(0);
        assertThat(row.getSubjectId()).isEqualTo("E001");
        assertThat(row.getCycleType()).isEqualTo("MONTHLY");
        assertThat(row.getTotalScore()).isEqualByComparingTo("85.5");
    }

    @Test
    void getPerfSummary_subjectIdsOver100_throwsRpt40007() {
        PerfSummaryReqDTO req = new PerfSummaryReqDTO();
        req.setDim("EMP");
        req.setSubjectIds(IntStream.range(0, 101)
                .mapToObj(i -> "E" + i).collect(Collectors.toList()));
        req.setCycleType("MONTHLY");

        assertThatThrownBy(() -> service.getPerfSummary(req, new PageRequest()))
                .isInstanceOf(RptException.class)
                .satisfies(ex -> assertThat(((RptException) ex).getErrorCode())
                        .isEqualTo(RptErrorCode.SUBJECT_SIZE_EXCEEDED));
    }

    @Test
    void getPerfSummary_kpiApiReturnsNull_skipsSubject() {
        // E001 有数据，E002 无数据（kpi_result 表无记录，KpiApi 返回 null）
        when(kpiApi.getCurrentKpiTotal(eq("E001"), eq("MONTHLY")))
                .thenReturn(new BigDecimal("90"));
        when(kpiApi.getCurrentKpiTotal(eq("E002"), eq("MONTHLY"))).thenReturn(null);

        PerfSummaryReqDTO req = new PerfSummaryReqDTO();
        req.setDim("EMP");
        req.setSubjectIds(List.of("E001", "E002"));
        req.setCycleType("MONTHLY");

        PageResult<PerfSummaryRowVO> result = service.getPerfSummary(req, new PageRequest());

        // E002 被跳过，只剩 1 行
        assertThat(result.getRecords()).hasSize(1);
        assertThat(result.getRecords().get(0).getSubjectId()).isEqualTo("E001");
    }

    @Test
    void getPerfSummary_kpiApiThrows_wrapsAsRpt50001() {
        when(kpiApi.getCurrentKpiTotal(anyString(), anyString()))
                .thenThrow(new RuntimeException("upstream"));

        PerfSummaryReqDTO req = new PerfSummaryReqDTO();
        req.setDim("EMP");
        req.setSubjectIds(List.of("E001"));
        req.setCycleType("MONTHLY");

        assertThatThrownBy(() -> service.getPerfSummary(req, new PageRequest()))
                .isInstanceOf(RptException.class)
                .satisfies(ex -> assertThat(((RptException) ex).getErrorCode())
                        .isEqualTo(RptErrorCode.CROSS_MODULE_CALL_FAILED));
    }
}
