package com.bank.branch.platform.customer.facade;

import com.bank.branch.platform.common.web.exception.BizException;
import com.bank.branch.platform.customer.api.dto.TagDTO;
import com.bank.branch.platform.customer.entity.CustTag;
import com.bank.branch.platform.customer.entity.CustTagRel;
import com.bank.branch.platform.customer.mapper.CustTagMapper;
import com.bank.branch.platform.customer.mapper.CustTagRelMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

/**
 * TagApiImpl 单元测试（TDD Red → Green）
 * 覆盖契约 §3 要求的 6 个方法。
 */
@ExtendWith(MockitoExtension.class)
class TagApiImplTest {

    @Mock
    private CustTagMapper custTagMapper;

    @Mock
    private CustTagRelMapper custTagRelMapper;

    @InjectMocks
    private TagApiImpl tagApiImpl;

    // ==================== listEnabledTags ====================

    @Test
    void listEnabledTags_returnsOnlyEnabledSortedByPriority() {
        List<CustTag> raw = List.of(
                buildTag("t1", "T1", "ACTIVE", 20),
                buildTag("t2", "T2", "ACTIVE", 10)
        );
        when(custTagMapper.selectEnabledSorted()).thenReturn(raw);

        List<TagDTO> list = tagApiImpl.listEnabledTags();

        assertThat(list).hasSize(2);
        assertThat(list.get(0).getId()).isEqualTo("t1");
        assertThat(list.get(1).getId()).isEqualTo("t2");
    }

    @Test
    void listEnabledTags_returnsEmptyWhenNoEnabledTags() {
        when(custTagMapper.selectEnabledSorted()).thenReturn(Collections.emptyList());

        List<TagDTO> list = tagApiImpl.listEnabledTags();

        assertThat(list).isEmpty();
    }

    // ==================== getCustomerTags ====================

    @Test
    void getCustomerTags_returnsOnlyEnabledTags() {
        // custTagRelMapper 返回 3 个关联 tagId
        List<CustTagRel> rels = List.of(
                buildRel("C1", "t1"),
                buildRel("C1", "t2"),
                buildRel("C1", "t3")
        );
        when(custTagRelMapper.selectByCustId("C1")).thenReturn(rels);

        // custTagMapper.selectEnabledByIds 只返回 ACTIVE 的 2 条（t3 是 DISABLED，不返回）
        List<CustTag> enabledTags = List.of(
                buildTag("t1", "T1", "ACTIVE", 10),
                buildTag("t2", "T2", "ACTIVE", 20)
        );
        when(custTagMapper.selectEnabledByIds(List.of("t1", "t2", "t3"))).thenReturn(enabledTags);

        List<TagDTO> result = tagApiImpl.getCustomerTags("C1");

        assertThat(result).hasSize(2);
    }

    @Test
    void getCustomerTags_emptyForNoTags() {
        when(custTagRelMapper.selectByCustId("C_EMPTY")).thenReturn(Collections.emptyList());

        List<TagDTO> result = tagApiImpl.getCustomerTags("C_EMPTY");

        assertThat(result).isEmpty();
    }

    // ==================== batchGetCustomerTags ====================

    @Test
    void batchGetCustomerTags_throwsForOver500() {
        List<String> ids = IntStream.range(0, 501)
                .mapToObj(i -> "C" + i)
                .toList();

        assertThatThrownBy(() -> tagApiImpl.batchGetCustomerTags(ids))
                .isInstanceOf(BizException.class)
                .hasFieldOrPropertyWithValue("code", "COMMON-40000");
    }

    @Test
    void batchGetCustomerTags_returnsMapKeyedByCustId() {
        List<String> custIds = List.of("C1", "C2");

        List<CustTagRel> rels = List.of(
                buildRel("C1", "t1"),
                buildRel("C2", "t2")
        );
        when(custTagRelMapper.selectByCustIds(custIds)).thenReturn(rels);

        List<CustTag> tags = List.of(
                buildTag("t1", "Tag1", "ACTIVE", 10),
                buildTag("t2", "Tag2", "ACTIVE", 20)
        );
        // 两个 tagId 都是 ACTIVE，selectEnabledByIds 返回全部
        when(custTagMapper.selectEnabledByIds(org.mockito.ArgumentMatchers.anyList())).thenReturn(tags);

        Map<String, List<TagDTO>> result = tagApiImpl.batchGetCustomerTags(custIds);

        assertThat(result).hasSize(2);
        assertThat(result.get("C1")).isNotNull().hasSize(1);
        assertThat(result.get("C2")).isNotNull().hasSize(1);
    }

    @Test
    void batchGetCustomerTags_returnsEmptyForEmptyInput() {
        Map<String, List<TagDTO>> result = tagApiImpl.batchGetCustomerTags(Collections.emptyList());

        assertThat(result).isEmpty();
    }

    // ==================== getCustomerIdsByTag ====================

    @Test
    void getCustomerIdsByTag_returnsList() {
        when(custTagRelMapper.selectCustIdsByTagId("t1"))
                .thenReturn(List.of("C1", "C2", "C3"));

        List<String> result = tagApiImpl.getCustomerIdsByTag("t1");

        assertThat(result).containsExactly("C1", "C2", "C3");
    }

    // ==================== isTagNameExists ====================

    @Test
    void isTagNameExists_trueForExisting() {
        when(custTagMapper.countByTagName("VIP")).thenReturn(1L);

        assertThat(tagApiImpl.isTagNameExists("VIP")).isTrue();
    }

    @Test
    void isTagNameExists_falseForNew() {
        when(custTagMapper.countByTagName("NEW_TAG")).thenReturn(0L);

        assertThat(tagApiImpl.isTagNameExists("NEW_TAG")).isFalse();
    }

    // ==================== 辅助方法 ====================

    private CustTag buildTag(String id, String name, String status, int priority) {
        CustTag tag = new CustTag();
        tag.setId(id);
        tag.setTagName(name);
        tag.setStatus(status);
        tag.setTagPriority(priority);
        return tag;
    }

    private CustTagRel buildRel(String custId, String tagId) {
        CustTagRel rel = new CustTagRel();
        rel.setCustId(custId);
        rel.setTagId(tagId);
        return rel;
    }
}
