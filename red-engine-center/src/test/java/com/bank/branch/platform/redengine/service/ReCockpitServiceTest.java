package com.bank.branch.platform.redengine.service;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.bank.branch.platform.redengine.api.dto.ReCockpitOverviewDTO;
import com.bank.branch.platform.redengine.api.dto.ReOverdueItemDTO;
import com.bank.branch.platform.redengine.api.dto.ReRankingItemDTO;
import com.bank.branch.platform.redengine.api.dto.ReWarningItemDTO;
import com.bank.branch.platform.redengine.entity.ReAnnualResult;
import com.bank.branch.platform.redengine.entity.ReOverdueDeduction;
import com.bank.branch.platform.redengine.entity.ReScore;
import com.bank.branch.platform.redengine.entity.ReSubmit;
import com.bank.branch.platform.redengine.mapper.ReAnnualResultMapper;
import com.bank.branch.platform.redengine.mapper.ReOverdueDeductionMapper;
import com.bank.branch.platform.redengine.mapper.ReScoreMapper;
import com.bank.branch.platform.redengine.mapper.ReSubmitMapper;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.Year;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * ReCockpitService 单元测试 -- 纯 JUnit 5 + Mockito，不连数据库。
 * <p>覆盖 Task 10 简报要求的 4 类核心用例：①总览四计数；②红黄牌阈值边界(59.9/60/79.9/80，
 * BigDecimal 精确断言)；③逾期7天边界(第7天/第8天)；④年度归档维度聚合+upsert+is_qualified边界
 * (total=60/59.9)。并补充 executeOverdue/ranking/archiveSettlement 等其余公共方法的基础覆盖。</p>
 */
@ExtendWith(MockitoExtension.class)
class ReCockpitServiceTest {

    @Mock
    private ReSubmitMapper reSubmitMapper;

    @Mock
    private ReScoreMapper reScoreMapper;

    @Mock
    private ReOverdueDeductionMapper reOverdueDeductionMapper;

    @Mock
    private ReAnnualResultMapper reAnnualResultMapper;

    @InjectMocks
    private ReCockpitService reCockpitService;

    /** 预热各实体的 lambda 缓存，使 LambdaQueryWrapper 在纯单测中可正常构造. */
    @BeforeAll
    static void initTableInfo() {
        TableInfoHelper.initTableInfo(
                new MapperBuilderAssistant(new MybatisConfiguration(), ""), ReSubmit.class);
        TableInfoHelper.initTableInfo(
                new MapperBuilderAssistant(new MybatisConfiguration(), ""), ReScore.class);
        TableInfoHelper.initTableInfo(
                new MapperBuilderAssistant(new MybatisConfiguration(), ""), ReOverdueDeduction.class);
        TableInfoHelper.initTableInfo(
                new MapperBuilderAssistant(new MybatisConfiguration(), ""), ReAnnualResult.class);
    }

    private static ReSubmit submitWithStatus(Long id, Integer status) {
        ReSubmit s = new ReSubmit();
        s.setId(id);
        s.setStatus(status);
        return s;
    }

    private static ReSubmit submitWithDate(Long id, LocalDate submitDate) {
        ReSubmit s = new ReSubmit();
        s.setId(id);
        s.setOrgId(1L);
        s.setProjectName("项目" + id);
        s.setStatus(1);
        s.setSubmitDate(submitDate);
        return s;
    }

    private static ReSubmit submitWithDimension(Long id, String dimension) {
        ReSubmit s = new ReSubmit();
        s.setId(id);
        s.setDimension(dimension);
        return s;
    }

    private static ReScore scoreWithFinalScore(Long orgId, BigDecimal finalScore) {
        ReScore s = new ReScore();
        s.setOrgId(orgId);
        s.setFinalScore(finalScore);
        s.setScorePeriod("2026-01");
        return s;
    }

    private static ReScore scoreForOrgAndSubmit(Long orgId, Long submitId, BigDecimal finalScore) {
        ReScore s = new ReScore();
        s.setOrgId(orgId);
        s.setSubmitId(submitId);
        s.setFinalScore(finalScore);
        return s;
    }

    // ---------- ① 总览四计数 ----------

    @Test
    void getOverview_countsFourStatusesCorrectly() {
        when(reSubmitMapper.selectCount(any())).thenReturn(10L, 4L, 3L, 2L);

        ReCockpitOverviewDTO overview = reCockpitService.getOverview();

        assertThat(overview.getTotalSubmits()).isEqualTo(10L);
        assertThat(overview.getApprovedCount()).isEqualTo(4L);
        assertThat(overview.getPendingCount()).isEqualTo(3L);
        assertThat(overview.getRejectedCount()).isEqualTo(2L);
        verify(reSubmitMapper, org.mockito.Mockito.times(4)).selectCount(any());
    }

    // ---------- ② 红黄牌阈值边界(BigDecimal 精确断言) ----------

    @Test
    void getRedWarning_onlyScoresBelow60_59_9Included_60Excluded() {
        ReScore s59_9 = scoreWithFinalScore(100L, new BigDecimal("59.9"));
        ReScore s60 = scoreWithFinalScore(200L, new BigDecimal("60"));
        ReScore s79_9 = scoreWithFinalScore(300L, new BigDecimal("79.9"));
        ReScore s80 = scoreWithFinalScore(400L, new BigDecimal("80"));
        when(reScoreMapper.selectList(any())).thenReturn(List.of(s59_9, s60, s79_9, s80));

        List<ReWarningItemDTO> red = reCockpitService.getRedWarning();

        assertThat(red).extracting(ReWarningItemDTO::getOrgId).containsExactly(100L);
        assertThat(red.get(0).getFinalScore()).isEqualByComparingTo(new BigDecimal("59.9"));
        assertThat(red.get(0).getLevel()).isEqualTo("red");
    }

    @Test
    void getYellowWarning_60and79_9Included_59_9and80Excluded() {
        ReScore s59_9 = scoreWithFinalScore(100L, new BigDecimal("59.9"));
        ReScore s60 = scoreWithFinalScore(200L, new BigDecimal("60"));
        ReScore s79_9 = scoreWithFinalScore(300L, new BigDecimal("79.9"));
        ReScore s80 = scoreWithFinalScore(400L, new BigDecimal("80"));
        when(reScoreMapper.selectList(any())).thenReturn(List.of(s59_9, s60, s79_9, s80));

        List<ReWarningItemDTO> yellow = reCockpitService.getYellowWarning();

        assertThat(yellow).extracting(ReWarningItemDTO::getOrgId).containsExactlyInAnyOrder(200L, 300L);
        assertThat(yellow).allMatch(w -> "yellow".equals(w.getLevel()));
        // 精确断言：60 与 79.9 均在黄牌区间内，79.9 严格小于 80（不含上界）
        assertThat(yellow.stream().map(ReWarningItemDTO::getFinalScore))
                .containsExactlyInAnyOrder(new BigDecimal("60"), new BigDecimal("79.9"));
    }

    // ---------- ③ 逾期7天边界(第7天不逾期/第8天逾期) ----------

    @Test
    void getOverdueList_day7NotOverdue_day8Overdue() {
        LocalDate today = LocalDate.now();
        ReSubmit day7 = submitWithDate(1L, today.minusDays(7));   // submitDate+7 == today，非"早于"，不逾期
        ReSubmit day8 = submitWithDate(2L, today.minusDays(8));   // submitDate+7 == today-1，早于today，逾期
        when(reSubmitMapper.selectList(any())).thenReturn(List.of(day7, day8));

        List<ReOverdueItemDTO> result = reCockpitService.getOverdueList();

        assertThat(result).hasSize(1);
        assertThat(result.get(0).getId()).isEqualTo(2L);
        assertThat(result.get(0).getSubmitDate()).isEqualTo(today.minusDays(8));
    }

    @Test
    void getOverdueList_statusNotIn0Or1_excludedByWrapper_onlyMockedCandidatesConsidered() {
        // status 过滤发生在 wrapper 层(交由DB执行)；本用例仅验证 selectList 被调用且结果按 submitDate 边界二次精筛
        LocalDate today = LocalDate.now();
        ReSubmit overdue = submitWithDate(3L, today.minusDays(30));
        when(reSubmitMapper.selectList(any())).thenReturn(List.of(overdue));

        List<ReOverdueItemDTO> result = reCockpitService.getOverdueList();

        assertThat(result).hasSize(1);
        assertThat(result.get(0).getOrgId()).isEqualTo(1L);
        assertThat(result.get(0).getProjectName()).isEqualTo("项目3");
    }

    @Test
    void getOverdueList_nullSubmitDate_skipped() {
        ReSubmit noDate = submitWithStatus(4L, 1);
        when(reSubmitMapper.selectList(any())).thenReturn(List.of(noDate));

        List<ReOverdueItemDTO> result = reCockpitService.getOverdueList();

        assertThat(result).isEmpty();
    }

    // ---------- 年度归档：维度聚合 + upsert + is_qualified 边界 ----------

    @Test
    void generateAnnualResult_aggregatesDimensionsAcrossSubmits_thenInserts() {
        ReSubmit submitDim1 = submitWithDimension(11L, "dim1");
        ReSubmit submitDim2 = submitWithDimension(12L, "dim2");
        when(reSubmitMapper.selectList(any())).thenReturn(List.of(submitDim1, submitDim2));

        ReScore scoreDim1 = scoreForOrgAndSubmit(500L, 11L, new BigDecimal("20"));
        ReScore scoreDim2 = scoreForOrgAndSubmit(500L, 12L, new BigDecimal("15"));
        when(reScoreMapper.selectList(any())).thenReturn(List.of(scoreDim1, scoreDim2));
        when(reAnnualResultMapper.selectOne(any())).thenReturn(null);

        reCockpitService.generateAnnualResult(2026);

        ArgumentCaptor<ReAnnualResult> captor = ArgumentCaptor.forClass(ReAnnualResult.class);
        verify(reAnnualResultMapper).insert(captor.capture());
        ReAnnualResult saved = captor.getValue();

        assertThat(saved.getOrgId()).isEqualTo(500L);
        assertThat(saved.getDim1Score()).isEqualByComparingTo("20");
        assertThat(saved.getDim2Score()).isEqualByComparingTo("15");
        assertThat(saved.getDim3Score()).isEqualByComparingTo("0");
        assertThat(saved.getDim4Score()).isEqualByComparingTo("0");
        assertThat(saved.getTotalScore()).isEqualByComparingTo("35");
        assertThat(saved.getFinalScore()).isEqualByComparingTo("14.0");
        assertThat(saved.getIsQualified()).isEqualTo(0);
        assertThat(saved.getEvalYear()).isEqualTo(2026);
    }

    @Test
    void generateAnnualResult_isQualifiedBoundary_total60Qualified_total59_9NotQualified() {
        ReSubmit submitA = submitWithDimension(1L, "dim1");
        ReSubmit submitB = submitWithDimension(2L, "dim1");
        when(reSubmitMapper.selectList(any())).thenReturn(List.of(submitA, submitB));

        ReScore scoreOrg100 = scoreForOrgAndSubmit(100L, 1L, new BigDecimal("60"));
        ReScore scoreOrg200 = scoreForOrgAndSubmit(200L, 2L, new BigDecimal("59.9"));
        when(reScoreMapper.selectList(any())).thenReturn(List.of(scoreOrg100, scoreOrg200));
        when(reAnnualResultMapper.selectOne(any())).thenReturn(null);

        reCockpitService.generateAnnualResult(2026);

        ArgumentCaptor<ReAnnualResult> captor = ArgumentCaptor.forClass(ReAnnualResult.class);
        verify(reAnnualResultMapper, org.mockito.Mockito.times(2)).insert(captor.capture());
        List<ReAnnualResult> results = captor.getAllValues();

        ReAnnualResult r100 = results.stream().filter(r -> r.getOrgId().equals(100L)).findFirst().orElseThrow();
        assertThat(r100.getTotalScore()).isEqualByComparingTo("60");
        assertThat(r100.getIsQualified()).isEqualTo(1);
        assertThat(r100.getFinalScore()).isEqualByComparingTo("24.0");

        ReAnnualResult r200 = results.stream().filter(r -> r.getOrgId().equals(200L)).findFirst().orElseThrow();
        assertThat(r200.getTotalScore()).isEqualByComparingTo("59.9");
        assertThat(r200.getIsQualified()).isEqualTo(0);
    }

    @Test
    void generateAnnualResult_existingRecordFound_updatesById_doesNotInsert() {
        ReSubmit submitDim1 = submitWithDimension(21L, "dim1");
        when(reSubmitMapper.selectList(any())).thenReturn(List.of(submitDim1));
        ReScore score = scoreForOrgAndSubmit(600L, 21L, new BigDecimal("70"));
        when(reScoreMapper.selectList(any())).thenReturn(List.of(score));

        ReAnnualResult existing = new ReAnnualResult();
        existing.setId(999L);
        existing.setOrgId(600L);
        existing.setEvalYear(2026);
        when(reAnnualResultMapper.selectOne(any())).thenReturn(existing);

        reCockpitService.generateAnnualResult(2026);

        ArgumentCaptor<ReAnnualResult> captor = ArgumentCaptor.forClass(ReAnnualResult.class);
        verify(reAnnualResultMapper).updateById(captor.capture());
        verify(reAnnualResultMapper, never()).insert(any(ReAnnualResult.class));
        assertThat(captor.getValue().getId()).isEqualTo(999L);
        assertThat(captor.getValue().getDim1Score()).isEqualByComparingTo("70");
    }

    @Test
    void generateAnnualResult_scoreWithoutMatchingSubmit_skippedFromAggregation() {
        // score 关联的 submitId 在 RE_SUBMIT 中查不到(如已被物理清理)，该分不计入任何维度，不应导致异常
        when(reSubmitMapper.selectList(any())).thenReturn(List.of());
        ReScore orphanScore = scoreForOrgAndSubmit(700L, 999L, new BigDecimal("50"));
        when(reScoreMapper.selectList(any())).thenReturn(List.of(orphanScore));

        reCockpitService.generateAnnualResult(2026);

        verify(reAnnualResultMapper, never()).insert(any(ReAnnualResult.class));
        verify(reAnnualResultMapper, never()).updateById(any(ReAnnualResult.class));
    }

    @Test
    void generateAnnualResult_yearNull_defaultsToCurrentYear() {
        ReSubmit submitDim1 = submitWithDimension(31L, "dim1");
        when(reSubmitMapper.selectList(any())).thenReturn(List.of(submitDim1));
        ReScore score = scoreForOrgAndSubmit(800L, 31L, new BigDecimal("10"));
        when(reScoreMapper.selectList(any())).thenReturn(List.of(score));
        when(reAnnualResultMapper.selectOne(any())).thenReturn(null);

        reCockpitService.generateAnnualResult(null);

        ArgumentCaptor<ReAnnualResult> captor = ArgumentCaptor.forClass(ReAnnualResult.class);
        verify(reAnnualResultMapper).insert(captor.capture());
        assertThat(captor.getValue().getEvalYear()).isEqualTo(Year.now().getValue());
    }

    // ---------- 其余公共方法基础覆盖 ----------

    @Test
    void getRanking_mapsScoreListToRankedDTOs_preservingMapperOrder() {
        ReScore first = scoreWithFinalScore(1L, new BigDecimal("95"));
        first.setScorePeriod("2026-01");
        ReScore second = scoreWithFinalScore(2L, new BigDecimal("80"));
        second.setScorePeriod("2026-01");
        when(reScoreMapper.selectList(any())).thenReturn(List.of(first, second));

        List<ReRankingItemDTO> ranking = reCockpitService.getRanking();

        assertThat(ranking).hasSize(2);
        assertThat(ranking.get(0).getRank()).isEqualTo(1);
        assertThat(ranking.get(0).getOrgId()).isEqualTo(1L);
        assertThat(ranking.get(0).getFinalScore()).isEqualByComparingTo("95");
        assertThat(ranking.get(1).getRank()).isEqualTo(2);
        assertThat(ranking.get(1).getOrgId()).isEqualTo(2L);
    }

    @Test
    void executeOverdue_submitNotFound_throwsRe40005_andNeverInserts() {
        when(reSubmitMapper.selectById(999L)).thenReturn(null);

        assertThatThrownBy(() -> reCockpitService.executeOverdue(999L, new BigDecimal("5")))
                .isInstanceOf(com.bank.branch.platform.common.web.exception.BizException.class)
                .satisfies(ex -> {
                    var bizEx = (com.bank.branch.platform.common.web.exception.BizException) ex;
                    assertThat(bizEx.getCode()).isEqualTo("RE-40005");
                });

        verify(reOverdueDeductionMapper, never()).insert(any(ReOverdueDeduction.class));
    }

    @Test
    void executeOverdue_deductionPointsProvided_insertsWithGivenPoints() {
        ReSubmit submit = submitWithStatus(10L, 1);
        submit.setOrgId(50L);
        when(reSubmitMapper.selectById(10L)).thenReturn(submit);

        reCockpitService.executeOverdue(10L, new BigDecimal("8"));

        ArgumentCaptor<ReOverdueDeduction> captor = ArgumentCaptor.forClass(ReOverdueDeduction.class);
        verify(reOverdueDeductionMapper).insert(captor.capture());
        assertThat(captor.getValue().getOrgId()).isEqualTo(50L);
        assertThat(captor.getValue().getSubmitId()).isEqualTo(10L);
        assertThat(captor.getValue().getDeductionPoints()).isEqualByComparingTo("8");
    }

    @Test
    void executeOverdue_deductionPointsNull_defaultsTo5() {
        ReSubmit submit = submitWithStatus(11L, 1);
        submit.setOrgId(51L);
        when(reSubmitMapper.selectById(11L)).thenReturn(submit);

        reCockpitService.executeOverdue(11L, null);

        ArgumentCaptor<ReOverdueDeduction> captor = ArgumentCaptor.forClass(ReOverdueDeduction.class);
        verify(reOverdueDeductionMapper).insert(captor.capture());
        assertThat(captor.getValue().getDeductionPoints()).isEqualByComparingTo("5");
    }

    @Test
    void archiveSettlement_periodHasScores_returnsTrue() {
        when(reScoreMapper.selectList(any())).thenReturn(List.of(scoreWithFinalScore(1L, new BigDecimal("90"))));

        boolean result = reCockpitService.archiveSettlement("2026-01");

        assertThat(result).isTrue();
    }

    @Test
    void archiveSettlement_periodHasNoScores_returnsFalse() {
        when(reScoreMapper.selectList(any())).thenReturn(List.of());

        boolean result = reCockpitService.archiveSettlement("2099-12");

        assertThat(result).isFalse();
    }
}
