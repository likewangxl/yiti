package com.bank.branch.platform.customer.service;

import com.bank.branch.platform.auth.api.UserApi;
import com.bank.branch.platform.auth.api.dto.UserDTO;
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
import static org.mockito.ArgumentMatchers.argThat;
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

    @Mock
    private UserApi userApi;

    @InjectMocks
    private TagService tagService;

    // ==================== createTag ====================

    @Test
    void createTag_shouldInsertAndReturnTag() {
        // given: 名称不重复
        when(tagMapper.selectByTagName("VIP客户")).thenReturn(null);
        when(tagMapper.insert(any(CustTag.class))).thenReturn(1);

        // when
        CustTag result = tagService.createTag("VIP客户", "高净值客户", "价值类", 10, "E001");

        // then
        assertThat(result).isNotNull();
        assertThat(result.getTagName()).isEqualTo("VIP客户");
        assertThat(result.getDescription()).isEqualTo("高净值客户");
        assertThat(result.getTagCategory()).isEqualTo("价值类");
        assertThat(result.getTagPriority()).isEqualTo(10);
        assertThat(result.getStatus()).isEqualTo(TagStatus.DISABLED.getCode());
        assertThat(result.getApprovalStatus()).isEqualTo("PENDING");
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
        assertThatThrownBy(() -> tagService.createTag("VIP客户", null, null, null, "E001"))
                .isInstanceOf(BizException.class)
                .hasFieldOrPropertyWithValue("code", CustomerErrorCode.TAG_NAME_DUPLICATE.getCode());

        verify(tagMapper, never()).insert(any(CustTag.class));
    }

    // ==================== updateTag ====================

    @Test
    void updateTag_shouldSucceed() {
        CustTag existing = new CustTag();
        existing.setId("tag-001");
        existing.setTagName("VIP客户");
        when(tagMapper.selectById("tag-001")).thenReturn(existing);
        when(tagMapper.selectByTagName("新VIP")).thenReturn(null);
        when(tagMapper.updateById(any(CustTag.class))).thenReturn(1);

        // when
        CustTag result = tagService.updateTag("tag-001", "新VIP", "高净值", "价值类", 20, "E001");

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
        when(tagMapper.selectById("tag-001")).thenReturn(existing);

        CustTag otherTag = new CustTag();
        otherTag.setId("tag-002"); // 不同 id
        otherTag.setTagName("重复名称");
        when(tagMapper.selectByTagName("重复名称")).thenReturn(otherTag);

        // when/then
        assertThatThrownBy(() -> tagService.updateTag("tag-001", "重复名称", null, null, null, "E001"))
                .isInstanceOf(BizException.class)
                .hasFieldOrPropertyWithValue("code", CustomerErrorCode.TAG_NAME_DUPLICATE.getCode());

        verify(tagMapper, never()).updateById(any(CustTag.class));
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

        verify(tagMapper, never()).updateById(any(CustTag.class));
    }

    @Test
    void toggleStatus_shouldRejectEnablingPendingTag() {
        CustTag tag = new CustTag();
        tag.setId("tag-pending");
        tag.setApprovalStatus("PENDING");
        tag.setStatus("DISABLED");
        when(tagMapper.selectById("tag-pending")).thenReturn(tag);

        assertThatThrownBy(() -> tagService.toggleStatus("tag-pending", "ACTIVE", "E001"))
                .isInstanceOf(BizException.class)
                .hasFieldOrPropertyWithValue("code", CustomerErrorCode.TAG_NOT_APPROVED.getCode());
    }

    @Test
    void batchDisable_shouldDisableAllSelectedTags() {
        CustTag first = new CustTag();
        first.setId("tag-001");
        CustTag second = new CustTag();
        second.setId("tag-002");
        when(tagMapper.selectById("tag-001")).thenReturn(first);
        when(tagMapper.selectById("tag-002")).thenReturn(second);

        tagService.batchDisable(List.of("tag-001", "tag-002"), "E001");

        verify(tagMapper).updateById(argThat((CustTag tag) -> "tag-001".equals(tag.getId())
                && TagStatus.DISABLED.getCode().equals(tag.getStatus())
                && "E001".equals(tag.getUpdatedBy())));
        verify(tagMapper).updateById(argThat((CustTag tag) -> "tag-002".equals(tag.getId())
                && TagStatus.DISABLED.getCode().equals(tag.getStatus())
                && "E001".equals(tag.getUpdatedBy())));
    }

    @Test
    void batchDelete_shouldSoftDeleteAllSelectedTags() {
        CustTag first = new CustTag();
        first.setId("tag-001");
        CustTag second = new CustTag();
        second.setId("tag-002");
        when(tagMapper.selectById("tag-001")).thenReturn(first);
        when(tagMapper.selectById("tag-002")).thenReturn(second);

        tagService.batchDelete(List.of("tag-001", "tag-002"), "E001");

        verify(tagMapper).updateById(argThat((CustTag tag) -> "tag-001".equals(tag.getId())
                && Integer.valueOf(1).equals(tag.getDeleted())
                && TagStatus.DISABLED.getCode().equals(tag.getStatus())));
        verify(tagMapper).updateById(argThat((CustTag tag) -> "tag-002".equals(tag.getId())
                && Integer.valueOf(1).equals(tag.getDeleted())
                && TagStatus.DISABLED.getCode().equals(tag.getStatus())));
    }

    @Test
    void approve_shouldRejectOtherCreatorTagForNonCompanyReviewer() {
        CustTag tag = new CustTag();
        tag.setId("tag-pending");
        tag.setCreatedBy("E001");
        tag.setApprovalStatus("PENDING");
        when(tagMapper.selectById("tag-pending")).thenReturn(tag);

        assertThatThrownBy(() -> tagService.approve("tag-pending", "E002", false))
                .isInstanceOf(BizException.class)
                .hasFieldOrPropertyWithValue("code", CustomerErrorCode.TAG_REVIEW_FORBIDDEN.getCode());
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

    @Test
    void listPage_shouldFillCreatorAndReviewerNamesInBatch() {
        CustTag tag = new CustTag();
        tag.setId("tag-001");
        tag.setCreatedBy("E10001");
        tag.setReviewedBy("E10002");
        when(tagMapper.selectPage(isNull(), isNull(), eq(0), eq(20)))
                .thenReturn(Collections.singletonList(tag));
        when(tagMapper.countPage(isNull(), isNull())).thenReturn(1L);
        UserDTO creator = new UserDTO();
        creator.setEmpId("E10001");
        creator.setDisplayName("张三");
        UserDTO reviewer = new UserDTO();
        reviewer.setEmpId("E10002");
        reviewer.setDisplayName("李四");
        when(userApi.getUserByEmpIds(List.of("E10001", "E10002")))
                .thenReturn(List.of(creator, reviewer));

        PageResult<CustTag> result = tagService.listPage(null, null, 1, 20);

        assertThat(result.getRecords().get(0).getCreatedByName()).isEqualTo("张三");
        assertThat(result.getRecords().get(0).getReviewedByName()).isEqualTo("李四");
        verify(userApi).getUserByEmpIds(List.of("E10001", "E10002"));
    }

    @Test
    void listReviewPage_historyOnlyQueriesRecordsReviewedByCurrentEmployee() {
        CustTag tag = new CustTag();
        tag.setId("tag-001");
        tag.setReviewedBy("E10001");
        when(tagMapper.selectReviewPage(null, "HISTORY", "E10001", 0, 20))
                .thenReturn(List.of(tag));
        when(tagMapper.countReviewPage(null, "HISTORY", "E10001")).thenReturn(1L);
        when(userApi.getUserByEmpIds(List.of("E10001"))).thenReturn(List.of());

        PageResult<CustTag> result = tagService.listReviewPage(
                null, "HISTORY", 1, 20, "E10001");

        assertThat(result.getTotal()).isEqualTo(1L);
        assertThat(result.getRecords()).extracting(CustTag::getId).containsExactly("tag-001");
        verify(tagMapper).selectReviewPage(null, "HISTORY", "E10001", 0, 20);
        verify(tagMapper).countReviewPage(null, "HISTORY", "E10001");
    }

    @Test
    void approve_shouldActivatePendingTagAndRecordReviewer() {
        CustTag pending = new CustTag();
        pending.setId("tag-001");
        pending.setApprovalStatus("PENDING");
        pending.setStatus(TagStatus.DISABLED.getCode());
        when(tagMapper.selectById("tag-001")).thenReturn(pending);

        tagService.approve("tag-001", "E002");

        verify(tagMapper).updateById(argThat((CustTag tag) ->
                "APPROVED".equals(tag.getApprovalStatus())
                        && TagStatus.ACTIVE.getCode().equals(tag.getStatus())
                        && "E002".equals(tag.getReviewedBy())
                        && tag.getReviewedTime() != null));
    }

    @Test
    void reject_shouldRequireReasonAndDisablePendingTag() {
        CustTag pending = new CustTag();
        pending.setId("tag-001");
        pending.setApprovalStatus("PENDING");
        when(tagMapper.selectById("tag-001")).thenReturn(pending);

        assertThatThrownBy(() -> tagService.reject("tag-001", " ", "E002"))
                .isInstanceOf(BizException.class)
                .hasFieldOrPropertyWithValue("code", CustomerErrorCode.TAG_REJECT_REASON_REQUIRED.getCode());

        tagService.reject("tag-001", "适用范围不明确", "E002");
        verify(tagMapper).updateById(argThat((CustTag tag) ->
                "REJECTED".equals(tag.getApprovalStatus())
                        && TagStatus.DISABLED.getCode().equals(tag.getStatus())
                        && "适用范围不明确".equals(tag.getRejectReason())));
    }
}
