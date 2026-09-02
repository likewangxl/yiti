package com.bank.branch.platform.customer.service;

import com.bank.branch.platform.common.web.PageResult;
import com.bank.branch.platform.common.web.exception.BizException;
import com.bank.branch.platform.customer.dto.req.TouchLimitRuleUpdateReqDTO;
import com.bank.branch.platform.customer.dto.resp.TouchLimitRuleRespDTO;
import com.bank.branch.platform.customer.entity.CustTag;
import com.bank.branch.platform.customer.entity.TouchLimitRule;
import com.bank.branch.platform.customer.enums.TouchLimitCycleUnit;
import com.bank.branch.platform.customer.mapper.CustTagMapper;
import com.bank.branch.platform.customer.mapper.TouchLimitRuleMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 触达周期规则服务测试，先覆盖分页默认值、批量查询及标签存在性校验。
 */
@ExtendWith(MockitoExtension.class)
class TouchLimitRuleServiceTest {

    @Mock
    private TouchLimitRuleMapper ruleMapper;

    @Mock
    private CustTagMapper tagMapper;

    @InjectMocks
    private TouchLimitRuleService service;

    @Test
    void listPage_shouldFillMonthFiveDefaultsAndLoadRulesInOneBatch() {
        CustTag first = tag("tag-1", "重点客户", "ACTIVE", "APPROVED");
        CustTag second = tag("tag-2", "普通客户", "DISABLED", "APPROVED");
        TouchLimitRule configured = rule("rule-1", "tag-2", "WEEK", 3);
        when(tagMapper.selectPage("客户", null, 0, 20)).thenReturn(List.of(first, second));
        when(tagMapper.countPage("客户", null)).thenReturn(2L);
        when(ruleMapper.selectByTagIds(List.of("tag-1", "tag-2"))).thenReturn(List.of(configured));

        PageResult<TouchLimitRuleRespDTO> result = service.listPage("客户", 1, 20);

        assertThat(result.getTotal()).isEqualTo(2L);
        assertThat(result.getRecords()).extracting(TouchLimitRuleRespDTO::getCycleUnit)
                .containsExactly(TouchLimitCycleUnit.MONTH.name(), TouchLimitCycleUnit.WEEK.name());
        assertThat(result.getRecords()).extracting(TouchLimitRuleRespDTO::getMaxTouches)
                .containsExactly(5, 3);
        assertThat(result.getRecords()).extracting(TouchLimitRuleRespDTO::getTagName)
                .containsExactly("重点客户", "普通客户");
        verify(ruleMapper).selectByTagIds(List.of("tag-1", "tag-2"));
    }

    @Test
    void listPage_shouldNotQueryRulesWhenTagPageIsEmpty() {
        when(tagMapper.selectPage(null, null, 0, 20)).thenReturn(List.of());
        when(tagMapper.countPage(null, null)).thenReturn(0L);

        PageResult<TouchLimitRuleRespDTO> result = service.listPage(null, 1, 20);

        assertThat(result.getRecords()).isEmpty();
        verify(ruleMapper, never()).selectByTagIds(any());
    }

    @Test
    void updateRule_shouldInsertWithAuditFieldsWhenNoRuleExists() {
        when(tagMapper.selectById("tag-1")).thenReturn(tag("tag-1", "重点客户", "ACTIVE", "APPROVED"));
        when(ruleMapper.selectByTagId("tag-1")).thenReturn(null);
        when(ruleMapper.insert(any(TouchLimitRule.class))).thenReturn(1);
        TouchLimitRuleUpdateReqDTO request = request("QUARTER", 7);

        service.updateRule("tag-1", request, "E10001");

        verify(ruleMapper).insert(argThat((TouchLimitRule rule) -> "tag-1".equals(rule.getTagId())
                && "QUARTER".equals(rule.getCycleUnit())
                && Integer.valueOf(7).equals(rule.getMaxTouches())
                && "E10001".equals(rule.getCreatedBy())
                && "E10001".equals(rule.getUpdatedBy())
                && rule.getId() != null));
    }

    @Test
    void updateRule_shouldUpdateExistingRule() {
        when(tagMapper.selectById("tag-1")).thenReturn(tag("tag-1", "重点客户", "ACTIVE", "APPROVED"));
        TouchLimitRule existing = rule("rule-1", "tag-1", "MONTH", 5);
        existing.setCreatedBy("E00001");
        when(ruleMapper.selectByTagId("tag-1")).thenReturn(existing);
        when(ruleMapper.updateById(any(TouchLimitRule.class))).thenReturn(1);

        service.updateRule("tag-1", request("YEAR", 9), "E10001");

        verify(ruleMapper).updateById(argThat((TouchLimitRule rule) -> "rule-1".equals(rule.getId())
                && "YEAR".equals(rule.getCycleUnit())
                && Integer.valueOf(9).equals(rule.getMaxTouches())
                && "E00001".equals(rule.getCreatedBy())
                && "E10001".equals(rule.getUpdatedBy())));
    }

    @Test
    void updateRule_shouldRejectDeletedOrMissingTag() {
        when(tagMapper.selectById("missing")).thenReturn(null);

        assertThatThrownBy(() -> service.updateRule("missing", request("DAY", 1), "E10001"))
                .isInstanceOf(BizException.class)
                .hasFieldOrPropertyWithValue("code", "CUST-40401");

        verify(ruleMapper, never()).insert(any(TouchLimitRule.class));
        verify(ruleMapper, never()).updateById(any(TouchLimitRule.class));
    }

    @Test
    void updateRule_shouldRejectInvalidCycleAndMaxTouches() {
        assertThatThrownBy(() -> service.updateRule("tag-1", request("YEARLY", 1), "E10001"))
                .isInstanceOf(BizException.class);
        assertThatThrownBy(() -> service.updateRule("tag-1", request("YEAR", 0), "E10001"))
                .isInstanceOf(BizException.class);
        assertThatThrownBy(() -> service.updateRule("tag-1", request("YEAR", 10000), "E10001"))
                .isInstanceOf(BizException.class);

        verify(ruleMapper, never()).insert(any(TouchLimitRule.class));
        verify(ruleMapper, never()).updateById(any(TouchLimitRule.class));
    }

    private static CustTag tag(String id, String name, String status, String approvalStatus) {
        CustTag tag = new CustTag();
        tag.setId(id);
        tag.setTagName(name);
        tag.setStatus(status);
        tag.setApprovalStatus(approvalStatus);
        tag.setDeleted(0);
        return tag;
    }

    private static TouchLimitRule rule(String id, String tagId, String cycleUnit, int maxTouches) {
        TouchLimitRule rule = new TouchLimitRule();
        rule.setId(id);
        rule.setTagId(tagId);
        rule.setCycleUnit(cycleUnit);
        rule.setMaxTouches(maxTouches);
        return rule;
    }

    private static TouchLimitRuleUpdateReqDTO request(String cycleUnit, int maxTouches) {
        TouchLimitRuleUpdateReqDTO request = new TouchLimitRuleUpdateReqDTO();
        request.setCycleUnit(cycleUnit);
        request.setMaxTouches(maxTouches);
        return request;
    }
}
