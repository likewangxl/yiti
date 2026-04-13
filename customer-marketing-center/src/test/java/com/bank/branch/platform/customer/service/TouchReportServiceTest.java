package com.bank.branch.platform.customer.service;

import com.bank.branch.platform.common.web.PageResult;
import com.bank.branch.platform.customer.dto.resp.TouchReportVO;
import com.bank.branch.platform.customer.dto.resp.TouchStatisticVO;
import com.bank.branch.platform.customer.mapper.TouchReportMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * TouchReportService 单元测试（TDD RED-GREEN 闭环）。
 * <p>
 * 使用 MockitoExtension，不需要 Spring 上下文。
 * 覆盖两个核心方法：listPage 分页查询 + statistic 状态统计。
 * </p>
 */
@ExtendWith(MockitoExtension.class)
class TouchReportServiceTest {

    @Mock
    private TouchReportMapper reportMapper;

    @InjectMocks
    private TouchReportService touchReportService;

    // ==================== listPage ====================

    /**
     * 正常分页查询：验证 offset 计算正确 (pageNo-1)*pageSize，以及返回的分页结果字段正确。
     */
    @Test
    void listPage_shouldReturnPagedReport() {
        // given
        TouchReportVO vo1 = new TouchReportVO();
        vo1.setTaskNo("TK-20260101-001");
        vo1.setTaskType("FIRST_TOUCH");
        vo1.setTaskStatus("PENDING");
        vo1.setSlaStatus("GREEN");
        vo1.setCustName("深圳科技有限公司");
        vo1.setAssigneeEmpId("E10001");
        vo1.setOrgId("ORG_SZ_001");
        vo1.setPlanFinishTime(LocalDateTime.of(2026, 1, 10, 18, 0));
        vo1.setLogCount(2L);

        TouchReportVO vo2 = new TouchReportVO();
        vo2.setTaskNo("TK-20260101-002");
        vo2.setTaskType("FOLLOW_UP");
        vo2.setTaskStatus("SUCCESS");
        vo2.setSlaStatus("GREEN");
        vo2.setCustName("广州贸易股份有限公司");
        vo2.setAssigneeEmpId("E10002");
        vo2.setOrgId("ORG_SZ_001");
        vo2.setLogCount(5L);

        List<TouchReportVO> mockList = Arrays.asList(vo1, vo2);

        // pageNo=2, pageSize=10 → offset = (2-1)*10 = 10
        when(reportMapper.selectReportPage(isNull(), isNull(), eq("ORG_SZ_001"), eq(10), eq(10)))
                .thenReturn(mockList);
        when(reportMapper.countReportPage(isNull(), isNull(), eq("ORG_SZ_001")))
                .thenReturn(25L);

        // when
        PageResult<TouchReportVO> result = touchReportService.listPage(null, null, "ORG_SZ_001", 2, 10);

        // then
        assertThat(result).isNotNull();
        assertThat(result.getPageNo()).isEqualTo(2);
        assertThat(result.getPageSize()).isEqualTo(10);
        assertThat(result.getTotal()).isEqualTo(25L);
        assertThat(result.getRecords()).hasSize(2);
        assertThat(result.getRecords().get(0).getTaskNo()).isEqualTo("TK-20260101-001");
        assertThat(result.getRecords().get(0).getCustName()).isEqualTo("深圳科技有限公司");
        assertThat(result.getRecords().get(0).getLogCount()).isEqualTo(2L);
        assertThat(result.getRecords().get(1).getTaskNo()).isEqualTo("TK-20260101-002");

        // 验证 offset 计算：(2-1)*10 = 10
        verify(reportMapper).selectReportPage(null, null, "ORG_SZ_001", 10, 10);
        verify(reportMapper).countReportPage(null, null, "ORG_SZ_001");
    }

    /**
     * 带过滤条件的分页查询：验证 keyword 和 status 参数正确透传给 Mapper。
     */
    @Test
    void listPage_shouldPassFilterParamsToMapper() {
        // given
        TouchReportVO vo = new TouchReportVO();
        vo.setTaskNo("TK-20260101-003");
        vo.setTaskStatus("PENDING");

        when(reportMapper.selectReportPage(eq("科技"), eq("PENDING"), eq("ORG_SZ_001"), eq(0), eq(20)))
                .thenReturn(List.of(vo));
        when(reportMapper.countReportPage(eq("科技"), eq("PENDING"), eq("ORG_SZ_001")))
                .thenReturn(1L);

        // when: pageNo=1 → offset=0
        PageResult<TouchReportVO> result = touchReportService.listPage("科技", "PENDING", "ORG_SZ_001", 1, 20);

        // then
        assertThat(result.getTotal()).isEqualTo(1L);
        assertThat(result.getRecords()).hasSize(1);
        assertThat(result.getRecords().get(0).getTaskStatus()).isEqualTo("PENDING");

        verify(reportMapper).selectReportPage("科技", "PENDING", "ORG_SZ_001", 0, 20);
    }

    // ==================== statistic ====================

    /**
     * 状态统计：验证返回按状态分组的统计列表，字段映射正确。
     */
    @Test
    void statistic_shouldReturnGroupedCounts() {
        // given
        TouchStatisticVO stat1 = new TouchStatisticVO();
        stat1.setStatus("PENDING");
        stat1.setCount(15L);

        TouchStatisticVO stat2 = new TouchStatisticVO();
        stat2.setStatus("SUCCESS");
        stat2.setCount(8L);

        TouchStatisticVO stat3 = new TouchStatisticVO();
        stat3.setStatus("CANCELLED");
        stat3.setCount(3L);

        List<TouchStatisticVO> mockStats = Arrays.asList(stat1, stat2, stat3);
        when(reportMapper.selectStatistics(eq("ORG_SZ_001"))).thenReturn(mockStats);

        // when
        List<TouchStatisticVO> result = touchReportService.statistic("ORG_SZ_001");

        // then
        assertThat(result).isNotNull();
        assertThat(result).hasSize(3);
        assertThat(result.get(0).getStatus()).isEqualTo("PENDING");
        assertThat(result.get(0).getCount()).isEqualTo(15L);
        assertThat(result.get(1).getStatus()).isEqualTo("SUCCESS");
        assertThat(result.get(1).getCount()).isEqualTo(8L);
        assertThat(result.get(2).getStatus()).isEqualTo("CANCELLED");
        assertThat(result.get(2).getCount()).isEqualTo(3L);

        verify(reportMapper).selectStatistics("ORG_SZ_001");
    }

    /**
     * 统计接口：orgId 为 null 时，应直接传 null 给 Mapper（统计全量数据）。
     */
    @Test
    void statistic_shouldPassNullOrgIdToMapper() {
        // given
        when(reportMapper.selectStatistics(isNull())).thenReturn(List.of());

        // when
        List<TouchStatisticVO> result = touchReportService.statistic(null);

        // then
        assertThat(result).isEmpty();
        verify(reportMapper).selectStatistics(null);
    }
}
