package com.bank.branch.platform.governance.controller;

import com.bank.branch.platform.common.web.ResponseWrapper;
import com.bank.branch.platform.governance.api.dto.DictItemRespDTO;
import com.bank.branch.platform.governance.api.dto.DictTypeRespDTO;
import com.bank.branch.platform.governance.service.DictService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 字典查询控制器
 * 提供字典项的公共查询接口（无需权限）
 */
@Slf4j
@RestController
@RequiredArgsConstructor
@Tag(name = "字典查询", description = "字典项的公共查询接口")
@RequestMapping("/api/sys")
public class DictController {

    private final DictService dictService;

    /**
     * 查询字典类型列表（公共接口，无需权限）。
     * <p>
     * 用于 A.1 字典类型列表查询（GET /api/sys/dicts）。
     * 按字典类型分组聚合，返回每种类型的汇总信息。
     * </p>
     *
     * @param dictType 字典类型精确匹配，可为 null
     * @param keyword  关键词模糊搜索字典类型，可为 null
     * @param status   状态筛选（ACTIVE/DISABLED），可为 null
     * @return 字典类型汇总列表
     */
    @GetMapping("/dicts")
    @Operation(summary = "查询字典类型列表")
    public ResponseWrapper<List<DictTypeRespDTO>> listDictTypes(
            @RequestParam(value = "dictType", required = false) String dictType,
            @RequestParam(value = "keyword", required = false) String keyword,
            @RequestParam(value = "status", required = false) String status) {
        log.debug("[DictController.listDictTypes] dictType={}, keyword={}, status={}", dictType, keyword, status);
        List<DictTypeRespDTO> result = dictService.listDictTypes(dictType, keyword, status);
        return ResponseWrapper.success(result);
    }

    /**
     * 查询指定字典类型下的所有启用字典项（公共接口，无需权限）
     *
     * @param dictType 字典类型编码
     * @return 字典项列表
     */
    @GetMapping("/dicts/{dictType}/items")
    @Operation(summary = "查询指定类型的字典项列表")
    public ResponseWrapper<List<DictItemRespDTO>> getDictItems(
            @PathVariable(value = "dictType") String dictType) {
        log.debug("[DictController.getDictItems] dictType={}", dictType);
        List<DictItemRespDTO> items = dictService.toDictItemRespDTOList(dictService.getDictItems(dictType));
        return ResponseWrapper.success(items);
    }

}
