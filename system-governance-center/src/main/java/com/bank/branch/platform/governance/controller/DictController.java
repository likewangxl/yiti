package com.bank.branch.platform.governance.controller;

import com.bank.branch.platform.common.security.annotation.BizAuth;
import com.bank.branch.platform.common.security.enums.BizAction;
import com.bank.branch.platform.common.security.enums.BizType;
import com.bank.branch.platform.common.web.PageResult;
import com.bank.branch.platform.common.web.ResponseWrapper;
import com.bank.branch.platform.governance.api.dto.DictCreateReqDTO;
import com.bank.branch.platform.governance.api.dto.DictTypeRespDTO;
import com.bank.branch.platform.governance.api.dto.DictUpdateReqDTO;
import com.bank.branch.platform.governance.entity.SysDict;
import com.bank.branch.platform.governance.service.DictService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 字典管理控制器
 * 提供字典项的公共查询接口和管理端增删改查接口
 */
@Slf4j
@RestController
@RequiredArgsConstructor
@Tag(name = "字典管理", description = "系统字典的增删改查管理")
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
    @GetMapping("/api/sys/dicts")
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
    @GetMapping("/api/sys/dicts/{dictType}/items")
    @Operation(summary = "查询指定类型的字典项列表")
    public ResponseWrapper<List<SysDict>> getDictItems(
            @PathVariable(value = "dictType") String dictType) {
        log.debug("[DictController.getDictItems] dictType={}", dictType);
        List<SysDict> items = dictService.getDictItems(dictType);
        return ResponseWrapper.success(items);
    }

    /**
     * 新增字典项
     *
     * @param req 字典创建请求DTO
     * @return 新建的字典实体
     */
    @PostMapping("/api/admin/sys/dicts")
    @Operation(summary = "新增字典项")
    @BizAuth(bizType = BizType.SYS_CONFIG, action = BizAction.CONFIG)
    public ResponseWrapper<SysDict> createDict(@Valid @RequestBody DictCreateReqDTO req) {
        log.info("[DictController.createDict] dictType={}, dictCode={}", req.getDictType(), req.getDictCode());
        SysDict dict = dictService.createDict(
                req.getDictType(), req.getDictCode(), req.getDictLabel(),
                req.getDictValue(), req.getSortOrder(), req.getRemark());
        return ResponseWrapper.success(dict);
    }

    /**
     * 更新字典项
     *
     * @param id  字典ID（路径参数）
     * @param req 字典更新请求DTO
     * @return 更新后的字典实体
     */
    @PutMapping("/api/admin/sys/dicts/{id}")
    @Operation(summary = "更新字典项")
    @BizAuth(bizType = BizType.SYS_CONFIG, action = BizAction.CONFIG)
    public ResponseWrapper<SysDict> updateDict(
            @PathVariable(value = "id") String id,
            @Valid @RequestBody DictUpdateReqDTO req) {
        log.info("[DictController.updateDict] id={}", id);
        SysDict dict = dictService.updateDict(id, req.getDictLabel(), req.getDictValue(),
                req.getSortOrder(), req.getRemark());
        return ResponseWrapper.success(dict);
    }

    /**
     * 删除字典项（逻辑删除）
     *
     * @param id 字典ID（路径参数）
     * @return 成功响应
     */
    @DeleteMapping("/api/admin/sys/dicts/{id}")
    @Operation(summary = "删除字典项")
    @BizAuth(bizType = BizType.SYS_CONFIG, action = BizAction.CONFIG)
    public ResponseWrapper<Void> deleteDict(@PathVariable(value = "id") String id) {
        log.info("[DictController.deleteDict] id={}", id);
        dictService.deleteDict(id);
        return ResponseWrapper.success();
    }

    /**
     * 启用/禁用字典项状态（A.6）
     *
     * @param id     字典ID（路径参数）
     * @param status 目标状态（ACTIVE/DISABLED）
     * @return 更新后的字典实体
     */
    @PutMapping("/api/admin/sys/dicts/{id}/status")
    @Operation(summary = "启用/禁用字典项")
    @BizAuth(bizType = BizType.SYS_CONFIG, action = BizAction.CONFIG)
    public ResponseWrapper<SysDict> updateDictStatus(
            @PathVariable(value = "id") String id,
            @RequestParam(value = "status") String status) {
        log.info("[DictController.updateDictStatus] id={}, status={}", id, status);
        SysDict dict = dictService.updateStatus(id, status);
        return ResponseWrapper.success(dict);
    }

    /**
     * 分页查询字典列表（管理端）
     *
     * @param dictType 字典类型，可为 null
     * @param keyword  关键词，可为 null
     * @param pageNo   页码，默认 1
     * @param pageSize 每页条数，默认 20
     * @return 分页结果
     */
    @GetMapping("/api/admin/sys/dicts")
    @Operation(summary = "分页查询字典列表")
    @BizAuth(bizType = BizType.SYS_CONFIG, action = BizAction.READ)
    public ResponseWrapper<SysDict> listByPage(
            @RequestParam(value = "dictType", required = false) String dictType,
            @RequestParam(value = "keyword", required = false) String keyword,
            @RequestParam(value = "pageNo", defaultValue = "1") int pageNo,
            @RequestParam(value = "pageSize", defaultValue = "20") int pageSize) {
        log.debug("[DictController.listByPage] dictType={}, keyword={}, pageNo={}, pageSize={}",
                dictType, keyword, pageNo, pageSize);
        PageResult<SysDict> result = dictService.listByPage(dictType, keyword, pageNo, pageSize);
        return ResponseWrapper.page(result);
    }
}
