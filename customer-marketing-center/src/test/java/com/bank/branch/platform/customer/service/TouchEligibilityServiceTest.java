package com.bank.branch.platform.customer.service;

import com.bank.branch.platform.common.web.exception.BizException;
import com.bank.branch.platform.customer.entity.CustMaster;
import com.bank.branch.platform.customer.entity.CustTag;
import com.bank.branch.platform.customer.entity.CustTagRel;
import com.bank.branch.platform.customer.entity.TouchLimitRule;
import com.bank.branch.platform.customer.mapper.CustMasterMapper;
import com.bank.branch.platform.customer.mapper.CustTagMapper;
import com.bank.branch.platform.customer.mapper.CustTagRelMapper;
import com.bank.branch.platform.customer.mapper.TouchEligibilityMapper;
import com.bank.branch.platform.customer.mapper.TouchLimitRuleMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/** 触达资格规则的 TDD 红测。 */
@ExtendWith(MockitoExtension.class)
class TouchEligibilityServiceTest {

    @Mock
    private CustMasterMapper masterMapper;
    @Mock
    private CustTagRelMapper tagRelMapper;
    @Mock
    private CustTagMapper tagMapper;
    @Mock
    private TouchLimitRuleMapper ruleMapper;
    @Mock
    private TouchEligibilityMapper eligibilityMapper;

    private TouchEligibilityService service;

    @BeforeEach
    void setUp() {
        service = new TouchEligibilityService(masterMapper, tagRelMapper, tagMapper,
                ruleMapper, eligibilityMapper);
    }

    @Test
    void nullCustomer_shouldKeepOriginalCustomerNotFoundError() {
        when(masterMapper.selectById("missing")).thenReturn(null);

        assertThatThrownBy(() -> service.assertEligible("missing", fixedNow()))
                .isInstanceOf(BizException.class)
                .hasMessageContaining("客户");
    }

    @Test
    void onlyExplicitZero_shouldSkipAllTouchRestrictions() {
        CustMaster customer = customer(0, 1);
        when(masterMapper.selectById("cust-1")).thenReturn(customer);

        assertThatCode(() -> service.assertEligible("cust-1", fixedNow()))
                .doesNotThrowAnyException();
        verifyNoInteractions(tagRelMapper, tagMapper, ruleMapper, eligibilityMapper);
    }

    @Test
    void nullRestriction_shouldBeTreatedAsRestrictedAndRejectOpenedWithoutStockTag() {
        CustMaster customer = customer(null, 1);
        when(masterMapper.selectById("cust-1")).thenReturn(customer);
        when(tagRelMapper.selectByCustId("cust-1")).thenReturn(List.of());

        assertThatThrownBy(() -> service.assertEligible("cust-1", fixedNow()))
                .isInstanceOf(BizException.class)
                .hasMessage("该企业经判定已开户，无法再创建工作日志");
    }

    @Test
    void openedCustomerWithExactValidStockTag_shouldContinueToRuleCheck() {
        CustMaster customer = customer(1, 1);
        when(masterMapper.selectById("cust-1")).thenReturn(customer);
        CustTagRel rel = relation("tag-stock");
        CustTag tag = tag("tag-stock", "存量客户");
        when(tagRelMapper.selectByCustId("cust-1")).thenReturn(List.of(rel));
        when(tagMapper.selectEnabledByIds(eq(List.of("tag-stock")))).thenReturn(List.of(tag));
        when(ruleMapper.selectByTagIds(eq(List.of("tag-stock")))).thenReturn(List.of());
        when(eligibilityMapper.countValidWorklogs(eq("cust-1"),
                eq(LocalDateTime.of(2026, 8, 1, 0, 0)),
                eq(LocalDateTime.of(2026, 9, 1, 0, 0)))).thenReturn(0L);

        assertThatCode(() -> service.assertEligible("cust-1", fixedNow()))
                .doesNotThrowAnyException();
        verify(tagMapper).selectEnabledByIds(eq(List.of("tag-stock")));
        verify(ruleMapper).selectByTagIds(eq(List.of("tag-stock")));
        verify(tagMapper, never()).selectById(anyString());
        verify(ruleMapper, never()).selectByTagId(anyString());
    }

    @Test
    void accountOpenedWithInvalidStockTag_shouldStillReject() {
        CustMaster customer = customer(1, 1);
        when(masterMapper.selectById("cust-1")).thenReturn(customer);
        CustTagRel rel = relation("tag-stock");
        CustTag tag = tag("tag-stock", "存量客户");
        tag.setApprovalStatus("PENDING");
        when(tagRelMapper.selectByCustId("cust-1")).thenReturn(List.of(rel));
        when(tagMapper.selectEnabledByIds(eq(List.of("tag-stock")))).thenReturn(List.of(tag));

        assertThatThrownBy(() -> service.assertEligible("cust-1", fixedNow()))
                .isInstanceOf(BizException.class)
                .hasMessage("该企业经判定已开户，无法再创建工作日志");
        verify(tagMapper, never()).selectById(anyString());
        verify(ruleMapper, never()).selectByTagId(anyString());
    }

    @Test
    void nullRelationActive_shouldRemainCompatibleWithLegacyEffectiveRelation() {
        CustMaster customer = customer(1, 1);
        when(masterMapper.selectById("cust-1")).thenReturn(customer);
        CustTagRel rel = relation("tag-stock");
        rel.setActive(null);
        when(tagRelMapper.selectByCustId("cust-1")).thenReturn(List.of(rel));
        when(tagMapper.selectEnabledByIds(eq(List.of("tag-stock"))))
                .thenReturn(List.of(tag("tag-stock", "存量客户")));
        when(ruleMapper.selectByTagIds(eq(List.of("tag-stock")))).thenReturn(List.of());
        when(eligibilityMapper.countValidWorklogs(eq("cust-1"),
                eq(LocalDateTime.of(2026, 8, 1, 0, 0)),
                eq(LocalDateTime.of(2026, 9, 1, 0, 0)))).thenReturn(0L);

        assertThatCode(() -> service.assertEligible("cust-1", fixedNow()))
                .doesNotThrowAnyException();
        verify(tagMapper, never()).selectById(anyString());
        verify(ruleMapper, never()).selectByTagId(anyString());
    }

    @Test
    void nullApprovalStatus_shouldRemainCompatibleWithLegacyEffectiveTag() {
        CustMaster customer = customer(1, 1);
        when(masterMapper.selectById("cust-1")).thenReturn(customer);
        CustTagRel rel = relation("tag-stock");
        CustTag tag = tag("tag-stock", "存量客户");
        tag.setApprovalStatus(null);
        when(tagRelMapper.selectByCustId("cust-1")).thenReturn(List.of(rel));
        when(tagMapper.selectEnabledByIds(eq(List.of("tag-stock")))).thenReturn(List.of(tag));
        when(ruleMapper.selectByTagIds(eq(List.of("tag-stock")))).thenReturn(List.of());
        when(eligibilityMapper.countValidWorklogs(eq("cust-1"),
                eq(LocalDateTime.of(2026, 8, 1, 0, 0)),
                eq(LocalDateTime.of(2026, 9, 1, 0, 0)))).thenReturn(0L);

        assertThatCode(() -> service.assertEligible("cust-1", fixedNow()))
                .doesNotThrowAnyException();
    }

    @ParameterizedTest(name = "{0} natural boundary")
    @MethodSource("cycleBoundaries")
    void eachCycle_shouldCountAtItsNaturalBoundary(String cycleUnit,
                                                     LocalDateTime expectedStart,
                                                     LocalDateTime expectedEnd) {
        CustMaster customer = customer(1, 0);
        when(masterMapper.selectById("cust-1")).thenReturn(customer);
        CustTagRel rel = relation("tag-" + cycleUnit);
        CustTag tag = tag(rel.getTagId(), "规则标签-" + cycleUnit);
        TouchLimitRule rule = new TouchLimitRule();
        rule.setTagId(rel.getTagId());
        rule.setCycleUnit(cycleUnit);
        rule.setMaxTouches(9999);
        when(tagRelMapper.selectByCustId("cust-1")).thenReturn(List.of(rel));
        when(tagMapper.selectEnabledByIds(eq(List.of(rel.getTagId())))).thenReturn(List.of(tag));
        when(ruleMapper.selectByTagIds(eq(List.of(rel.getTagId())))).thenReturn(List.of(rule));
        when(eligibilityMapper.countValidWorklogs(eq("cust-1"),
                eq(expectedStart), eq(expectedEnd))).thenReturn(0L);

        assertThatCode(() -> service.assertEligible("cust-1", fixedNow()))
                .doesNotThrowAnyException();
        verify(eligibilityMapper).countValidWorklogs("cust-1",
                expectedStart, expectedEnd);
        verify(tagMapper, never()).selectById(anyString());
        verify(ruleMapper, never()).selectByTagId(anyString());
    }

    @Test
    void absentRule_shouldUseMonthlyDefaultFiveAndRejectAtLimit() {
        CustMaster customer = customer(1, 0);
        when(masterMapper.selectById("cust-1")).thenReturn(customer);
        CustTagRel rel = relation("tag-default");
        when(tagRelMapper.selectByCustId("cust-1")).thenReturn(List.of(rel));
        when(tagMapper.selectEnabledByIds(eq(List.of("tag-default"))))
                .thenReturn(List.of(tag("tag-default", "普通标签")));
        when(ruleMapper.selectByTagIds(eq(List.of("tag-default")))).thenReturn(List.of());
        when(eligibilityMapper.countValidWorklogs(eq("cust-1"),
                eq(LocalDateTime.of(2026, 8, 1, 0, 0)),
                eq(LocalDateTime.of(2026, 9, 1, 0, 0)))).thenReturn(5L);

        assertThatThrownBy(() -> service.assertEligible("cust-1", fixedNow()))
                .isInstanceOf(BizException.class)
                .hasMessageContaining("触达");
        verify(ruleMapper, never()).selectByTagId(anyString());
        verify(tagMapper, never()).selectById(anyString());
    }

    @Test
    void multipleTags_shouldRejectWhenAnyTagReachesItsOwnLimit() {
        CustMaster customer = customer(1, 0);
        when(masterMapper.selectById("cust-1")).thenReturn(customer);
        CustTagRel first = relation("tag-first");
        CustTagRel second = relation("tag-second");
        when(tagRelMapper.selectByCustId("cust-1")).thenReturn(List.of(first, second));
        when(tagMapper.selectEnabledByIds(eq(List.of("tag-first", "tag-second"))))
                .thenReturn(List.of(tag("tag-first", "标签一"), tag("tag-second", "标签二")));
        TouchLimitRule firstRule = rule("tag-first", "MONTH", 5);
        TouchLimitRule secondRule = rule("tag-second", "DAY", 2);
        when(ruleMapper.selectByTagIds(eq(List.of("tag-first", "tag-second"))))
                .thenReturn(List.of(firstRule, secondRule));
        when(eligibilityMapper.countValidWorklogs(eq("cust-1"),
                eq(LocalDateTime.of(2026, 8, 1, 0, 0)),
                eq(LocalDateTime.of(2026, 9, 1, 0, 0)))).thenReturn(0L);
        when(eligibilityMapper.countValidWorklogs(eq("cust-1"),
                eq(LocalDateTime.of(2026, 8, 26, 0, 0)),
                eq(LocalDateTime.of(2026, 8, 27, 0, 0)))).thenReturn(2L);

        assertThatThrownBy(() -> service.assertEligible("cust-1", fixedNow()))
                .isInstanceOf(BizException.class)
                .hasMessageContaining("触达");
        verify(tagMapper, never()).selectById(anyString());
        verify(ruleMapper, never()).selectByTagId(anyString());
    }

    @Test
    void multipleTagsWithSameCycle_shouldReuseOneWorklogCount() {
        CustMaster customer = customer(1, 0);
        when(masterMapper.selectById("cust-1")).thenReturn(customer);
        CustTagRel first = relation("tag-first");
        CustTagRel second = relation("tag-second");
        when(tagRelMapper.selectByCustId("cust-1")).thenReturn(List.of(first, second));
        when(tagMapper.selectEnabledByIds(eq(List.of("tag-first", "tag-second"))))
                .thenReturn(List.of(tag("tag-first", "标签一"), tag("tag-second", "标签二")));
        when(ruleMapper.selectByTagIds(eq(List.of("tag-first", "tag-second"))))
                .thenReturn(List.of(rule("tag-first", "MONTH", 5), rule("tag-second", "MONTH", 8)));
        when(eligibilityMapper.countValidWorklogs(eq("cust-1"),
                eq(LocalDateTime.of(2026, 8, 1, 0, 0)),
                eq(LocalDateTime.of(2026, 9, 1, 0, 0)))).thenReturn(0L);

        assertThatCode(() -> service.assertEligible("cust-1", fixedNow()))
                .doesNotThrowAnyException();
        verify(eligibilityMapper, times(1)).countValidWorklogs("cust-1",
                LocalDateTime.of(2026, 8, 1, 0, 0),
                LocalDateTime.of(2026, 9, 1, 0, 0));
    }

    private static Stream<Arguments> cycleBoundaries() {
        return Stream.of(
                Arguments.of("DAY", LocalDateTime.of(2026, 8, 26, 0, 0),
                        LocalDateTime.of(2026, 8, 27, 0, 0)),
                Arguments.of("WEEK", LocalDateTime.of(2026, 8, 24, 0, 0),
                        LocalDateTime.of(2026, 8, 31, 0, 0)),
                Arguments.of("MONTH", LocalDateTime.of(2026, 8, 1, 0, 0),
                        LocalDateTime.of(2026, 9, 1, 0, 0)),
                Arguments.of("QUARTER", LocalDateTime.of(2026, 7, 1, 0, 0),
                        LocalDateTime.of(2026, 10, 1, 0, 0)),
                Arguments.of("YEAR", LocalDateTime.of(2026, 1, 1, 0, 0),
                        LocalDateTime.of(2027, 1, 1, 0, 0))
        );
    }

    private static LocalDateTime fixedNow() {
        return LocalDateTime.of(2026, 8, 26, 15, 14, 13);
    }

    private static CustMaster customer(Integer restricted, Integer opened) {
        CustMaster customer = new CustMaster();
        customer.setId("cust-1");
        customer.setUnifiedCreditCode("91310000MA1FL4LL3X");
        customer.setTouchRestricted(restricted);
        customer.setIsAccountOpened(opened);
        return customer;
    }

    private static CustTagRel relation(String tagId) {
        CustTagRel relation = new CustTagRel();
        relation.setCustId("cust-1");
        relation.setTagId(tagId);
        relation.setActive(1);
        relation.setEffectiveTime(LocalDateTime.of(2026, 1, 1, 0, 0));
        relation.setExpiredTime(null);
        return relation;
    }

    private static CustTag tag(String id, String name) {
        CustTag tag = new CustTag();
        tag.setId(id);
        tag.setTagName(name);
        tag.setStatus("ACTIVE");
        tag.setApprovalStatus("APPROVED");
        tag.setDeleted(0);
        tag.setExpiresAt(LocalDate.of(2026, 12, 31));
        return tag;
    }

    private static TouchLimitRule rule(String tagId, String cycleUnit, int maxTouches) {
        TouchLimitRule rule = new TouchLimitRule();
        rule.setTagId(tagId);
        rule.setCycleUnit(cycleUnit);
        rule.setMaxTouches(maxTouches);
        return rule;
    }
}
