package com.bank.branch.platform.governance.facade;

import com.bank.branch.platform.governance.api.DictApi;
import com.bank.branch.platform.governance.api.dto.DictItemDTO;
import com.bank.branch.platform.governance.entity.SysDict;
import com.bank.branch.platform.governance.service.DictService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 字典服务 Facade 实现
 * <p>
 * 实现 DictApi 接口，负责将 DictService 返回的实体转换为对外 DTO。
 * 所有方法均为同步调用，缓存由 DictService 内部管理。
 * </p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class DictFacade implements DictApi {

    private final DictService dictService;

    /**
     * 获取指定类型的字典项列表
     * dictType 不存在时返回空列表，不抛异常
     *
     * @param dictType 字典类型编码
     * @return 字典项 DTO 列表
     */
    @Override
    public List<DictItemDTO> getDictItems(String dictType) {
        List<SysDict> items = dictService.getDictItems(dictType);
        return items.stream()
                .map(this::toItemDTO)
                .collect(Collectors.toList());
    }

    /**
     * 获取指定字典项
     * 从字典项列表中按 dictCode 查找匹配项
     *
     * @param dictType 字典类型编码
     * @param dictCode 字典项编码
     * @return 字典项 DTO，不存在时返回 Optional.empty()
     */
    @Override
    public Optional<DictItemDTO> getDictItem(String dictType, String dictCode) {
        List<SysDict> items = dictService.getDictItems(dictType);
        return items.stream()
                .filter(item -> item.getDictCode().equals(dictCode))
                .findFirst()
                .map(this::toItemDTO);
    }

    /**
     * 获取指定字典项的显示标签
     * 不存在时返回 dictCode 本身，避免前端显示空白
     *
     * @param dictType 字典类型编码
     * @param dictCode 字典项编码
     * @return 字典标签，不存在时返回 dictCode
     */
    @Override
    public String getDictLabel(String dictType, String dictCode) {
        String label = dictService.getDictLabel(dictType, dictCode);
        // API 契约要求：不存在时返回 dictCode 本身
        return label != null ? label : dictCode;
    }

    /**
     * 批量获取多个类型的字典项
     * 委托给 DictService.batchGetDictItems 后将实体列表转换为 DTO 列表
     *
     * @param dictTypes 字典类型集合
     * @return key=dictType, value=该类型下的字典项 DTO 列表
     */
    @Override
    public Map<String, List<DictItemDTO>> batchGetDictItems(Set<String> dictTypes) {
        Map<String, List<SysDict>> serviceResult = dictService.batchGetDictItems(dictTypes);
        Map<String, List<DictItemDTO>> result = new HashMap<>();
        for (Map.Entry<String, List<SysDict>> entry : serviceResult.entrySet()) {
            result.put(entry.getKey(),
                    entry.getValue().stream()
                            .map(this::toItemDTO)
                            .collect(Collectors.toList()));
        }
        return result;
    }

    /**
     * 校验字典值是否合法（存在且启用）
     *
     * @param dictType  字典类型编码
     * @param dictValue 字典值
     * @return true=合法, false=不合法
     */
    @Override
    public boolean isValidDictValue(String dictType, String dictValue) {
        return dictService.isValidDictValue(dictType, dictValue);
    }

    // ── 私有方法 ──────────────────────────────────────────────────

    /**
     * 将 SysDict 实体转换为 DictItemDTO
     * 仅映射对外可见的展示与存储字段
     *
     * @param entity 字典实体
     * @return DictItemDTO
     */
    private DictItemDTO toItemDTO(SysDict entity) {
        DictItemDTO dto = new DictItemDTO();
        dto.setId(entity.getId());
        dto.setDictType(entity.getDictType());
        dto.setDictCode(entity.getDictCode());
        dto.setDictLabel(entity.getDictLabel());
        dto.setDictValue(entity.getDictValue());
        dto.setSortOrder(entity.getSortOrder());
        return dto;
    }
}
