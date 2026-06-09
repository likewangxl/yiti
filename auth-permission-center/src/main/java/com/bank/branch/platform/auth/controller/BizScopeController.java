package com.bank.branch.platform.auth.controller;

import com.bank.branch.platform.auth.api.dto.BizScopeMatrixRespDTO;
import com.bank.branch.platform.auth.api.dto.BizScopeRespDTO;
import com.bank.branch.platform.auth.api.dto.BizScopeSaveReqDTO;
import com.bank.branch.platform.auth.service.BizScopeService;
import com.bank.branch.platform.common.aop.annotation.AuditLog;
import com.bank.branch.platform.common.security.annotation.BizAuth;
import com.bank.branch.platform.common.security.enums.BizAction;
import com.bank.branch.platform.common.security.enums.BizType;
import com.bank.branch.platform.common.web.PageResult;
import com.bank.branch.platform.common.web.ResponseWrapper;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 业务数据范围控制器
 * 提供角色业务数据范围（BizScope）的查询、保存和删除接口
 */
@Slf4j
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/admin/biz-scopes")
@Tag(name = "业务数据范围管理", description = "角色业务数据范围配置管理")
public class BizScopeController {

    private final BizScopeService bizScopeService;

    /**
     * 分页查询业务数据范围配置列表
     *
     * @param roleId   角色ID过滤，可为 null
     * @param bizType  BizType 过滤，可为 null
     * @param pageNo   页码，默认 1
     * @param pageSize 每页条数，默认 20
     * @return 分页结果
     */
    @GetMapping("")
    @Operation(summary = "分页查询业务数据范围列表")
    @BizAuth(bizType = BizType.SYS_CONFIG, action = BizAction.READ)
    public ResponseWrapper<BizScopeRespDTO> listBizScopes(
            @RequestParam(value = "roleId", required = false) String roleId,
            @RequestParam(value = "bizType", required = false) String bizType,
            @RequestParam(value = "pageNo", defaultValue = "1") int pageNo,
            @RequestParam(value = "pageSize", defaultValue = "20") int pageSize) {
        log.debug("[BizScopeController.listBizScopes] roleId={}, bizType={}", roleId, bizType);
        PageResult<BizScopeRespDTO> result = bizScopeService.listByPage(roleId, bizType, pageNo, pageSize);
        return ResponseWrapper.page(result);
    }

    /**
     * 获取角色×业务类型数据范围矩阵（F.2）
     *
     * @return 矩阵视图，含所有角色列表、所有业务类型、roleId→(bizType→dataScope)映射
     */
    @GetMapping("/matrix")
    @Operation(summary = "获取角色×业务类型数据范围矩阵",
               description = "以矩阵表格形式展示所有角色在各业务类型下的数据范围配置")
    @BizAuth(bizType = BizType.SYS_CONFIG, action = BizAction.READ)
    public ResponseWrapper<BizScopeMatrixRespDTO> getBizScopeMatrix() {
        log.debug("[BizScopeController.getBizScopeMatrix] 获取角色×业务类型矩阵");
        BizScopeMatrixRespDTO matrix = bizScopeService.getBizScopeMatrix();
        return ResponseWrapper.success(matrix);
    }

    /**
     * 保存业务数据范围配置（UPSERT：已存在则更新，不存在则新增）
     *
     * @param req 保存请求DTO（含角色ID、BizType、DataScope 和操作原因）
     * @return 保存后的 BizScopeRespDTO
     */
    @PostMapping("")
    @Operation(summary = "保存业务数据范围配置", description = "存在则更新，不存在则新增（UPSERT 语义）")
    @BizAuth(bizType = BizType.SYS_CONFIG, action = BizAction.PERMISSION_CHANGE)
    @AuditLog(action = "BIZ_SCOPE_SAVE", resourceType = "PT_ROLE_BIZ_SCOPE", reasonRequired = true)
    public ResponseWrapper<BizScopeRespDTO> saveBizScope(@Valid @RequestBody BizScopeSaveReqDTO req) {
        log.info("[BizScopeController.saveBizScope] roleId={}, bizType={}, dataScope={}",
                req.getRoleId(), req.getBizType(), req.getDataScope());
        BizScopeRespDTO dto = bizScopeService.saveBizScope(
                req.getRoleId(), req.getBizType(), req.getDataScope(), req.getReason());
        return ResponseWrapper.success(dto);
    }

    /**
     * 删除业务数据范围配置
     *
     * @param id     PT_ROLE_BIZ_SCOPE 的主键ID（路径参数）
     * @param reason 删除原因（审计用）
     * @return 成功响应
     */
    @DeleteMapping("/{id}")
    @Operation(summary = "删除业务数据范围配置")
    @BizAuth(bizType = BizType.SYS_CONFIG, action = BizAction.PERMISSION_CHANGE)
    public ResponseWrapper<Void> deleteBizScope(
            @PathVariable("id") String id,
            @RequestParam("reason") String reason) {
        log.info("[BizScopeController.deleteBizScope] id={}, reason={}", id, reason);
        bizScopeService.deleteBizScope(id, reason);
        return ResponseWrapper.success();
    }
}
