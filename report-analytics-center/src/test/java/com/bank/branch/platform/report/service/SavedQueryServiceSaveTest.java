package com.bank.branch.platform.report.service;

import com.bank.branch.platform.auth.api.CurrentUserApi;
import com.bank.branch.platform.report.dto.req.SavedQuerySaveReqDTO;
import com.bank.branch.platform.report.entity.RptSavedQuery;
import com.bank.branch.platform.report.mapper.RptSavedQueryMapper;
import com.bank.branch.platform.report.service.impl.SavedQueryServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InOrder;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * SavedQueryService.saveQuery 测试（Task M1.5.1，Red）.
 *
 * <p>三档：
 * <ul>
 *   <li>< 10 条 → 仅 INSERT，不删旧</li>
 *   <li>= 10 条 → 先删最旧再 INSERT</li>
 *   <li>> 10 条（防御性）→ 仍然先删最旧再 INSERT</li>
 * </ul>
 */
@ExtendWith(MockitoExtension.class)
class SavedQueryServiceSaveTest {

    @Mock
    private RptSavedQueryMapper mapper;

    @Mock
    private CurrentUserApi currentUserApi;

    @InjectMocks
    private SavedQueryServiceImpl service;

    @BeforeEach
    void setUp() {
        when(currentUserApi.getCurrentEmpId()).thenReturn("E001");
    }

    @Test
    void saveQuery_whenUnder10_insertOnly() {
        when(mapper.countByEmpId("E001")).thenReturn(5);

        String id = service.saveQuery(buildReq("方案 X"));

        verify(mapper, never()).deleteById(anyString());
        verify(mapper, times(1)).insert(any(RptSavedQuery.class));
        assertThat(id).isNotBlank();
    }

    @Test
    void saveQuery_when10_shouldDeleteOldestThenInsert() {
        when(mapper.countByEmpId("E001")).thenReturn(10);
        when(mapper.findOldestId("E001")).thenReturn("Q-OLDEST");

        service.saveQuery(buildReq("方案 11"));

        InOrder order = inOrder(mapper);
        order.verify(mapper).deleteById("Q-OLDEST");
        order.verify(mapper).insert(any(RptSavedQuery.class));
    }

    @Test
    void saveQuery_when11OrMore_alsoDeletesOldest() {
        when(mapper.countByEmpId("E001")).thenReturn(11);
        when(mapper.findOldestId("E001")).thenReturn("Q-OLDEST");

        service.saveQuery(buildReq("any"));

        verify(mapper).deleteById("Q-OLDEST");
        verify(mapper).insert(any());
    }

    private SavedQuerySaveReqDTO buildReq(String name) {
        SavedQuerySaveReqDTO r = new SavedQuerySaveReqDTO();
        r.setName(name);
        r.setDim("EMP");
        r.setSubjectIds("[\"E001\"]");
        r.setMetricCodes("[\"M\"]");
        return r;
    }
}
