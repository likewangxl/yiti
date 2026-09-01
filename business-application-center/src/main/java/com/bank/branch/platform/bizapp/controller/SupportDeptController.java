package com.bank.branch.platform.bizapp.controller;

import com.bank.branch.platform.auth.api.CurrentUserApi;
import com.bank.branch.platform.bizapp.api.dto.SupportRequestListItemDTO;
import com.bank.branch.platform.bizapp.api.dto.SupportRequestDTO;
import com.bank.branch.platform.bizapp.api.dto.SupportProcessLogDTO;
import com.bank.branch.platform.bizapp.dto.req.CompleteReq;
import com.bank.branch.platform.bizapp.dto.req.CreateSupportProcessLogReq;
import com.bank.branch.platform.bizapp.dto.req.DispatchReq;
import com.bank.branch.platform.bizapp.dto.req.TransferReq;
import com.bank.branch.platform.bizapp.service.SupportDeptService;
import com.bank.branch.platform.bizapp.service.SupportProcessLogService;
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
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 中台支持申请 REST 控制器（承接侧视图）。
 * <p>
 * 提供承接部门的 4 个端点：列表、派单、转交、办理完成。
 * </p>
 */
@Slf4j
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/support-dept/requests")
@Validated
@Tag(name = "中台支持承接侧管理")
public class SupportDeptController {

    private final SupportDeptService supportDeptService;
    private final CurrentUserApi currentUserApi;
    private final SupportProcessLogService supportProcessLogService;

    /**
     * 分页查询支持申请列表（承接侧）。
     * <p>返回 {@link SupportRequestListItemDTO}，不暴露 Entity 内部字段。</p>
     */
    @GetMapping
    @BizAuth(bizType = BizType.SUPPORT_DEPT, action = BizAction.LIST)
    @Operation(summary = "承接侧查询中台支持申请列表")
    public ResponseWrapper<SupportRequestListItemDTO> listPageForDept(
            @RequestParam(required = false) String supportDeptId,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String assignedEmpId,
            @RequestParam(defaultValue = "1") int pageNo,
            @RequestParam(defaultValue = "20") int pageSize) {
        log.info("[SupportDeptController.listPageForDept] deptId={}, pageNo={}, pageSize={}",
                supportDeptId, pageNo, pageSize);
        PageResult<SupportRequestListItemDTO> result = supportDeptService.listPageForDeptAsDTO(
                supportDeptId, status, assignedEmpId, pageNo, pageSize);
        return ResponseWrapper.page(result);
    }

    /** 承接侧详情；服务层按 SUPPORT_DEPT 数据域校验。 */
    @GetMapping("/{id}")
    @BizAuth(bizType = BizType.SUPPORT_DEPT, action = BizAction.READ)
    @Operation(summary = "承接侧查询中台支持详情")
    public ResponseWrapper<SupportRequestDTO> getById(@PathVariable String id) {
        return ResponseWrapper.success(supportDeptService.getByIdAsDTO(
                id, currentUserApi.getCurrentEmpId()));
    }

    /**
     * 派单（承接部门秘书操作）。
     */
    @PostMapping("/{id}/dispatch")
    @BizAuth(bizType = BizType.SUPPORT_DEPT, action = BizAction.WRITE)
    @AuditLog(action = "DISPATCH_SUPPORT_REQUEST", resourceType = "SUPPORT_REQUEST")
    @Operation(summary = "承接侧派单")
    public ResponseWrapper<Void> dispatch(@PathVariable String id,
                                          @Valid @RequestBody DispatchReq req) {
        log.info("[SupportDeptController.dispatch] id={}, assignedEmpId={}", id, req.getAssignedEmpId());
        String empId = currentUserApi.getCurrentEmpId();
        supportDeptService.dispatch(id, req.getAssignedEmpId(), empId, req.getDispatchRemark());
        return ResponseWrapper.success();
    }

    /**
     * 转交承接人（高危操作）。
     */
    @PostMapping("/{id}/transfer")
    @BizAuth(bizType = BizType.SUPPORT_DEPT, action = BizAction.TRANSFER)
    @AuditLog(action = "TRANSFER_SUPPORT_REQUEST", resourceType = "SUPPORT_REQUEST", reasonRequired = true)
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
    @AuditLog(action = "COMPLETE_SUPPORT_REQUEST", resourceType = "SUPPORT_REQUEST")
    @Operation(summary = "承接侧办理完成")
    public ResponseWrapper<Void> complete(@PathVariable String id,
                                          @RequestBody CompleteReq req) {
        log.info("[SupportDeptController.complete] id={}, success={}", id, req.isSuccess());
        String empId = currentUserApi.getCurrentEmpId();
        if (req.getHandleResult() == null && req.getSummary() == null
                && req.getResult() == null && req.getOutputAttachmentIds() == null
                && req.getFileIds() == null) {
            supportDeptService.complete(id, req.isSuccess(), empId);
        } else {
            supportDeptService.complete(id, req, empId);
        }
        return ResponseWrapper.success();
    }

    /** 承接侧只读过程记录。 */
    @GetMapping("/{id}/logs")
    @BizAuth(bizType = BizType.SUPPORT_DEPT, action = BizAction.READ)
    @Operation(summary = "承接侧查询中台支持过程记录")
    public ResponseWrapper<List<SupportProcessLogDTO>> logs(@PathVariable String id) {
        return ResponseWrapper.success(supportProcessLogService.listForDept(
                id, currentUserApi.getCurrentEmpId()));
    }

    /** 当前承接人新增过程记录（含定位和照片关联）。 */
    @PostMapping("/{id}/logs")
    @BizAuth(bizType = BizType.SUPPORT_DEPT, action = BizAction.WRITE)
    @AuditLog(action = "CREATE_SUPPORT_PROCESS_LOG", resourceType = "SUPPORT_LOG")
    @Operation(summary = "新增中台支持过程记录")
    public ResponseWrapper<SupportProcessLogDTO> addLog(@PathVariable String id,
                                                        @Valid @RequestBody CreateSupportProcessLogReq req) {
        return ResponseWrapper.success(supportProcessLogService.addLog(
                id, req, currentUserApi.getCurrentEmpId()));
    }
}
