package com.bank.branch.platform.redengine.controller;

import com.bank.branch.platform.auth.api.CurrentUserApi;
import com.bank.branch.platform.common.aop.annotation.AuditLog;
import com.bank.branch.platform.common.security.annotation.BizAuth;
import com.bank.branch.platform.common.security.enums.BizAction;
import com.bank.branch.platform.common.security.enums.BizType;
import com.bank.branch.platform.common.web.PageResult;
import com.bank.branch.platform.common.web.ResponseWrapper;
import com.bank.branch.platform.redengine.api.dto.ReReviewApproveReqDTO;
import com.bank.branch.platform.redengine.api.dto.ReReviewRejectReqDTO;
import com.bank.branch.platform.redengine.entity.ReSubmit;
import com.bank.branch.platform.redengine.service.ReReviewService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.math.BigDecimal;

/**
 * 红色引擎-两级审核端点。
 * <p>移植自 redengine {@code ReviewController}（{@code business.controller}）。
 * URL 与 Task 4 权限种子对照表严格一致：GET {@code /api/re/reviews/queue} 对应资源
 * {@code P_RE_REVIEW_Q}，POST {@code /api/re/reviews/{id}/approve}/{@code reject} 分别对应
 * {@code P_RE_REVIEW_APPR}/{@code P_RE_REVIEW_REJ}。</p>
 * <p>GET {@code /api/re/reviews/{id}/preview} 端点复用同一条 {@code P_RE_REVIEW_Q} 资源
 * （Task 9 控制器裁决：种子里该资源 RESOURCE_URL 由 {@code /api/re/reviews/queue} 调整为
 * {@code /api/re/reviews/**}，覆盖 queue 与 preview 两个 GET 端点，不单独登记新资源）。</p>
 */
@Slf4j
@Tag(name = "红色引擎-两级审核")
@RestController
@RequestMapping("/api/re/reviews")
@RequiredArgsConstructor
public class ReReviewController {

    private final ReReviewService reReviewService;
    private final CurrentUserApi currentUserApi;

    @Operation(summary = "审核待审队列")
    @GetMapping("/queue")
    @BizAuth(bizType = BizType.RED_ENGINE, action = BizAction.LIST)
    public ResponseWrapper<PageResult<ReSubmit>> getReviewQueue(
            @RequestParam(value = "pageNo", defaultValue = "1") int pageNo,
            @RequestParam(value = "pageSize", defaultValue = "10") int pageSize,
            @RequestParam(value = "tab", defaultValue = "PENDING") String tab) {
        String operatorId = currentUserApi.getCurrentEmpId();
        PageResult<ReSubmit> result = reReviewService.getReviewQueue(pageNo, pageSize, tab, operatorId);
        log.info("[ReReviewController.getReviewQueue] pageNo={}, pageSize={}, tab={}, operatorId={}, total={}",
                pageNo, pageSize, tab, operatorId, result.getTotal());
        return ResponseWrapper.success(result);
    }

    @Operation(summary = "上报详情预览(审核用)")
    @GetMapping("/{id}/preview")
    @BizAuth(bizType = BizType.RED_ENGINE, action = BizAction.READ)
    public ResponseWrapper<ReSubmit> getPreview(@PathVariable Long id) {
        ReSubmit submit = reReviewService.getPreview(id);
        log.info("[ReReviewController.getPreview] id={}, found={}", id, submit != null);
        return ResponseWrapper.success(submit);
    }

    @Operation(summary = "审核通过(含评分)")
    @PostMapping("/{id}/approve")
    @BizAuth(bizType = BizType.RED_ENGINE, action = BizAction.WRITE)
    @AuditLog(action = "RE_REVIEW_APPROVE", resourceType = "RE_SUBMIT")
    public ResponseWrapper<Void> approve(@PathVariable Long id,
                                          @RequestBody(required = false) ReReviewApproveReqDTO req) {
        BigDecimal score = req == null ? null : req.getScore();
        String feedback = req == null ? null : req.getFeedback();
        String empId = currentUserApi.getCurrentEmpId();
        reReviewService.approve(id, score, feedback, empId);
        log.info("[ReReviewController.approve] id={}, score={}, empId={}", id, score, empId);
        return ResponseWrapper.success();
    }

    @Operation(summary = "审核驳回")
    @PostMapping("/{id}/reject")
    @BizAuth(bizType = BizType.RED_ENGINE, action = BizAction.WRITE)
    @AuditLog(action = "RE_REVIEW_REJECT", resourceType = "RE_SUBMIT")
    public ResponseWrapper<Void> reject(@PathVariable Long id,
                                         @RequestBody(required = false) ReReviewRejectReqDTO req) {
        String feedback = req == null ? null : req.getFeedback();
        String empId = currentUserApi.getCurrentEmpId();
        reReviewService.reject(id, feedback, empId);
        log.info("[ReReviewController.reject] id={}, empId={}", id, empId);
        return ResponseWrapper.success();
    }
}
