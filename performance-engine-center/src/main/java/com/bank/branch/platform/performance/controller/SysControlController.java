package com.bank.branch.platform.performance.controller;

import com.bank.branch.platform.auth.api.CurrentUserApi;
import com.bank.branch.platform.common.aop.annotation.AuditLog;
import com.bank.branch.platform.common.security.annotation.BizAuth;
import com.bank.branch.platform.common.security.enums.BizAction;
import com.bank.branch.platform.common.security.enums.BizType;
import com.bank.branch.platform.common.web.ResponseWrapper;
import com.bank.branch.platform.performance.controller.dto.InitSysControlReqDTO;
import com.bank.branch.platform.performance.controller.dto.RollbackReqDTO;
import com.bank.branch.platform.performance.controller.dto.SwitchVersionReqDTO;
import com.bank.branch.platform.performance.controller.dto.SysControlRespDTO;
import com.bank.branch.platform.performance.facade.SysControlFacade;
import com.bank.branch.platform.performance.service.cmd.SwitchVersionCmd;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * sys_control 版本控制 REST 控制器.
 *
 * <p>对应 PT_RESOURCE:
 * <ul>
 *   <li>GET /api/perf/sys-control           (P_PERF_SC_GET)</li>
 *   <li>GET /api/perf/sys-control/history   (P_PERF_SC_HIS)</li>
 *   <li>POST /api/perf/sys-control/init     (P_PERF_SC_INIT, @AuditLog INIT)</li>
 *   <li>POST /api/perf/sys-control/switch-version (P_PERF_SC_SW, @AuditLog SWITCH)</li>
 *   <li>POST /api/perf/sys-control/rollback (P_PERF_SYS_CONTROL_ROLLBACK, @AuditLog SYS_CONTROL_ROLLBACK 高危, V1.2 Q1.2)</li>
 * </ul>
 *
 * <p>V1.3 R4.1 改造：Controller 不再 import / 使用 entity，所有装配下沉到
 * {@link SysControlFacade} 的 {@code xxxDto} 方法。
 */
@Slf4j
@RestController
@RequestMapping("/api/perf/sys-control")
@Tag(name = "绩效-版本控制", description = "sys_control 表版本管理")
@RequiredArgsConstructor
public class SysControlController {

    private final SysControlFacade sysControlFacade;
    private final CurrentUserApi currentUserApi;

    /**
     * 查询指定维度当前生效版本.
     * 返回 SysControlRespDTO，不暴露 entity 内部字段。
     *
     * @param scopeDim 维度 EMP / ORG / CUST
     */
    @GetMapping
    @Operation(summary = "查询当前生效版本")
    @BizAuth(bizType = BizType.PERF_CONFIG, action = BizAction.READ)
    public ResponseWrapper<SysControlRespDTO> getCurrent(
            @NotBlank @RequestParam("scopeDim") String scopeDim) {
        log.debug("[SysControlController.getCurrent] scopeDim={}", scopeDim);
        return ResponseWrapper.success(sysControlFacade.getCurrentVersionDto(scopeDim));
    }

    /**
     * 查询指定维度历史版本 (按 latest_data_date 倒序).
     * 返回 SysControlRespDTO 列表，不暴露 entity 内部字段。
     *
     * @param scopeDim 维度
     * @param limit    最多返回条数, 默认 20
     */
    @GetMapping("/history")
    @Operation(summary = "查询历史版本")
    @BizAuth(bizType = BizType.PERF_CONFIG, action = BizAction.READ)
    public ResponseWrapper<List<SysControlRespDTO>> getHistory(
            @NotBlank @RequestParam("scopeDim") String scopeDim,
            @RequestParam(value = "limit", defaultValue = "20") int limit) {
        log.debug("[SysControlController.getHistory] scopeDim={}, limit={}", scopeDim, limit);
        return ResponseWrapper.success(sysControlFacade.listVersionHistoryDto(scopeDim, limit));
    }

    /**
     * 初始化 EMP / ORG / CUST 三个维度的版本记录.
     * <p>幂等: 已存在时不重复新增.
     * 返回 SysControlRespDTO 列表，不暴露 entity 内部字段。
     */
    @PostMapping("/init")
    @Operation(summary = "版本初始化 (EMP/ORG/CUST)")
    @BizAuth(bizType = BizType.PERF_CONFIG, action = BizAction.CONFIG)
    @AuditLog(action = "INIT", resourceType = "SYS_CONTROL", reasonRequired = true)
    public ResponseWrapper<List<SysControlRespDTO>> init(@Valid @RequestBody InitSysControlReqDTO req) {
        log.info("[SysControlController.init] reason={}", req.getReason());
        // V1.3 R4.1：Facade.initAllDto 内部完成 BaseDimEnum 遍历 + 默认 baseline
        return ResponseWrapper.success(sysControlFacade.initAllDto());
    }

    /**
     * 手工切换版本 (高危, @AuditLog reasonRequired=true).
     * 返回 SysControlRespDTO，不暴露 entity 内部字段。
     */
    @PostMapping("/switch-version")
    @Operation(summary = "手工切换版本")
    @BizAuth(bizType = BizType.PERF_CONFIG, action = BizAction.CONFIG)
    @AuditLog(action = "SWITCH", resourceType = "SYS_CONTROL", reasonRequired = true)
    public ResponseWrapper<SysControlRespDTO> switchVersion(@Valid @RequestBody SwitchVersionReqDTO req) {
        log.info("[SysControlController.switchVersion] scopeDim={}, dataDate={}, newVersion={}, reason={}",
                req.getScopeDim(), req.getDataDate(), req.getNewVersion(), req.getReason());

        SwitchVersionCmd cmd = SwitchVersionCmd.builder()
                .scopeDim(req.getScopeDim())
                .dataDate(req.getDataDate())
                .newVersion(req.getNewVersion())
                .reason(req.getReason())
                .operator(currentUserApi.getCurrentEmpId())
                .build();
        return ResponseWrapper.success(sysControlFacade.switchVersionDto(cmd));
    }

    /**
     * 回滚到历史版本（V1.2 Q1.2）.
     *
     * <p>高危操作：{@code @AuditLog(reasonRequired=true)}，
     * 权限由 P_PERF_SYS_CONTROL_ROLLBACK（绑定"绩效管理员"角色）控制。
     *
     * <p>返回 SysControlRespDTO，不暴露 entity 内部字段。
     */
    @PostMapping("/rollback")
    @Operation(summary = "回滚到历史版本（高危）")
    @BizAuth(bizType = BizType.PERF_CONFIG, action = BizAction.EXECUTE)
    @AuditLog(action = "SYS_CONTROL_ROLLBACK", resourceType = "SYS_CONTROL", reasonRequired = true)
    public ResponseWrapper<SysControlRespDTO> rollback(@Valid @RequestBody RollbackReqDTO req) {
        log.info("[SysControlController.rollback] scopeDim={}, rollbackTo={}, reason={}",
                req.getScopeDim(), req.getRollbackTo(), req.getReason());
        return ResponseWrapper.success(sysControlFacade.rollbackDto(
                req.getScopeDim(), req.getRollbackTo(), req.getReason(),
                currentUserApi.getCurrentEmpId()));
    }
}
