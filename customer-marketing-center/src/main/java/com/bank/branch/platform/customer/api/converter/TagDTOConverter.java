package com.bank.branch.platform.customer.api.converter;

import com.bank.branch.platform.customer.api.dto.TagDTO;
import com.bank.branch.platform.customer.entity.CustTag;
import org.springframework.beans.BeanUtils;

import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

/**
 * CustTag → TagDTO 转换器（纯静态工具类）。
 *
 * <p>转换规则：
 * <ul>
 *   <li>TagDTO 与 CustTag 公共字段名完全一致，直接使用 BeanUtils.copyProperties 拷贝</li>
 *   <li>Entity 特有字段（createdBy/updatedBy/createdTime/updatedTime/deleted）不在 DTO 中，自动忽略</li>
 * </ul>
 */
public final class TagDTOConverter {

    private TagDTOConverter() {
        // 纯静态工具类，禁止实例化
    }

    /**
     * 将单个 CustTag 实体转换为 TagDTO。
     *
     * @param entity 实体，允许为 null
     * @return DTO；entity 为 null 时返回 null
     */
    public static TagDTO toDTO(CustTag entity) {
        if (entity == null) {
            return null;
        }
        TagDTO dto = new TagDTO();
        // TagDTO 与 CustTag 公共字段名完全相同，直接拷贝
        BeanUtils.copyProperties(entity, dto);
        return dto;
    }

    /**
     * 将实体列表转换为 DTO 列表，自动过滤 null 元素。
     *
     * @param entities 实体列表，允许为 null
     * @return DTO 列表；entities 为 null 时返回空列表
     */
    public static List<TagDTO> toDTOList(List<CustTag> entities) {
        if (entities == null) {
            return Collections.emptyList();
        }
        return entities.stream()
                .filter(Objects::nonNull)
                .map(TagDTOConverter::toDTO)
                .collect(Collectors.toList());
    }
}
