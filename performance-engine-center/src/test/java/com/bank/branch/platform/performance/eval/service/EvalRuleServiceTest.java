package com.bank.branch.platform.performance.eval.service;

import com.bank.branch.platform.performance.enums.PerfErrorCode;
import com.bank.branch.platform.performance.eval.entity.EvalRule;
import com.bank.branch.platform.performance.eval.mapper.EvalRuleGroupMapper;
import com.bank.branch.platform.performance.eval.mapper.EvalRuleMapper;
import com.bank.branch.platform.performance.exception.PerfException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class EvalRuleServiceTest {

    @Mock
    private EvalRuleMapper evalRuleMapper;

    @Mock
    private EvalRuleGroupMapper evalRuleGroupMapper;

    @InjectMocks
    private EvalRuleService evalRuleService;

    /** 构造权重之和恰好为 100 的两组参数 */
    private List<EvalRuleService.GroupParam> validGroups() {
        return List.of(
                new EvalRuleService.GroupParam(1, 10L, new BigDecimal("60.00"), 1),
                new EvalRuleService.GroupParam(2, null, new BigDecimal("40.00"), 2)
        );
    }

    @Test
    @DisplayName("创建规则成功：权重之和=100%")
    void create_success() {
        // given
        when(evalRuleMapper.selectByBeEvalTagId(20L)).thenReturn(null);
        when(evalRuleMapper.insert(any(EvalRule.class))).thenReturn(1);
        when(evalRuleGroupMapper.batchInsert(any())).thenReturn(2);

        // when
        EvalRule result = evalRuleService.create("省行行长考核规则", 20L, validGroups());

        // then
        assertThat(result.getRuleName()).isEqualTo("省行行长考核规则");
        assertThat(result.getBeEvalTagId()).isEqualTo(20L);
        assertThat(result.getStatus()).isEqualTo(1);
        verify(evalRuleMapper).insert(any(EvalRule.class));
        verify(evalRuleGroupMapper).batchInsert(any());
    }

    @Test
    @DisplayName("权重之和不等于100%时抛 PERF-40052")
    void create_whenWeightSumInvalid_throws40052() {
        // given：两组权重之和 = 90，不等于 100
        List<EvalRuleService.GroupParam> badGroups = List.of(
                new EvalRuleService.GroupParam(1, 10L, new BigDecimal("50.00"), 1),
                new EvalRuleService.GroupParam(2, null, new BigDecimal("40.00"), 2)
        );

        // when/then
        assertThatThrownBy(() -> evalRuleService.create("测试规则", 20L, badGroups))
                .isInstanceOfSatisfying(PerfException.class,
                        ex -> assertThat(ex.getErrorCode()).isEqualTo(PerfErrorCode.EVAL_RULE_WEIGHT_INVALID));

        // 权重校验在 DB 操作之前，不应触碰 mapper
        verifyNoInteractions(evalRuleMapper);
        verifyNoInteractions(evalRuleGroupMapper);
    }

    @Test
    @DisplayName("被评价人标签已存在规则时抛 PERF-40051")
    void create_whenBeEvalTagIdDuplicate_throws40051() {
        // given：beEvalTagId=20 已存在规则
        EvalRule existing = new EvalRule();
        existing.setRuleId(1L);
        existing.setBeEvalTagId(20L);
        when(evalRuleMapper.selectByBeEvalTagId(20L)).thenReturn(existing);

        // when/then
        assertThatThrownBy(() -> evalRuleService.create("新规则", 20L, validGroups()))
                .isInstanceOfSatisfying(PerfException.class,
                        ex -> assertThat(ex.getErrorCode()).isEqualTo(PerfErrorCode.EVAL_RULE_TAG_EXISTS));

        verify(evalRuleMapper, never()).insert(any(EvalRule.class));
    }
}
