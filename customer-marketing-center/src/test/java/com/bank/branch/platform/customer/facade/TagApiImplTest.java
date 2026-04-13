package com.bank.branch.platform.customer.facade;

import com.bank.branch.platform.customer.entity.CustTag;
import com.bank.branch.platform.customer.service.TagService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * TagApiImpl 单元测试（TDD）
 * 验证委托调用路径正确。
 */
@ExtendWith(MockitoExtension.class)
class TagApiImplTest {

    @Mock
    private TagService tagService;

    @InjectMocks
    private TagApiImpl tagApiImpl;

    @Test
    void listEnabled_shouldDelegateToTagService() {
        // given
        CustTag tag1 = new CustTag();
        tag1.setId("tag-001");
        tag1.setTagName("VIP客户");

        CustTag tag2 = new CustTag();
        tag2.setId("tag-002");
        tag2.setTagName("重点客户");

        when(tagService.listEnabled()).thenReturn(Arrays.asList(tag1, tag2));

        // when
        List<CustTag> result = tagApiImpl.listEnabled();

        // then
        assertThat(result).hasSize(2);
        assertThat(result.get(0).getId()).isEqualTo("tag-001");
        verify(tagService).listEnabled();
    }

    @Test
    void listEnabled_shouldReturnEmptyListWhenNoTags() {
        // given
        when(tagService.listEnabled()).thenReturn(Collections.emptyList());

        // when
        List<CustTag> result = tagApiImpl.listEnabled();

        // then
        assertThat(result).isEmpty();
        verify(tagService).listEnabled();
    }

    @Test
    void getById_shouldDelegateToTagService() {
        // given
        CustTag tag = new CustTag();
        tag.setId("tag-001");
        tag.setTagName("VIP客户");
        tag.setTagCode("VIP_CUSTOMER");
        when(tagService.getById("tag-001")).thenReturn(tag);

        // when
        CustTag result = tagApiImpl.getById("tag-001");

        // then
        assertThat(result).isNotNull();
        assertThat(result.getId()).isEqualTo("tag-001");
        assertThat(result.getTagCode()).isEqualTo("VIP_CUSTOMER");
        verify(tagService).getById("tag-001");
    }
}
