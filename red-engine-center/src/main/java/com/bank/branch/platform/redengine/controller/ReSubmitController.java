package com.bank.branch.platform.redengine.controller;

import com.bank.branch.platform.auth.api.CurrentUserApi;
import com.bank.branch.platform.common.aop.annotation.AuditLog;
import com.bank.branch.platform.common.security.annotation.BizAuth;
import com.bank.branch.platform.common.security.enums.BizAction;
import com.bank.branch.platform.common.security.enums.BizType;
import com.bank.branch.platform.common.web.PageResult;
import com.bank.branch.platform.common.web.ResponseWrapper;
import com.bank.branch.platform.redengine.api.dto.ReSubmitCreateReqDTO;
import com.bank.branch.platform.redengine.entity.ReSubmit;
import com.bank.branch.platform.redengine.service.ReSubmitService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 红色引擎-材料上报端点。
 * <p>移植自 redengine {@code SubmitController}（{@code business.controller}）。
 * URL 与 Task 4 权限种子对照表严格一致：POST {@code /api/re/submits} 对应资源 P_RE_SUBMIT_ADD，
 * GET {@code /api/re/submits/my} 对应 P_RE_SUBMIT_MY，GET {@code /api/re/submits/{id}} 对应
 * P_RE_SUBMIT_GET（通配符 {@code /api/re/submits/*} 覆盖）。</p>
 * <p>源系统 {@code POST /api/submit/{id}/files}（{@code @Deprecated uploadFile}）本次移植不落地：
 * 附件上传已统一走 governance 既有 {@code POST /api/files/upload}，前端直传拿 fileObjectId 后随
 * {@link #createSubmit} 请求体一并提交，本模块不再自建文件端点（YAGNI）。</p>
 */
@Slf4j
@Tag(name = "红色引擎-材料上报")
@RestController
@RequestMapping("/api/re/submits")
@RequiredArgsConstructor
public class ReSubmitController {

    private final ReSubmitService reSubmitService;
    private final CurrentUserApi currentUserApi;

    @Operation(summary = "新建材料上报")
    @PostMapping
    @BizAuth(bizType = BizType.RED_ENGINE, action = BizAction.WRITE)
    @AuditLog(action = "RE_SUBMIT_ADD", resourceType = "RE_SUBMIT")
    public ResponseWrapper<Long> createSubmit(@Valid @RequestBody ReSubmitCreateReqDTO req) {
        String empId = currentUserApi.getCurrentEmpId();
        Long id = reSubmitService.createSubmit(req, empId);
        log.info("[ReSubmitController.createSubmit] id={}, empId={}", id, empId);
        return ResponseWrapper.success(id);
    }

    @Operation(summary = "我的上报列表(按党组织)")
    @GetMapping("/my")
    @BizAuth(bizType = BizType.RED_ENGINE, action = BizAction.LIST)
    public ResponseWrapper<PageResult<ReSubmit>> getMySubmits(
            @RequestParam(value = "pageNo", defaultValue = "1") int pageNo,
            @RequestParam(value = "pageSize", defaultValue = "10") int pageSize) {
        String empId = currentUserApi.getCurrentEmpId();
        PageResult<ReSubmit> result = reSubmitService.getMySubmits(empId, pageNo, pageSize);
        log.info("[ReSubmitController.getMySubmits] empId={}, total={}", empId, result.getTotal());
        return ResponseWrapper.success(result);
    }

    @Operation(summary = "上报详情")
    @GetMapping("/{id}")
    @BizAuth(bizType = BizType.RED_ENGINE, action = BizAction.READ)
    public ResponseWrapper<ReSubmit> getSubmit(@PathVariable Long id) {
        ReSubmit submit = reSubmitService.getDetail(id);
        log.info("[ReSubmitController.getSubmit] id={}, found={}", id, submit != null);
        return ResponseWrapper.success(submit);
    }
}
