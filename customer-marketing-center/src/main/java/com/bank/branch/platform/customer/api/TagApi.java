package com.bank.branch.platform.customer.api;

import com.bank.branch.platform.customer.api.dto.TagDTO;

import java.util.List;
import java.util.Map;

/**
 * 标签查询 API
 *
 * <p>被调用方：business-application-center / performance-engine-center / report-analytics-center</p>
 *
 * @author customer-marketing-center
 * @since V1.0
 */
public interface TagApi {

    /**
     * 获取启用的标签列表。按 tag_priority 升序、tag_name 升序。
     * 缓存：cust:tag:enabled:list，TTL 5 分钟。
     *
     * @return 启用状态的标签 DTO 列表
     */
    List<TagDTO> listEnabledTags();

    /**
     * 获取客户的标签列表（只返回启用状态）。
     *
     * @param custId 客户 ID
     * @return 该客户已打的启用标签列表
     */
    List<TagDTO> getCustomerTags(String custId);

    /**
     * 批量获取客户标签。
     * custIds 最大 500，超限抛 COMMON-40000。
     *
     * @param custIds 客户 ID 列表，最多 500 个
     * @return 以 custId 为 key 的标签列表 Map
     */
    Map<String, List<TagDTO>> batchGetCustomerTags(List<String> custIds);

    /**
     * 按标签查询客户 ID 列表（仅 ID 不分页）。
     *
     * @param tagId 标签 ID
     * @return 打了该标签的客户 ID 列表
     */
    List<String> getCustomerIdsByTag(String tagId);

    /**
     * 校验标签名称是否存在。
     *
     * @param tagName 标签名称
     * @return true-已存在，false-不存在
     */
    boolean isTagNameExists(String tagName);
}
