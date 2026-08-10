package com.bank.branch.platform.redengine.service;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.bank.branch.platform.common.web.PageResult;
import com.bank.branch.platform.common.web.exception.BizException;
import com.bank.branch.platform.redengine.entity.ReScore;
import com.bank.branch.platform.redengine.entity.ReSubmit;
import com.bank.branch.platform.redengine.mapper.ReScoreMapper;
import com.bank.branch.platform.redengine.mapper.ReSubmitMapper;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.ArgumentMatchers;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.Year;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * ReReviewService 单元测试 -- 纯 JUnit 5 + Mockito，不连数据库。
 * 覆盖 Task 9 简报要求的 4 个核心用例（队列只含 status=1 / approve 超限抛 RE-40004 且不落
 * RE_SCORE 不改状态 / approve 正常落 RE_SCORE+状态2+reviewerId/reviewDate / reject 状态3），
 * 并补充 approve 上报不存在抛 RE-40003、score 为 null/0 时跳过评分落库，以及创建人与审核人
 * 相同时 approve/reject 均以 RE-40008 fail-close 的边界用例。
 */
@ExtendWith(MockitoExtension.class)
class ReReviewServiceTest {

    @Mock
    private ReSubmitMapper reSubmitMapper;

    @Mock
    private ReScoreMapper reScoreMapper;

    @InjectMocks
    private ReReviewService reReviewService;

    /** 预热 ReSubmit/ReScore 的 lambda 缓存，使 LambdaQueryWrapper.getTargetSql() 可在纯单测中渲染. */
    @BeforeAll
    static void initTableInfo() {
        TableInfoHelper.initTableInfo(
                new MapperBuilderAssistant(new MybatisConfiguration(), ""), ReSubmit.class);
        TableInfoHelper.initTableInfo(
                new MapperBuilderAssistant(new MybatisConfiguration(), ""), ReScore.class);
    }

    private static ReSubmit existingSubmit(Long id, Long orgId, String itemCode, BigDecimal maxScore) {
        ReSubmit submit = new ReSubmit();
        submit.setId(id);
        submit.setOrgId(orgId);
        submit.setItemCode(itemCode);
        submit.setMaxScore(maxScore);
        submit.setStatus(1);
        return submit;
    }

    private static ReScore score(BigDecimal finalScore) {
        ReScore s = new ReScore();
        s.setFinalScore(finalScore);
        return s;
    }

    @Test
    void getReviewQueue_onlyStatus1_orderedBySubmitDateAsc() {
        ReSubmit s1 = new ReSubmit();
        s1.setId(1L);
        s1.setStatus(1);
        Page<ReSubmit> mpPage = new Page<>(1, 10);
        mpPage.setRecords(List.of(s1));
        mpPage.setTotal(1L);

        @SuppressWarnings("unchecked")
        ArgumentCaptor<LambdaQueryWrapper<ReSubmit>> wrapperCaptor = ArgumentCaptor.forClass(LambdaQueryWrapper.class);
        when(reSubmitMapper.selectPage(ArgumentMatchers.<IPage<ReSubmit>>any(), wrapperCaptor.capture()))
                .thenReturn(mpPage);

        PageResult<ReSubmit> result = reReviewService.getReviewQueue(1, 10);

        assertThat(result.getTotal()).isEqualTo(1L);
        assertThat(result.getRecords()).hasSize(1);

        // 队列条件必须限定 status=1，且渲染出的 targetSql 含 ORDER BY submit_date 升序标记
        LambdaQueryWrapper<ReSubmit> wrapper = wrapperCaptor.getValue();
        String targetSql = wrapper.getTargetSql();
        assertThat(wrapper.getParamNameValuePairs().values()).contains(1);
        assertThat(targetSql).containsIgnoringCase("ORDER BY").containsIgnoringCase("submit_date");
    }

    @Test
    void approve_submitNotFound_throwsRe40003_andNeverTouchesScoreOrUpdate() {
        when(reSubmitMapper.selectById(999L)).thenReturn(null);

        assertThatThrownBy(() -> reReviewService.approve(999L, new BigDecimal("10"), "ok", "E001"))
                .isInstanceOf(BizException.class)
                .satisfies(ex -> {
                    BizException bizEx = (BizException) ex;
                    assertThat(bizEx.getCode()).isEqualTo("RE-40003");
                    assertThat(bizEx.getMessage()).isEqualTo("提交记录不存在");
                });

        verify(reScoreMapper, never()).insert(any(ReScore.class));
        verify(reSubmitMapper, never()).updateById(any(ReSubmit.class));
    }

    @Test
    void approve_submitterSameAsReviewer_throwsRe40008_beforeScoreOrUpdate() {
        ReSubmit existing = existingSubmit(9L, 100L, "1.1", new BigDecimal("35"));
        existing.setSubmitterId("E008");
        when(reSubmitMapper.selectById(9L)).thenReturn(existing);

        assertThatThrownBy(() -> reReviewService.approve(9L, new BigDecimal("10"), "自审", "E008"))
                .isInstanceOf(BizException.class)
                .satisfies(ex -> {
                    BizException bizEx = (BizException) ex;
                    assertThat(bizEx.getCode()).isEqualTo("RE-40008");
                    assertThat(bizEx.getMessage()).isEqualTo("禁止审核本人提交的记录");
                });

        verify(reScoreMapper, never()).selectList(any());
        verify(reScoreMapper, never()).insert(any(ReScore.class));
        verify(reSubmitMapper, never()).updateById(any(ReSubmit.class));
    }

    @Test
    void approve_scoreExceedsMax_throwsRe40004_andDoesNotInsertScore_andDoesNotUpdateStatus() {
        ReSubmit existing = existingSubmit(10L, 100L, "1.1", new BigDecimal("35"));
        when(reSubmitMapper.selectById(10L)).thenReturn(existing);
        when(reScoreMapper.selectList(any())).thenReturn(List.of(score(new BigDecimal("30"))));

        assertThatThrownBy(() -> reReviewService.approve(10L, new BigDecimal("10"), "超限", "E002"))
                .isInstanceOf(BizException.class)
                .satisfies(ex -> {
                    BizException bizEx = (BizException) ex;
                    assertThat(bizEx.getCode()).isEqualTo("RE-40004");
                    // 累计30 + 本次10 = 40 > 上限35，允许最多再计 5 分
                    assertThat(bizEx.getMessage()).isEqualTo("考核项[1.1]累计得分已达30分，上限35分，本次最多可计5分");
                });

        // 超限必须直接短路：不落 RE_SCORE，也不推进 RE_SUBMIT 状态
        verify(reScoreMapper, never()).insert(any(ReScore.class));
        verify(reSubmitMapper, never()).updateById(any(ReSubmit.class));
    }

    @Test
    void approve_withinLimit_insertsScore_andUpdatesStatus2WithReviewerAndDate() {
        ReSubmit existing = existingSubmit(11L, 100L, "1.1", new BigDecimal("35"));
        when(reSubmitMapper.selectById(11L)).thenReturn(existing);
        when(reScoreMapper.selectList(any())).thenReturn(List.of(score(new BigDecimal("20"))));

        reReviewService.approve(11L, new BigDecimal("10"), "通过", "E003");

        int currentYear = Year.now().getValue();
        verify(reScoreMapper).insert(ArgumentMatchers.<ReScore>argThat(sc ->
                sc.getOrgId().equals(100L)
                        && sc.getSubmitId().equals(11L)
                        && "1.1".equals(sc.getItemCode())
                        && sc.getScoreYear().equals(currentYear)
                        && sc.getFinalScore().compareTo(new BigDecimal("10")) == 0
                        && "通过".equals(sc.getRemark())));

        verify(reSubmitMapper).updateById(ArgumentMatchers.<ReSubmit>argThat(u ->
                u.getId().equals(11L)
                        && u.getStatus().equals(2)
                        && "通过".equals(u.getReviewFeedback())
                        && "E003".equals(u.getReviewerId())
                        && u.getReviewDate() != null));
    }

    @Test
    void approve_noMaxScoreConfigured_fallsBackTo100() {
        ReSubmit existing = existingSubmit(12L, 100L, "1.1", null);
        when(reSubmitMapper.selectById(12L)).thenReturn(existing);
        when(reScoreMapper.selectList(any())).thenReturn(List.of());

        // maxScore 未配置，回退到 100：累计0 + 本次60 = 60 <= 100，不应超限
        reReviewService.approve(12L, new BigDecimal("60"), "ok", "E004");

        verify(reScoreMapper).insert(any(ReScore.class));
        verify(reSubmitMapper).updateById(any(ReSubmit.class));
    }

    @Test
    void approve_scoreNullOrZero_skipsScoreInsertion_butStillAdvancesStatus() {
        ReSubmit existing = existingSubmit(13L, 100L, "1.1", new BigDecimal("35"));
        when(reSubmitMapper.selectById(13L)).thenReturn(existing);

        reReviewService.approve(13L, null, "无需评分", "E005");

        verify(reScoreMapper, never()).insert(any(ReScore.class));
        verify(reScoreMapper, never()).selectList(any());
        verify(reSubmitMapper).updateById(ArgumentMatchers.<ReSubmit>argThat(u ->
                u.getId().equals(13L) && u.getStatus().equals(2)));
    }

    @Test
    void approve_scoreZero_skipsScoreInsertion_butStillAdvancesStatus() {
        // 防边界盲区：score=BigDecimal.ZERO（非 null）时，score.compareTo(BigDecimal.ZERO) > 0 应为 false，
        // 跳过评分落库分支；若该判断被误改为 >= 0，本用例会因 reScoreMapper.insert 被调用而失败
        ReSubmit existing = existingSubmit(14L, 100L, "1.1", new BigDecimal("35"));
        when(reSubmitMapper.selectById(14L)).thenReturn(existing);

        reReviewService.approve(14L, BigDecimal.ZERO, "免于评分", "E007");

        verify(reScoreMapper, never()).insert(any(ReScore.class));
        verify(reScoreMapper, never()).selectList(any());
        verify(reSubmitMapper).updateById(ArgumentMatchers.<ReSubmit>argThat(u ->
                u.getId().equals(14L) && u.getStatus().equals(2)));
    }

    @Test
    void reject_updatesStatus3_withFeedbackReviewerAndDate() {
        ReSubmit existing = existingSubmit(20L, 100L, "1.1", new BigDecimal("35"));
        existing.setStatus(2);
        existing.setSubmitterId("E_OTHER");
        when(reSubmitMapper.selectById(20L)).thenReturn(existing);

        reReviewService.reject(20L, "材料不齐", "E006");

        verify(reSubmitMapper).updateById(ArgumentMatchers.<ReSubmit>argThat(u ->
                u.getId().equals(20L)
                        && u.getStatus().equals(3)
                        && "材料不齐".equals(u.getReviewFeedback())
                        && "E006".equals(u.getReviewerId())
                        && u.getReviewDate() != null));
    }

    @Test
    void reject_submitterSameAsReviewer_throwsRe40008_beforeUpdate() {
        ReSubmit existing = existingSubmit(21L, 100L, "1.1", new BigDecimal("35"));
        existing.setSubmitterId("E009");
        when(reSubmitMapper.selectById(21L)).thenReturn(existing);

        assertThatThrownBy(() -> reReviewService.reject(21L, "自审驳回", "E009"))
                .isInstanceOf(BizException.class)
                .satisfies(ex -> {
                    BizException bizEx = (BizException) ex;
                    assertThat(bizEx.getCode()).isEqualTo("RE-40008");
                    assertThat(bizEx.getMessage()).isEqualTo("禁止审核本人提交的记录");
                });

        verify(reSubmitMapper, never()).updateById(any(ReSubmit.class));
    }

    @Test
    void reject_submitNotFound_preservesSilentNoopUpdateContract() {
        when(reSubmitMapper.selectById(22L)).thenReturn(null);

        reReviewService.reject(22L, "不存在记录", "E010");

        verify(reSubmitMapper).updateById(ArgumentMatchers.<ReSubmit>argThat(u ->
                u.getId().equals(22L)
                        && u.getStatus().equals(3)
                        && "E010".equals(u.getReviewerId())));
    }

    @Test
    void getPreview_delegatesToMapperSelectById() {
        ReSubmit submit = new ReSubmit();
        submit.setId(30L);
        when(reSubmitMapper.selectById(30L)).thenReturn(submit);

        ReSubmit result = reReviewService.getPreview(30L);

        assertThat(result).isSameAs(submit);
    }
}
