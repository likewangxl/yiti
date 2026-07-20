package com.bank.branch.platform.governance.facade;

import com.bank.branch.platform.governance.api.PersonTagApi;
import com.bank.branch.platform.governance.api.dto.PersonTagDTO;
import com.bank.branch.platform.governance.entity.PersonTag;
import com.bank.branch.platform.governance.entity.PersonTagRel;
import com.bank.branch.platform.governance.mapper.PersonTagMapper;
import com.bank.branch.platform.governance.mapper.PersonTagRelMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;

/**
 * 人员标签 Facade 实现（跨模块唯一入口）。
 * <p>只做实体 → DTO 的转换与去重，不含业务规则。</p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PersonTagFacade implements PersonTagApi {

    private final PersonTagMapper tagMapper;
    private final PersonTagRelMapper relMapper;

    /**
     * 按标签 ID 集合取其下全部员工工号（并集去重，单次 IN 查询）。
     *
     * @param tagIds 标签 ID 集合
     * @return 去重工号列表
     */
    @Override
    public List<String> getUsernamesByTagIds(List<Long> tagIds) {
        List<Long> ids = distinctNonNull(tagIds);
        if (ids.isEmpty()) {
            return List.of();
        }
        LinkedHashSet<String> usernames = new LinkedHashSet<>();
        for (PersonTagRel rel : relMapper.selectByTagIds(ids)) {
            if (rel != null && rel.getUsername() != null) {
                usernames.add(rel.getUsername());
            }
        }
        return new ArrayList<>(usernames);
    }

    /**
     * 按标签 ID 集合查标签（缺失的 ID 即为已删除标签，调用方据此提示失效）。
     *
     * @param tagIds 标签 ID 集合
     * @return 命中的标签列表
     */
    @Override
    public List<PersonTagDTO> getTagsByIds(List<Long> tagIds) {
        List<Long> ids = distinctNonNull(tagIds);
        if (ids.isEmpty()) {
            return List.of();
        }
        return toDtos(tagMapper.selectBatchIds(ids));
    }

    /**
     * 按标签名称集合查标签（导入按名称解析 ID 用）。
     *
     * @param tagNames 标签名称集合
     * @return 命中的标签列表
     */
    @Override
    public List<PersonTagDTO> getTagsByNames(List<String> tagNames) {
        if (tagNames == null || tagNames.isEmpty()) {
            return List.of();
        }
        LinkedHashSet<String> names = new LinkedHashSet<>();
        for (String n : tagNames) {
            if (n != null && !n.isBlank()) {
                names.add(n.trim());
            }
        }
        if (names.isEmpty()) {
            return List.of();
        }
        return toDtos(tagMapper.selectByTagNames(new ArrayList<>(names)));
    }

    /** 实体列表 → DTO 列表（跳过 null 元素）。 */
    private static List<PersonTagDTO> toDtos(List<PersonTag> tags) {
        List<PersonTagDTO> out = new ArrayList<>();
        if (tags == null) {
            return out;
        }
        for (PersonTag t : tags) {
            if (t == null) {
                continue;
            }
            PersonTagDTO dto = new PersonTagDTO();
            dto.setTagId(t.getTagId());
            dto.setTagName(t.getTagName());
            out.add(dto);
        }
        return out;
    }

    /** ID 列表清洗：去 null、保序去重。 */
    private static List<Long> distinctNonNull(List<Long> ids) {
        LinkedHashSet<Long> out = new LinkedHashSet<>();
        if (ids != null) {
            for (Long id : ids) {
                if (id != null) {
                    out.add(id);
                }
            }
        }
        return new ArrayList<>(out);
    }
}
