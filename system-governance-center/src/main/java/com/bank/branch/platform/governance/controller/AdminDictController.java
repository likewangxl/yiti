package com.bank.branch.platform.governance.controller;

import com.bank.branch.platform.common.security.annotation.BizAuth;
import com.bank.branch.platform.common.security.enums.BizAction;
import com.bank.branch.platform.common.security.enums.BizType;
import com.bank.branch.platform.common.web.ResponseWrapper;
import com.bank.branch.platform.governance.api.dto.DictCreateReqDTO;
import com.bank.branch.platform.governance.api.dto.DictItemRespDTO;
import com.bank.branch.platform.governance.api.dto.DictStatusReqDTO;
import com.bank.branch.platform.governance.api.dto.DictUpdateReqDTO;
import com.bank.branch.platform.governance.entity.SysDict;
import com.bank.branch.platform.governance.service.DictService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 字典管理控制器
 * 提供字典项的管理端增删改查接口（需权限）
 */
@Slf4j
@RestController
@RequiredArgsConstructor
@Tag(name = "字典管理", description = "字典项的增删改查管理接口")
@RequestMapping("/api/admin/sys/dicts")
public class AdminDictController {

    private final DictService dictService;

    /**
     * 新增字典项
     *
     * @param req 字典创建请求DTO
     * @return 新建的字典项响应
     */
    @PostMapping
    @Operation(summary = "新增字典项")
    @BizAuth(bizType = BizType.SYS_CONFIG, action = BizAction.CONFIG)
    public ResponseWrapper<DictItemRespDTO> createDict(@Valid @RequestBody DictCreateReqDTO req) {
        log.info("[AdminDictController.createDict] dictType={}, dictCode={}", req.getDictType(), req.getDictCode());
        SysDict dict = dictService.createDict(
                req.getDictType(), req.getDictCode(), req.getDictLabel(),
                req.getDictValue(), req.getSortOrder(), req.getRemark());
        return ResponseWrapper.success(dictService.toDictItemRespDTO(dict));
    }

    /**
     * 更新字典项
     *
     * @param id  字典ID（路径参数）
     * @param req 字典更新请求DTO
     * @return 更新后的字典项响应
     */
    @PutMapping("/{id}")
    @Operation(summary = "更新字典项")
    @BizAuth(bizType = BizType.SYS_CONFIG, action = BizAction.CONFIG)
    public ResponseWrapper<DictItemRespDTO> updateDict(
            @PathVariable(value = "id") String id,
            @Valid @RequestBody DictUpdateReqDTO req) {
        log.info("[AdminDictController.updateDict] id={}", id);
        SysDict dict = dictService.updateDict(id, req.getDictLabel(), req.getDictValue(),
                req.getSortOrder(), req.getRemark());
        return ResponseWrapper.success(dictService.toDictItemRespDTO(dict));
    }

    /**
     * 删除字典项（逻辑删除）
     *
     * @param id 字典ID（路径参数）
     * @return 成功响应
     */
    @DeleteMapping("/{id}")
    @Operation(summary = "删除字典项")
    @BizAuth(bizType = BizType.SYS_CONFIG, action = BizAction.CONFIG)
    public ResponseWrapper<Void> deleteDict(@PathVariable(value = "id") String id) {
        log.info("[AdminDictController.deleteDict] id={}", id);
        dictService.deleteDict(id);
        return ResponseWrapper.success();
    }

    /**
     * 启用/禁用字典项状态
     *
     * @param id     字典ID（路径参数）
     * @param req    目标状态请求DTO（ACTIVE/DISABLED）
     * @return 更新后的字典实体
     */
    @PutMapping("/{id}/status")
    @Operation(summary = "启用/禁用字典项")
    @BizAuth(bizType = BizType.SYS_CONFIG, action = BizAction.CONFIG)
    public ResponseWrapper<SysDict> updateDictStatus(
            @PathVariable(value = "id") String id,
            @Valid @RequestBody DictStatusReqDTO req) {
        log.info("[AdminDictController.updateDictStatus] id={}, status={}", id, req.getStatus());
        SysDict dict = dictService.updateStatus(id, req.getStatus());
        return ResponseWrapper.success(dict);
    }

}
