package com.bank.branch.platform.customer.service;

import com.bank.branch.platform.customer.dto.resp.TouchReportVO;
import com.bank.branch.platform.customer.mapper.TouchReportMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Arrays;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * TouchReportService.listAllForExport 单元测试（TDD Red 阶段）。
 * <p>
 * 验证 listAllForExport 方法正确调用 Mapper 查询并返回结果列表。
 * </p>
 */
@ExtendWith(MockitoExtension.class)
class TouchReportServiceExportTest {

    @Mock
    private TouchReportMapper reportMapper;

    @InjectMocks
    private TouchReportService touchReportService;

    /**
     * listAllForExport：正常场景，验证 offset=0, limit=maxRows，返回报告列表。
     */
    @Test
    void listAllForExport_shouldReturnReportList() {
        // given
        TouchReportVO vo1 = new TouchReportVO();
        vo1.setTaskNo("TK-20260101-001");
        vo1.setTaskType("FIRST_TOUCH");
        vo1.setTaskStatus("PENDING");
        vo1.setSlaStatus("GREEN");
        vo1.setCustName("深圳科技有限公司");
        vo1.setAssigneeEmpId("E10001");
        vo1.setOrgId("ORG_SZ_001");
        vo1.setLogCount(2L);

        TouchReportVO vo2 = new TouchReportVO();
        vo2.setTaskNo("TK-20260101-002");
        vo2.setTaskType("FOLLOW_UP");
        vo2.setTaskStatus("SUCCESS");
        vo2.setLogCount(5L);

        // maxRows=10000 → offset=0, limit=10000
        when(reportMapper.selectReportPage(isNull(), isNull(), isNull(), eq(0), eq(10000)))
                .thenReturn(Arrays.asList(vo1, vo2));

        // when
        List<TouchReportVO> result = touchReportService.listAllForExport(null, null, null, 10000);

        // then
        assertThat(result).hasSize(2);
        assertThat(result.get(0).getTaskNo()).isEqualTo("TK-20260101-001");
        assertThat(result.get(1).getTaskNo()).isEqualTo("TK-20260101-002");

        // 验证 offset=0, limit=10000
        verify(reportMapper).selectReportPage(null, null, null, 0, 10000);
    }

    /**
     * listAllForExport：带过滤参数时正确透传。
     */
    @Test
    void listAllForExport_shouldPassFilterParams() {
        // given
        TouchReportVO vo = new TouchReportVO();
        vo.setTaskNo("TK-20260101-003");
        vo.setTaskStatus("PENDING");

        when(reportMapper.selectReportPage(eq("科技"), eq("PENDING"), eq("ORG_SZ_001"), eq(0), eq(5000)))
                .thenReturn(List.of(vo));

        // when
        List<TouchReportVO> result = touchReportService.listAllForExport("科技", "PENDING", "ORG_SZ_001", 5000);

        // then
        assertThat(result).hasSize(1);
        assertThat(result.get(0).getTaskStatus()).isEqualTo("PENDING");

        verify(reportMapper).selectReportPage("科技", "PENDING", "ORG_SZ_001", 0, 5000);
    }

    /**
     * listAllForExport：空结果时返回空列表。
     */
    @Test
    void listAllForExport_shouldReturnEmptyListWhenNoData() {
        // given
        when(reportMapper.selectReportPage(isNull(), isNull(), isNull(), eq(0), eq(10000)))
                .thenReturn(List.of());

        // when
        List<TouchReportVO> result = touchReportService.listAllForExport(null, null, null, 10000);

        // then
        assertThat(result).isEmpty();
    }
}
