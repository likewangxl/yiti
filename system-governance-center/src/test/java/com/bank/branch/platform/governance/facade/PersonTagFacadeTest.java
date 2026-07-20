package com.bank.branch.platform.governance.facade;

import com.bank.branch.platform.governance.api.dto.PersonTagDTO;
import com.bank.branch.platform.governance.entity.PersonTag;
import com.bank.branch.platform.governance.entity.PersonTagRel;
import com.bank.branch.platform.governance.mapper.PersonTagMapper;
import com.bank.branch.platform.governance.mapper.PersonTagRelMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * PersonTagFacade 单元测试（跨模块契约：KPI 方案「员工标签范围」依赖本接口）。
 */
@ExtendWith(MockitoExtension.class)
class PersonTagFacadeTest {

    @Mock
    private PersonTagMapper tagMapper;
    @Mock
    private PersonTagRelMapper relMapper;

    private PersonTagFacade facade;

    @BeforeEach
    void setUp() {
        facade = new PersonTagFacade(tagMapper, relMapper);
    }

    private static PersonTagRel rel(Long tagId, String username) {
        PersonTagRel r = new PersonTagRel();
        r.setTagId(tagId);
        r.setUsername(username);
        return r;
    }

    private static PersonTag tag(Long id, String name) {
        PersonTag t = new PersonTag();
        t.setTagId(id);
        t.setTagName(name);
        return t;
    }

    @Test
    void getUsernamesByTagIds_multiTags_shouldReturnUnionDistinct() {
        when(relMapper.selectByTagIds(List.of(1L, 2L))).thenReturn(List.of(
                rel(1L, "100001"), rel(1L, "100002"),
                rel(2L, "100002"), rel(2L, "100003")));

        List<String> usernames = facade.getUsernamesByTagIds(List.of(1L, 2L));

        // 并集去重：100002 同属两个标签只出现一次
        assertThat(usernames).containsExactly("100001", "100002", "100003");
    }

    @Test
    void getUsernamesByTagIds_duplicateAndNullIds_shouldNormalizeBeforeQuery() {
        when(relMapper.selectByTagIds(List.of(1L))).thenReturn(List.of(rel(1L, "100001")));

        List<String> usernames = facade.getUsernamesByTagIds(java.util.Arrays.asList(1L, null, 1L));

        assertThat(usernames).containsExactly("100001");
        verify(relMapper).selectByTagIds(List.of(1L));
    }

    @Test
    void getUsernamesByTagIds_emptyInput_shouldSkipQuery() {
        assertThat(facade.getUsernamesByTagIds(List.of())).isEmpty();
        assertThat(facade.getUsernamesByTagIds(null)).isEmpty();
        verify(relMapper, never()).selectByTagIds(anyList());
    }

    @Test
    void getTagsByIds_shouldReturnOnlyExistingTags() {
        // 标签 9 已被删除 → 不在返回中，调用方据此判定范围失效
        when(tagMapper.selectBatchIds(List.of(1L, 9L))).thenReturn(List.of(tag(1L, "骨干")));

        List<PersonTagDTO> tags = facade.getTagsByIds(List.of(1L, 9L));

        assertThat(tags).hasSize(1);
        assertThat(tags.get(0).getTagId()).isEqualTo(1L);
        assertThat(tags.get(0).getTagName()).isEqualTo("骨干");
    }

    @Test
    void getTagsByNames_shouldTrimAndDedupeNames() {
        when(tagMapper.selectByTagNames(List.of("骨干", "新星"))).thenReturn(
                List.of(tag(1L, "骨干"), tag(2L, "新星")));

        // List.of 不接受 null 元素，用 Arrays.asList 覆盖"含 null/空白项"的清洗分支
        List<PersonTagDTO> tags = facade.getTagsByNames(
                java.util.Arrays.asList(" 骨干 ", "新星", "骨干", "", null));

        assertThat(tags).extracting(PersonTagDTO::getTagId).containsExactly(1L, 2L);
        verify(tagMapper).selectByTagNames(List.of("骨干", "新星"));
    }

    @Test
    void getTagsByNames_emptyInput_shouldSkipQuery() {
        assertThat(facade.getTagsByNames(List.of())).isEmpty();
        assertThat(facade.getTagsByNames(List.of("  "))).isEmpty();
        verify(tagMapper, never()).selectByTagNames(anyList());
    }
}
