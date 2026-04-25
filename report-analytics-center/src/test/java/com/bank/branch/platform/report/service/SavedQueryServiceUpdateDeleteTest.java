package com.bank.branch.platform.report.service;

import com.bank.branch.platform.auth.api.CurrentUserApi;
import com.bank.branch.platform.report.dto.req.SavedQueryUpdateReqDTO;
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

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * SavedQueryService.updateQuery + deleteQuery 单元测试（Task M1.5.2 / M1.5.3，Red）.
 */
@ExtendWith(MockitoExtension.class)
class SavedQueryServiceUpdateDeleteTest {

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

    // ===== B.3 PUT update =====

    @Test
    void updateQuery_existingMineWithMatchingVersion_succeeds() {
        when(mapper.selectById("Q1")).thenReturn(mockEntity("Q1", "E001", 1));
        when(mapper.updateWithOptimisticLock(any(), eq(1))).thenReturn(1);

        service.updateQuery("Q1", buildUpdateReq(1, "改名"));

        verify(mapper, times(1)).updateWithOptimisticLock(any(), eq(1));
    }

    @Test
    void updateQuery_notFound_throwsRpt40001() {
        when(mapper.selectById("X")).thenReturn(null);
        assertThatThrownBy(() -> service.updateQuery("X", buildUpdateReq(1, "n")))
                .isInstanceOf(RptException.class)
                .hasFieldOrPropertyWithValue("code", "RPT-40001");
        verify(mapper, never()).updateWithOptimisticLock(any(), anyInt());
    }

    @Test
    void updateQuery_othersQuery_throwsRpt40002() {
        when(mapper.selectById("Q1")).thenReturn(mockEntity("Q1", "E_OTHER", 1));
        assertThatThrownBy(() -> service.updateQuery("Q1", buildUpdateReq(1, "n")))
                .isInstanceOf(RptException.class)
                .hasFieldOrPropertyWithValue("code", "RPT-40002");
    }

    @Test
    void updateQuery_versionMismatch_throwsRpt40002() {
        when(mapper.selectById("Q1")).thenReturn(mockEntity("Q1", "E001", 2));
        when(mapper.updateWithOptimisticLock(any(), eq(1))).thenReturn(0);
        assertThatThrownBy(() -> service.updateQuery("Q1", buildUpdateReq(1, "n")))
                .isInstanceOf(RptException.class)
                .hasFieldOrPropertyWithValue("code", "RPT-40002");
    }

    // ===== B.4 DELETE =====

    @Test
    void deleteQuery_existingMine_callsMapperDelete() {
        when(mapper.selectById("Q1")).thenReturn(mockEntity("Q1", "E001", 1));
        service.deleteQuery("Q1");
        verify(mapper, times(1)).deleteById("Q1");
    }

    @Test
    void deleteQuery_notFound_throwsRpt40001() {
        when(mapper.selectById("X")).thenReturn(null);
        assertThatThrownBy(() -> service.deleteQuery("X"))
                .isInstanceOf(RptException.class)
                .hasFieldOrPropertyWithValue("code", "RPT-40001");
    }

    @Test
    void deleteQuery_others_throwsRpt40002() {
        when(mapper.selectById("Q1")).thenReturn(mockEntity("Q1", "E_OTHER", 1));
        assertThatThrownBy(() -> service.deleteQuery("Q1"))
                .isInstanceOf(RptException.class)
                .hasFieldOrPropertyWithValue("code", "RPT-40002");
    }

    private RptSavedQuery mockEntity(String id, String empId, int version) {
        RptSavedQuery e = new RptSavedQuery();
        e.setId(id);
        e.setEmpId(empId);
        e.setName("orig");
        e.setDim("EMP");
        e.setSubjectIds("[]");
        e.setMetricCodes("[]");
        e.setVersion(version);
        e.setCreatedTime(LocalDateTime.now());
        e.setUpdatedTime(LocalDateTime.now());
        return e;
    }

    private SavedQueryUpdateReqDTO buildUpdateReq(int expectedVersion, String name) {
        SavedQueryUpdateReqDTO r = new SavedQueryUpdateReqDTO();
        r.setExpectedVersion(expectedVersion);
        r.setName(name);
        return r;
    }
}
