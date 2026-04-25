package com.bank.branch.platform.report.service;

import com.bank.branch.platform.auth.api.CurrentUserApi;
import com.bank.branch.platform.report.dto.resp.SavedQueryDetailRespDTO;
import com.bank.branch.platform.report.dto.resp.SavedQuerySummaryDTO;
import com.bank.branch.platform.report.entity.RptSavedQuery;
import com.bank.branch.platform.report.exception.RptException;
import com.bank.branch.platform.report.mapper.RptSavedQueryMapper;
import com.bank.branch.platform.report.service.impl.SavedQueryServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;

/**
 * SavedQueryService 列表 + 详情查询测试（Task M1.4.1，Red）.
 *
 * <p>覆盖 B.1 GET /api/reports/saved-queries（列表 + 可按 dim 筛选）和 GET /:id（详情）：
 * <ul>
 *   <li>列表：仅查本人方案，按 dim 过滤</li>
 *   <li>详情：本人方案返回 detail，不存在 → RPT-40001，他人 → RPT-40002</li>
 * </ul>
 */
@ExtendWith(MockitoExtension.class)
class SavedQueryServiceListTest {

    @Mock
    private RptSavedQueryMapper mapper;

    @Mock
    private CurrentUserApi currentUserApi;

    @InjectMocks
    private SavedQueryServiceImpl service;

    @BeforeEach
    void setUp() {
        lenient().when(currentUserApi.getCurrentEmpId()).thenReturn("E001");
    }

    @Test
    void listMine_filterByDim_returnsOnlyMatched() {
        RptSavedQuery q1 = mockEntity("Q1", "E001", "EMP", "方案A");
        RptSavedQuery q2 = mockEntity("Q2", "E001", "ORG", "方案B");
        when(mapper.listByEmpAndDim(eq("E001"), eq("EMP"))).thenReturn(List.of(q1));

        List<SavedQuerySummaryDTO> list = service.listMine("EMP");

        assertThat(list).hasSize(1);
        assertThat(list.get(0).getId()).isEqualTo("Q1");
        assertThat(list.get(0).getName()).isEqualTo("方案A");
        assertThat(list.get(0).getDim()).isEqualTo("EMP");
    }

    @Test
    void listMine_nullDim_returnsAllOfEmp() {
        when(mapper.listByEmpAndDim(eq("E001"), eq(null))).thenReturn(
                List.of(mockEntity("Q1", "E001", "EMP", "方案A"),
                        mockEntity("Q2", "E001", "ORG", "方案B")));

        List<SavedQuerySummaryDTO> list = service.listMine(null);

        assertThat(list).hasSize(2);
    }

    @Test
    void getDetail_existingMine_returnsDetail() {
        when(mapper.selectById("Q1")).thenReturn(mockEntity("Q1", "E001", "EMP", "方案A"));

        SavedQueryDetailRespDTO detail = service.getDetail("Q1");

        assertThat(detail.getId()).isEqualTo("Q1");
        assertThat(detail.getDim()).isEqualTo("EMP");
        assertThat(detail.getName()).isEqualTo("方案A");
    }

    @Test
    void getDetail_notFound_throwsRpt40001() {
        when(mapper.selectById(any())).thenReturn(null);

        assertThatThrownBy(() -> service.getDetail("UNKNOWN"))
                .isInstanceOf(RptException.class)
                .hasFieldOrPropertyWithValue("code", "RPT-40001");
    }

    @Test
    void getDetail_othersQuery_throwsRpt40002() {
        when(mapper.selectById("Q1")).thenReturn(mockEntity("Q1", "E_OTHER", "EMP", "他人方案"));

        assertThatThrownBy(() -> service.getDetail("Q1"))
                .isInstanceOf(RptException.class)
                .hasFieldOrPropertyWithValue("code", "RPT-40002");
    }

    private RptSavedQuery mockEntity(String id, String empId, String dim, String name) {
        RptSavedQuery e = new RptSavedQuery();
        e.setId(id);
        e.setEmpId(empId);
        e.setDim(dim);
        e.setName(name);
        e.setSubjectIds("[\"X\"]");
        e.setMetricCodes("[\"M\"]");
        e.setVersion(1);
        e.setCreatedTime(LocalDateTime.now());
        e.setUpdatedTime(LocalDateTime.now());
        return e;
    }
}
