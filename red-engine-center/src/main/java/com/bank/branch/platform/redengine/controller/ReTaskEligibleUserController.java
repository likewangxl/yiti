package com.bank.branch.platform.redengine.controller;

import com.bank.branch.platform.auth.api.CurrentUserApi;
import com.bank.branch.platform.common.security.annotation.BizAuth;
import com.bank.branch.platform.common.security.enums.BizAction;
import com.bank.branch.platform.common.security.enums.BizType;
import com.bank.branch.platform.common.web.PageResult;
import com.bank.branch.platform.common.web.ResponseWrapper;
import com.bank.branch.platform.redengine.api.dto.ReTaskEligibleUserDTO;
import com.bank.branch.platform.redengine.api.dto.ReTaskEligibleUserPageQueryDTO;
import com.bank.branch.platform.redengine.service.ReTaskEligibleUserService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** 任务指定员工候选项查询入口。 */
@Slf4j
@Tag(name = "红色引擎-任务对象")
@RestController
@RequestMapping("/api/re/tasks")
@RequiredArgsConstructor
public class ReTaskEligibleUserController {

    private final ReTaskEligibleUserService eligibleUserService;
    private final CurrentUserApi currentUserApi;

    /**
     * 查询可用于“指定员工”任务对象的已启用员工。
     * <p>该入口单独登记资源，避免组织审核员被迫申请全局 {@code /api/admin/users} 权限。</p>
     */
    @Operation(summary = "任务可选员工分页")
    @GetMapping("/eligible-users")
    @BizAuth(bizType = BizType.RED_ENGINE, action = BizAction.LIST)
    public ResponseWrapper<ReTaskEligibleUserDTO> page(
            @Valid @ModelAttribute ReTaskEligibleUserPageQueryDTO query) {
        String operatorId = currentUserApi.getCurrentEmpId();
        PageResult<ReTaskEligibleUserDTO> result = eligibleUserService.page(query, operatorId);
        log.info("[ReTaskEligibleUserController.page] operatorId={}, total={}",
                operatorId, result.getTotal());
        return ResponseWrapper.page(result);
    }
}
