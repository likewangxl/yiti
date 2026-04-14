package com.bank.branch.platform.bizapp.controller;

import com.bank.branch.platform.auth.api.CurrentUserApi;
import com.bank.branch.platform.bizapp.dto.req.CompleteReq;
import com.bank.branch.platform.bizapp.dto.req.DispatchReq;
import com.bank.branch.platform.bizapp.dto.req.TransferReq;
import com.bank.branch.platform.bizapp.entity.SupportRequest;
import com.bank.branch.platform.bizapp.service.SupportDeptService;
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
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 中场支持申请 REST 控制器（承接侧视图）。
 * <p>
 * 提供承接部门的 4 个端点：列表、派单、转交、办理完成。
 * </p>
 */
@Slf4j
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/support-dept/requests")
@Validated
@Tag(name = "中场支持承接侧管理")
public class SupportDeptController {

    private final SupportDeptService supportDeptService;
    private final CurrentUserApi currentUserApi;

    /**
     * 分页查询支持申请列表（承接侧）。
     */
    @GetMapping
    @BizAuth(bizType = BizType.SUPPORT_DEPT, action = BizAction.LIST)
    @Operation(summary = "承接侧查询中场支持申请列表")
    public ResponseWrapper<SupportRequest> listPageForDept(
            @RequestParam(required = false) String supportDeptId,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String assignedEmpId,
            @RequestParam(defaultValue = "1") int pageNo,
            @RequestParam(defaultValue = "20") int pageSize) {
        log.info("[SupportDeptController.listPageForDept] deptId={}, pageNo={}, pageSize={}",
                supportDeptId, pageNo, pageSize);
        PageResult<SupportRequest> result = supportDeptService.listPageForDept(
                supportDeptId, status, assignedEmpId, pageNo, pageSize);
        return ResponseWrapper.page(result);
    }

    /**
     * 派单（承接部门秘书操作）。
     */
    @PostMapping("/{id}/dispatch")
    @BizAuth(bizType = BizType.SUPPORT_DEPT, action = BizAction.WRITE)
    @Operation(summary = "承接侧派单")
    public ResponseWrapper<Void> dispatch(@PathVariable String id,
                                          @Valid @RequestBody DispatchReq req) {
        log.info("[SupportDeptController.dispatch] id={}, assignedEmpId={}", id, req.getAssignedEmpId());
        String empId = currentUserApi.getCurrentEmpId();
        supportDeptService.dispatch(id, req.getAssignedEmpId(), empId);
        return ResponseWrapper.success();
    }

    /**
     * 转交承接人（高危操作）。
     */
    @PostMapping("/{id}/transfer")
    @BizAuth(bizType = BizType.SUPPORT_DEPT, action = BizAction.TRANSFER)
    @Operation(summary = "承接侧转交承接人")
    public ResponseWrapper<Void> transfer(@PathVariable String id,
                                          @Valid @RequestBody TransferReq req) {
        log.info("[SupportDeptController.transfer] id={}, newAssigned={}", id, req.getNewAssignedEmpId());
        String empId = currentUserApi.getCurrentEmpId();
        supportDeptService.transfer(id, req.getNewAssignedEmpId(), empId);
        return ResponseWrapper.success();
    }

    /**
     * 办理完成。
     */
    @PostMapping("/{id}/complete")
    @BizAuth(bizType = BizType.SUPPORT_DEPT, action = BizAction.WRITE)
    @Operation(summary = "承接侧办理完成")
    public ResponseWrapper<Void> complete(@PathVariable String id,
                                          @RequestBody CompleteReq req) {
        log.info("[SupportDeptController.complete] id={}, success={}", id, req.isSuccess());
        String empId = currentUserApi.getCurrentEmpId();
        supportDeptService.complete(id, req.isSuccess(), empId);
        return ResponseWrapper.success();
    }
}
