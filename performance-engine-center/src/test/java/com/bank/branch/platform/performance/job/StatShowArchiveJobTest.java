package com.bank.branch.platform.performance.job;

import com.bank.branch.platform.performance.entity.StatShowArchiveStatus;
import com.bank.branch.platform.performance.mapper.StatShowArchiveMapper;
import com.bank.branch.platform.performance.mapper.StatShowArchiveStatusMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** StatShowArchiveJob 编排单测：tmp 单日归档、边界 truncate、1 号主表幂等写入。 */
@ExtendWith(MockitoExtension.class)
class StatShowArchiveJobTest {

    private static final String CUST = "XAN_M98_CUST_STAT_SHOW3";
    private static final String EMP = "XAN_M98_EMP_STAT_SHOW3";

    @Mock
    private StatShowArchiveMapper mapper;
    @Mock
    private StatShowArchiveStatusMapper statusMapper;
    @InjectMocks
    private StatShowArchiveJob job;

    @BeforeEach
    void setUp() {
        lenient().when(statusMapper.selectList(any())).thenReturn(List.of());
        lenient().when(statusMapper.insert(any(StatShowArchiveStatus.class))).thenReturn(1);
        lenient().when(mapper.countByTableDate(anyString(), anyString())).thenReturn(2L);
    }

    @Test
    void ordinaryDay_deletesTargetDateThenCopiesTMinusOneFromTmp() {
        when(mapper.insertFromTmpByDate(anyString(), anyString(), anyString())).thenReturn(2);

        int inserted = job.run(LocalDate.of(2026, 8, 15)); // dataDate=08-14, run day 15 -> H2

        assertThat(inserted).isEqualTo(4);
        InOrder order = inOrder(mapper);
        order.verify(mapper).deleteByTableDate(CUST + "_H2", "2026-08-14");
        order.verify(mapper).insertFromTmpByDate(CUST + "_H2", CUST + "_TMP", "2026-08-14");
        order.verify(mapper).deleteByTableDate(EMP + "_H2", "2026-08-14");
        order.verify(mapper).insertFromTmpByDate(EMP + "_H2", EMP + "_TMP", "2026-08-14");
        verify(mapper, never()).truncateTable(anyString());
        verify(mapper, never()).deleteByTableDate(eq(CUST), anyString());
        verify(mapper, never()).deleteByTableDate(eq(EMP), anyString());
    }

    @Test
    void ordinaryDay_readsTmpCountBeforeCopying() {
        when(mapper.countByTableDate(anyString(), anyString())).thenReturn(2L);
        when(mapper.insertFromTmpByDate(anyString(), anyString(), anyString())).thenReturn(1);

        job.run(LocalDate.of(2026, 8, 15));

        verify(mapper).countByTableDate(CUST + "_TMP", "2026-08-14");
        verify(mapper).countByTableDate(EMP + "_TMP", "2026-08-14");
    }

    @Test
    void boundaryEleventh_truncatesCurrentHThenCopiesOnlyTMinusOne() {
        when(mapper.insertFromTmpByDate(anyString(), anyString(), anyString())).thenReturn(3);

        job.run(LocalDate.of(2026, 8, 11)); // dataDate=08-10, run day 11 -> H2

        InOrder order = inOrder(mapper);
        order.verify(mapper).truncateTable(CUST + "_H2");
        order.verify(mapper).insertFromTmpByDate(CUST + "_H2", CUST + "_TMP", "2026-08-10");
        order.verify(mapper).truncateTable(EMP + "_H2");
        order.verify(mapper).insertFromTmpByDate(EMP + "_H2", EMP + "_TMP", "2026-08-10");
        verify(mapper, never()).deleteByTableDate(eq(CUST + "_H2"), anyString());
        verify(mapper, never()).deleteByTableDate(eq(EMP + "_H2"), anyString());
    }

    @Test
    void boundaryTwentyFirst_truncatesH3() {
        job.run(LocalDate.of(2026, 8, 21)); // dataDate=08-20, run day 21 -> H3

        InOrder order = inOrder(mapper);
        order.verify(mapper).truncateTable(CUST + "_H3");
        order.verify(mapper).insertFromTmpByDate(CUST + "_H3", CUST + "_TMP", "2026-08-20");
        order.verify(mapper).truncateTable(EMP + "_H3");
        order.verify(mapper).insertFromTmpByDate(EMP + "_H3", EMP + "_TMP", "2026-08-20");
    }

    @Test
    void firstDay_truncatesH1AndUpsertsPreviousMonthEndIntoMain() {
        when(mapper.insertFromTmpByDate(anyString(), anyString(), anyString())).thenReturn(4);

        int inserted = job.run(LocalDate.of(2026, 8, 1)); // dataDate=07-31, run day 1 -> H1

        assertThat(inserted).isEqualTo(16);
        InOrder order = inOrder(mapper);
        order.verify(mapper).truncateTable(CUST + "_H1");
        order.verify(mapper).insertFromTmpByDate(CUST + "_H1", CUST + "_TMP", "2026-07-31");
        order.verify(mapper).deleteByTableDate(CUST, "2026-07-31");
        order.verify(mapper).insertFromTmpByDate(CUST, CUST + "_TMP", "2026-07-31");
        order.verify(mapper).truncateTable(EMP + "_H1");
        order.verify(mapper).insertFromTmpByDate(EMP + "_H1", EMP + "_TMP", "2026-07-31");
        order.verify(mapper).deleteByTableDate(EMP, "2026-07-31");
        order.verify(mapper).insertFromTmpByDate(EMP, EMP + "_TMP", "2026-07-31");
    }

    @Test
    void ordinaryDay_firstRun_recordsTmpCountForEachTarget() {
        when(mapper.countByTableDate(CUST + "_TMP", "2026-08-14")).thenReturn(12L);
        when(mapper.countByTableDate(EMP + "_TMP", "2026-08-14")).thenReturn(7L);
        when(mapper.insertFromTmpByDate(anyString(), anyString(), anyString())).thenReturn(2);

        int inserted = job.run(LocalDate.of(2026, 8, 15));

        assertThat(inserted).isEqualTo(4);
        ArgumentCaptor<StatShowArchiveStatus> captor =
                ArgumentCaptor.forClass(StatShowArchiveStatus.class);
        verify(statusMapper, times(2)).insert(captor.capture());
        List<StatShowArchiveStatus> records = captor.getAllValues();
        assertThat(records).extracting(StatShowArchiveStatus::getTargetTable)
                .containsExactly(CUST + "_H2", EMP + "_H2");
        assertThat(records).extracting(StatShowArchiveStatus::getTmpCount)
                .containsExactly(12L, 7L);
        assertThat(records).allSatisfy(record -> {
            assertThat(record.getId()).hasSize(32).doesNotContain("-");
            assertThat(record.getDataDate()).isEqualTo(LocalDate.of(2026, 8, 14));
            assertThat(record.getSourceTable()).isIn(CUST + "_TMP", EMP + "_TMP");
            assertThat(record.getArchiveStatus()).isEqualTo(StatShowArchiveStatus.SUCCESS);
            assertThat(record.getCreatedTime()).isNotNull();
        });
        assertThat(records.get(0).getCreatedTime()).isEqualTo(records.get(1).getCreatedTime());
        verify(statusMapper, times(1)).selectList(any());
    }

    @Test
    void existingSuccessRecord_skipsOnlyThatTarget() {
        statusMapperReturnsSuccess(CUST + "_H2", "2026-08-14");
        when(mapper.insertFromTmpByDate(anyString(), anyString(), anyString())).thenReturn(3);

        int inserted = job.run(LocalDate.of(2026, 8, 15));

        assertThat(inserted).isEqualTo(3);
        verify(mapper, never()).deleteByTableDate(CUST + "_H2", "2026-08-14");
        verify(mapper, never()).insertFromTmpByDate(CUST + "_H2", CUST + "_TMP", "2026-08-14");
        verify(mapper).countByTableDate(EMP + "_TMP", "2026-08-14");
        verify(mapper, never()).countByTableDate(CUST + "_TMP", "2026-08-14");
        verify(statusMapper).insert(any(StatShowArchiveStatus.class));
    }

    @Test
    void allTargetsSuccessful_returnsZeroWithoutTmpCountOrTargetDml() {
        when(statusMapper.selectList(any())).thenReturn(List.of(
                successStatus(CUST + "_H2", "2026-08-14"),
                successStatus(EMP + "_H2", "2026-08-14")));

        assertThat(job.run(LocalDate.of(2026, 8, 15))).isZero();

        verify(mapper, never()).countByTableDate(anyString(), anyString());
        verify(mapper, never()).deleteByTableDate(anyString(), anyString());
        verify(mapper, never()).truncateTable(anyString());
        verify(mapper, never()).insertFromTmpByDate(anyString(), anyString(), anyString());
        verify(statusMapper, never()).insert(any(StatShowArchiveStatus.class));
    }

    @Test
    void firstDay_historyAlreadySuccessful_processesOnlyMainTargets() {
        when(statusMapper.selectList(any())).thenReturn(List.of(
                successStatus(CUST + "_H1", "2026-07-31"),
                successStatus(EMP + "_H1", "2026-07-31")));
        when(mapper.insertFromTmpByDate(anyString(), anyString(), anyString())).thenReturn(4);

        int inserted = job.run(LocalDate.of(2026, 8, 1));

        assertThat(inserted).isEqualTo(8);
        verify(mapper, never()).truncateTable(CUST + "_H1");
        verify(mapper, never()).truncateTable(EMP + "_H1");
        verify(mapper, never()).insertFromTmpByDate(CUST + "_H1", CUST + "_TMP", "2026-07-31");
        verify(mapper, never()).insertFromTmpByDate(EMP + "_H1", EMP + "_TMP", "2026-07-31");
        verify(mapper).deleteByTableDate(CUST, "2026-07-31");
        verify(mapper).deleteByTableDate(EMP, "2026-07-31");
        verify(mapper).countByTableDate(CUST + "_TMP", "2026-07-31");
        verify(mapper).countByTableDate(EMP + "_TMP", "2026-07-31");
        verify(statusMapper, times(2)).insert(any(StatShowArchiveStatus.class));
    }

    @Test
    void tmpCountZero_skipsAllAssociatedDmlAndStatusWrites() {
        when(mapper.countByTableDate(anyString(), anyString())).thenReturn(0L);

        int inserted = job.run(LocalDate.of(2026, 8, 1));

        assertThat(inserted).isZero();
        verify(mapper, never()).truncateTable(anyString());
        verify(mapper, never()).deleteByTableDate(anyString(), anyString());
        verify(mapper, never()).insertFromTmpByDate(anyString(), anyString(), anyString());
        verify(statusMapper, never()).insert(any(StatShowArchiveStatus.class));
        verify(mapper).countByTableDate(CUST + "_TMP", "2026-07-31");
        verify(mapper).countByTableDate(EMP + "_TMP", "2026-07-31");
    }

    @Test
    void statusWriteOccursAfterTargetInsert() {
        when(mapper.insertFromTmpByDate(anyString(), anyString(), anyString())).thenReturn(1);

        InOrder order = inOrder(mapper, statusMapper);
        job.run(LocalDate.of(2026, 8, 15));

        order.verify(mapper).insertFromTmpByDate(CUST + "_H2", CUST + "_TMP", "2026-08-14");
        order.verify(statusMapper).insert(any(StatShowArchiveStatus.class));
        order.verify(mapper).insertFromTmpByDate(EMP + "_H2", EMP + "_TMP", "2026-08-14");
        order.verify(statusMapper).insert(any(StatShowArchiveStatus.class));
    }

    @Test
    void statusInsertAffectedRowsNotOne_throwsAfterTargetInsert() {
        when(mapper.insertFromTmpByDate(anyString(), anyString(), anyString())).thenReturn(1);
        when(statusMapper.insert(any(StatShowArchiveStatus.class))).thenReturn(0);

        assertThatThrownBy(() -> job.run(LocalDate.of(2026, 8, 15)))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("PERF_STAT_SHOW_ARCHIVE_STATUS");
        verify(mapper).insertFromTmpByDate(CUST + "_H2", CUST + "_TMP", "2026-08-14");
        verify(statusMapper).insert(any(StatShowArchiveStatus.class));
        verify(mapper, never()).deleteByTableDate(EMP + "_H2", "2026-08-14");
    }

    private void statusMapperReturnsSuccess(String targetTable, String dataDate) {
        when(statusMapper.selectList(any())).thenReturn(List.of(successStatus(targetTable, dataDate)));
    }

    private StatShowArchiveStatus successStatus(String targetTable, String dataDate) {
        StatShowArchiveStatus status = new StatShowArchiveStatus();
        status.setDataDate(LocalDate.parse(dataDate));
        status.setTargetTable(targetTable);
        status.setArchiveStatus(StatShowArchiveStatus.SUCCESS);
        return status;
    }
}
