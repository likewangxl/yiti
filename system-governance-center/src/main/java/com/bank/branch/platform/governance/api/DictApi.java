package com.bank.branch.platform.governance.api;

import com.bank.branch.platform.governance.api.dto.DictItemDTO;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * 字典服务对外API
 * <p>
 * 提供字典数据的查询与校验能力，供其他业务模块通过 Spring Bean 注入调用。
 * 高频调用，内部启用 Redis 缓存（TTL 10分钟）。
 * </p>
 */
public interface DictApi {

    /**
     * 获取指定类型的字典项列表（仅启用状态）
     * 按 sort_order 升序排列
     *
     * @param dictType 字典类型编码（如 INDUSTRY、CUSTOMER_TYPE）
     * @return 字典项列表，dictType 不存在时返回空列表
     */
    List<DictItemDTO> getDictItems(String dictType);

    /**
     * 获取指定字典项
     *
     * @param dictType 字典类型编码
     * @param dictCode 字典项编码
     * @return 字典项，不存在时返回 Optional.empty()
     */
    Optional<DictItemDTO> getDictItem(String dictType, String dictCode);

    /**
     * 获取指定字典项的显示标签
     * 用于将存储值转换为显示文本
     *
     * @param dictType 字典类型编码
     * @param dictCode 字典项编码
     * @return 字典标签（如 "信息技术"），不存在时返回 dictCode 本身
     */
    String getDictLabel(String dictType, String dictCode);

    /**
     * 批量获取多个类型的字典项
     * 减少多次调用开销，一次性获取多个字典类型的数据
     *
     * @param dictTypes 字典类型集合
     * @return key=dictType, value=该类型下的字典项列表
     */
    Map<String, List<DictItemDTO>> batchGetDictItems(Set<String> dictTypes);

    /**
     * 校验字典值是否合法（存在且启用）
     *
     * @param dictType  字典类型编码
     * @param dictValue 字典值
     * @return true=合法, false=不合法（不存在或已禁用）
     */
    boolean isValidDictValue(String dictType, String dictValue);
}
