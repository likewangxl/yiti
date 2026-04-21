package com.bank.branch.platform.customer.api.converter;

import com.bank.branch.platform.customer.api.dto.TagDTO;
import com.bank.branch.platform.customer.entity.CustTag;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * TagDTOConverter 单元测试（TDD Red 阶段）
 * 测试类先于实现类存在，用于驱动实现。
 */
class TagDTOConverterTest {

    // ==================== toDTO ====================

    @Test
    void toDTO_mapsAllFields() {
        // given
        CustTag entity = new CustTag();
        entity.setId("tag-001");
        entity.setTagName("VIP客户");
        entity.setTagCode("VIP_CUSTOMER");
        entity.setTagCategory("价值类");
        entity.setTagPriority(10);
        entity.setStatus("ACTIVE");
        entity.setDescription("高价值客户标签");
        entity.setCreatedBy("EMP_001");
        entity.setCreatedTime(LocalDateTime.of(2024, 1, 1, 0, 0));
        entity.setUpdatedBy("EMP_002");
        entity.setUpdatedTime(LocalDateTime.of(2024, 6, 1, 0, 0));
        entity.setDeleted(0);

        // when
        TagDTO dto = TagDTOConverter.toDTO(entity);

        // then
        assertThat(dto).isNotNull();
        assertThat(dto.getId()).isEqualTo("tag-001");
        assertThat(dto.getTagName()).isEqualTo("VIP客户");
        assertThat(dto.getTagCode()).isEqualTo("VIP_CUSTOMER");
        assertThat(dto.getTagCategory()).isEqualTo("价值类");
        assertThat(dto.getTagPriority()).isEqualTo(10);
        assertThat(dto.getStatus()).isEqualTo("ACTIVE");
        assertThat(dto.getDescription()).isEqualTo("高价值客户标签");
    }

    @Test
    void toDTO_returnsNullForNullEntity() {
        assertThat(TagDTOConverter.toDTO(null)).isNull();
    }

    @Test
    void toDTO_handlesNullOptionalFields() {
        // given: 可选字段为 null
        CustTag entity = new CustTag();
        entity.setId("tag-002");
        entity.setTagName("测试标签");
        entity.setTagCode("TEST_TAG");
        entity.setTagCategory(null);
        entity.setTagPriority(null);
        entity.setDescription(null);

        // when
        TagDTO dto = TagDTOConverter.toDTO(entity);

        // then
        assertThat(dto.getId()).isEqualTo("tag-002");
        assertThat(dto.getTagCategory()).isNull();
        assertThat(dto.getTagPriority()).isNull();
        assertThat(dto.getDescription()).isNull();
    }

    @Test
    void toDTOList_mapsEachAndFiltersNulls() {
        // given
        CustTag e1 = new CustTag();
        e1.setId("tag-001");
        e1.setTagCode("CODE_A");

        CustTag e2 = new CustTag();
        e2.setId("tag-002");
        e2.setTagCode("CODE_B");

        List<CustTag> list = Arrays.asList(e1, null, e2);

        // when
        List<TagDTO> dtos = TagDTOConverter.toDTOList(list);

        // then
        assertThat(dtos).hasSize(2);
        assertThat(dtos.get(0).getId()).isEqualTo("tag-001");
        assertThat(dtos.get(0).getTagCode()).isEqualTo("CODE_A");
        assertThat(dtos.get(1).getId()).isEqualTo("tag-002");
    }

    @Test
    void toDTOList_returnsEmptyForNullInput() {
        assertThat(TagDTOConverter.toDTOList(null)).isEmpty();
    }

    @Test
    void toDTOList_returnsEmptyForEmptyList() {
        assertThat(TagDTOConverter.toDTOList(Collections.emptyList())).isEmpty();
    }
}
