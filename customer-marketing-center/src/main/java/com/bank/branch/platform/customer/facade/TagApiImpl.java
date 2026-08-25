package com.bank.branch.platform.customer.facade;

import com.bank.branch.platform.common.web.exception.BizException;
import com.bank.branch.platform.customer.api.TagApi;
import com.bank.branch.platform.customer.api.converter.TagDTOConverter;
import com.bank.branch.platform.customer.api.dto.TagDTO;
import com.bank.branch.platform.customer.entity.CustTag;
import com.bank.branch.platform.customer.entity.CustTagRel;
import com.bank.branch.platform.customer.mapper.CustTagMapper;
import com.bank.branch.platform.customer.mapper.CustTagRelMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 标签对外接口实现。
 *
 * <p>实现 {@link TagApi} 契约定义的 5 个方法。直接依赖 Mapper，不通过 Service 层，
 * 避免 API 层混入内部业务校验逻辑。</p>
 *
 * <p>对外 API 无写操作，全部只读。</p>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class TagApiImpl implements TagApi {

    /** custIds 批量查询最大上限 */
    private static final int MAX_BATCH = 500;

    private final CustTagMapper custTagMapper;
    private final CustTagRelMapper custTagRelMapper;

    /**
     * 获取启用的标签列表，按 tag_priority 升序、tag_name 升序。
     *
     * @return 启用状态的标签 DTO 列表
     */
    @Override
    public List<TagDTO> listEnabledTags() {
        log.debug("[TagApiImpl.listEnabledTags] called");
        List<CustTag> tags = custTagMapper.selectEnabledSorted();
        return TagDTOConverter.toDTOList(tags);
    }

    /**
     * 获取客户的标签列表，只返回启用状态的标签。
     *
     * @param custId 客户 ID
     * @return 该客户打的启用标签列表
     */
    @Override
    public List<TagDTO> getCustomerTags(String custId) {
        log.debug("[TagApiImpl.getCustomerTags] custId={}", custId);
        List<CustTagRel> rels = custTagRelMapper.selectByCustId(custId);
        if (rels == null || rels.isEmpty()) {
            return Collections.emptyList();
        }
        List<String> tagIds = rels.stream().map(CustTagRel::getTagId).collect(Collectors.toList());
        // selectEnabledByIds 只返回 status=ACTIVE 的记录，天然过滤 DISABLED
        List<CustTag> enabledTags = custTagMapper.selectEnabledByIds(tagIds);
        return TagDTOConverter.toDTOList(enabledTags);
    }

    /**
     * 批量获取客户标签，custIds 最大 500。
     * 策略：两次 DB 调用（rel + tag），避免 N+1：
     * 1. 一次查询所有 rel
     * 2. 批量查 tag 详情
     * 3. 内存按 custId 分组
     *
     * @param custIds 客户 ID 列表，最多 500 个
     * @return 以 custId 为 key 的标签列表 Map
     */
    @Override
    public Map<String, List<TagDTO>> batchGetCustomerTags(List<String> custIds) {
        if (custIds == null || custIds.isEmpty()) {
            return Collections.emptyMap();
        }
        // 超限保护
        if (custIds.size() > MAX_BATCH) {
            throw new BizException("COMMON-40000", "参数超限: custIds 最大 500，实际 " + custIds.size());
        }
        log.debug("[TagApiImpl.batchGetCustomerTags] custIds.size={}", custIds.size());

        // 第一次 DB: 批量查询 rel
        List<CustTagRel> rels = custTagRelMapper.selectByCustIds(custIds);
        if (rels == null || rels.isEmpty()) {
            return Collections.emptyMap();
        }

        // 收集所有涉及的 tagId，第二次 DB: 批量查 tag 详情（只返回 ACTIVE）
        Set<String> tagIdSet = rels.stream().map(CustTagRel::getTagId).collect(Collectors.toSet());
        Map<String, TagDTO> tagMap = custTagMapper.selectEnabledByIds(new ArrayList<>(tagIdSet))
                .stream()
                .map(TagDTOConverter::toDTO)
                .collect(Collectors.toMap(TagDTO::getId, t -> t));

        // 内存分组：custId → List<TagDTO>（过滤掉 DISABLED 的标签）
        return rels.stream()
                .filter(r -> tagMap.containsKey(r.getTagId()))
                .collect(Collectors.groupingBy(
                        CustTagRel::getCustId,
                        Collectors.mapping(r -> tagMap.get(r.getTagId()), Collectors.toList())
                ));
    }

    /**
     * 按标签查询客户 ID 列表（仅 ID，不分页）。
     *
     * @param tagId 标签 ID
     * @return 打了该标签的客户 ID 列表
     */
    @Override
    public List<String> getCustomerIdsByTag(String tagId) {
        log.debug("[TagApiImpl.getCustomerIdsByTag] tagId={}", tagId);
        List<String> ids = custTagRelMapper.selectCustIdsByTagId(tagId);
        return ids != null ? ids : Collections.emptyList();
    }

    /**
     * 校验标签名称是否存在。
     *
     * @param tagName 标签名称
     * @return true-已存在，false-不存在或入参为空
     */
    @Override
    public boolean isTagNameExists(String tagName) {
        if (tagName == null || tagName.isBlank()) {
            return false;
        }
        log.debug("[TagApiImpl.isTagNameExists] tagName={}", tagName);
        Long count = custTagMapper.countByTagName(tagName);
        return count != null && count > 0L;
    }
}
