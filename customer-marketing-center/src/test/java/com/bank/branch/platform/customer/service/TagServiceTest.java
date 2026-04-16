package com.bank.branch.platform.customer.service;

import com.bank.branch.platform.common.web.PageResult;
import com.bank.branch.platform.common.web.exception.BizException;
import com.bank.branch.platform.customer.entity.CustTag;
import com.bank.branch.platform.customer.enums.CustomerErrorCode;
import com.bank.branch.platform.customer.enums.TagStatus;
import com.bank.branch.platform.customer.mapper.CustTagMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * TagService 单元测试（TDD RED 阶段）
 * 使用 MockitoExtension，不需要 Spring 上下文。
 */
@ExtendWith(MockitoExtension.class)
class TagServiceTest {

    @Mock
    private CustTagMapper tagMapper;

    @InjectMocks
    private TagService tagService;

    // ==================== createTag ====================

    @Test
    void createTag_shouldInsertAndReturnTag() {
        // given: 名称和编码均不重复
        when(tagMapper.selectByTagName("VIP客户")).thenReturn(null);
        when(tagMapper.selectByTagCode("VIP_CUSTOMER")).thenReturn(null);
        when(tagMapper.insert(any(CustTag.class))).thenReturn(1);

        // when
        CustTag result = tagService.createTag("VIP客户", "VIP_CUSTOMER", "高净值客户", "价值类", 10, "E001");

        // then
        assertThat(result).isNotNull();
        assertThat(result.getTagName()).isEqualTo("VIP客户");
        assertThat(result.getTagCode()).isEqualTo("VIP_CUSTOMER");
        assertThat(result.getDescription()).isEqualTo("高净值客户");
        assertThat(result.getTagCategory()).isEqualTo("价值类");
        assertThat(result.getTagPriority()).isEqualTo(10);
        assertThat(result.getStatus()).isEqualTo(TagStatus.ACTIVE.getCode());
        assertThat(result.getCreatedBy()).isEqualTo("E001");
        assertThat(result.getId()).isNotNull();
        verify(tagMapper).insert(any(CustTag.class));
    }

    @Test
    void createTag_shouldThrowWhenNameDuplicate() {
        // given: 同名标签已存在
        CustTag existing = new CustTag();
        existing.setId("existing-id");
        existing.setTagName("VIP客户");
        when(tagMapper.selectByTagName("VIP客户")).thenReturn(existing);

        // when/then
        assertThatThrownBy(() -> tagService.createTag("VIP客户", "NEW_CODE", null, null, null, "E001"))
                .isInstanceOf(BizException.class)
                .hasFieldOrPropertyWithValue("code", CustomerErrorCode.TAG_NAME_DUPLICATE.getCode());

        verify(tagMapper, never()).insert(any());
    }

    @Test
    void createTag_shouldThrowWhenCodeDuplicate() {
        // given: 同编码标签已存在
        when(tagMapper.selectByTagName("新标签")).thenReturn(null);
        CustTag existing = new CustTag();
        existing.setId("existing-id");
        existing.setTagCode("VIP_CUSTOMER");
        when(tagMapper.selectByTagCode("VIP_CUSTOMER")).thenReturn(existing);

        // when/then
        assertThatThrownBy(() -> tagService.createTag("新标签", "VIP_CUSTOMER", null, null, null, "E001"))
                .isInstanceOf(BizException.class)
                .hasFieldOrPropertyWithValue("code", CustomerErrorCode.TAG_CODE_DUPLICATE.getCode());

        verify(tagMapper, never()).insert(any());
    }

    // ==================== updateTag ====================

    @Test
    void updateTag_shouldThrowWhenCodeChanged() {
        // given: 已有标签编码为 VIP_CUSTOMER，更新请求修改为 NEW_CODE
        CustTag existing = new CustTag();
        existing.setId("tag-001");
        existing.setTagName("VIP客户");
        existing.setTagCode("VIP_CUSTOMER");
        when(tagMapper.selectById("tag-001")).thenReturn(existing);

        // when/then: 尝试修改 tagCode 应抛出异常
        assertThatThrownBy(() -> tagService.updateTag("tag-001", "VIP客户", "NEW_CODE", null, null, null, "E001"))
                .isInstanceOf(BizException.class)
                .hasFieldOrPropertyWithValue("code", CustomerErrorCode.TAG_CODE_IMMUTABLE.getCode());

        verify(tagMapper, never()).updateById(any());
    }

    @Test
    void updateTag_shouldSucceedWhenCodeUnchanged() {
        // given: 更新时 tagCode 保持不变
        CustTag existing = new CustTag();
        existing.setId("tag-001");
        existing.setTagName("VIP客户");
        existing.setTagCode("VIP_CUSTOMER");
        when(tagMapper.selectById("tag-001")).thenReturn(existing);
        when(tagMapper.selectByTagName("新VIP")).thenReturn(null);
        when(tagMapper.updateById(any(CustTag.class))).thenReturn(1);

        // when
        CustTag result = tagService.updateTag("tag-001", "新VIP", "VIP_CUSTOMER", "高净值", "价值类", 20, "E001");

        // then
        assertThat(result).isNotNull();
        assertThat(result.getTagName()).isEqualTo("新VIP");
        verify(tagMapper).updateById(any(CustTag.class));
    }

    @Test
    void updateTag_shouldThrowWhenNameDuplicateWithOther() {
        // given: 名称被另一个标签占用
        CustTag existing = new CustTag();
        existing.setId("tag-001");
        existing.setTagName("VIP客户");
        existing.setTagCode("VIP_CUSTOMER");
        when(tagMapper.selectById("tag-001")).thenReturn(existing);

        CustTag otherTag = new CustTag();
        otherTag.setId("tag-002"); // 不同 id
        otherTag.setTagName("重复名称");
        when(tagMapper.selectByTagName("重复名称")).thenReturn(otherTag);

        // when/then
        assertThatThrownBy(() -> tagService.updateTag("tag-001", "重复名称", "VIP_CUSTOMER", null, null, null, "E001"))
                .isInstanceOf(BizException.class)
                .hasFieldOrPropertyWithValue("code", CustomerErrorCode.TAG_NAME_DUPLICATE.getCode());

        verify(tagMapper, never()).updateById(any());
    }

    // ==================== toggleStatus ====================

    @Test
    void toggleStatus_shouldDisableActiveTag() {
        // given
        CustTag existing = new CustTag();
        existing.setId("tag-001");
        existing.setStatus(TagStatus.ACTIVE.getCode());
        when(tagMapper.selectById("tag-001")).thenReturn(existing);
        when(tagMapper.updateById(any(CustTag.class))).thenReturn(1);

        // when
        tagService.toggleStatus("tag-001", TagStatus.DISABLED.getCode(), "E001");

        // then: 验证 updateById 被调用，状态已更改
        verify(tagMapper).updateById(any(CustTag.class));
    }

    @Test
    void toggleStatus_shouldThrowWhenNotFound() {
        // given: 标签不存在
        when(tagMapper.selectById("not-exist")).thenReturn(null);

        // when/then
        assertThatThrownBy(() -> tagService.toggleStatus("not-exist", TagStatus.DISABLED.getCode(), "E001"))
                .isInstanceOf(BizException.class)
                .hasFieldOrPropertyWithValue("code", CustomerErrorCode.TAG_NOT_FOUND.getCode());

        verify(tagMapper, never()).updateById(any());
    }

    // ==================== getById ====================

    @Test
    void getById_shouldReturnTag() {
        // given
        CustTag tag = new CustTag();
        tag.setId("tag-001");
        tag.setTagName("VIP客户");
        when(tagMapper.selectById("tag-001")).thenReturn(tag);

        // when
        CustTag result = tagService.getById("tag-001");

        // then
        assertThat(result).isNotNull();
        assertThat(result.getId()).isEqualTo("tag-001");
        assertThat(result.getTagName()).isEqualTo("VIP客户");
    }

    @Test
    void getById_shouldThrowWhenNotFound() {
        // given: 标签不存在
        when(tagMapper.selectById("not-exist")).thenReturn(null);

        // when/then
        assertThatThrownBy(() -> tagService.getById("not-exist"))
                .isInstanceOf(BizException.class)
                .hasFieldOrPropertyWithValue("code", CustomerErrorCode.TAG_NOT_FOUND.getCode());
    }

    // ==================== listEnabled ====================

    @Test
    void listEnabled_shouldReturnActiveTagsOnly() {
        // given
        CustTag tag1 = new CustTag();
        tag1.setId("tag-001");
        tag1.setStatus(TagStatus.ACTIVE.getCode());
        CustTag tag2 = new CustTag();
        tag2.setId("tag-002");
        tag2.setStatus(TagStatus.ACTIVE.getCode());
        when(tagMapper.selectEnabled()).thenReturn(Arrays.asList(tag1, tag2));

        // when
        List<CustTag> result = tagService.listEnabled();

        // then
        assertThat(result).hasSize(2);
        verify(tagMapper).selectEnabled();
    }

    // ==================== listPage ====================

    @Test
    void listPage_shouldCallMapperWithCorrectOffset() {
        // given: pageNo=2, pageSize=10 -> offset = (2-1)*10 = 10
        CustTag tag = new CustTag();
        tag.setId("tag-001");
        when(tagMapper.selectPage(isNull(), isNull(), eq(10), eq(10)))
                .thenReturn(Collections.singletonList(tag));
        when(tagMapper.countPage(isNull(), isNull())).thenReturn(15L);

        // when
        PageResult<CustTag> result = tagService.listPage(null, null, 2, 10);

        // then
        assertThat(result.getPageNo()).isEqualTo(2);
        assertThat(result.getPageSize()).isEqualTo(10);
        assertThat(result.getTotal()).isEqualTo(15L);
        assertThat(result.getRecords()).hasSize(1);
        verify(tagMapper).selectPage(null, null, 10, 10);
    }
}
