package com.bank.branch.platform.performance.controller;

import com.bank.branch.platform.auth.api.CurrentUserApi;
import com.bank.branch.platform.common.aop.annotation.AuditLog;
import com.bank.branch.platform.common.security.annotation.BizAuth;
import com.bank.branch.platform.common.security.enums.BizAction;
import com.bank.branch.platform.common.security.enums.BizType;
import com.bank.branch.platform.common.web.ResponseWrapper;
import com.bank.branch.platform.performance.controller.dto.InitSysControlReqDTO;
import com.bank.branch.platform.performance.controller.dto.SwitchVersionReqDTO;
import com.bank.branch.platform.performance.entity.SysControl;
import com.bank.branch.platform.performance.enums.BaseDimEnum;
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

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

/**
 * sys_control 版本控制 REST 控制器.
 *
 * <p>对应 4 条 PT_RESOURCE:
 * <ul>
 *   <li>GET /api/perf/sys-control           (P_PERF_SC_GET)</li>
 *   <li>GET /api/perf/sys-control/history   (P_PERF_SC_HIS)</li>
 *   <li>POST /api/perf/sys-control/init     (P_PERF_SC_INIT, @AuditLog INIT)</li>
 *   <li>POST /api/perf/sys-control/switch-version (P_PERF_SC_SW, @AuditLog SWITCH)</li>
 * </ul>
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
     *
     * @param scopeDim 维度 EMP / ORG / CUST
     */
    @GetMapping
    @Operation(summary = "查询当前生效版本")
    @BizAuth(bizType = BizType.PERF_CONFIG, action = BizAction.READ)
    public ResponseWrapper<SysControl> getCurrent(
            @NotBlank @RequestParam("scopeDim") String scopeDim) {
        log.debug("[SysControlController.getCurrent] scopeDim={}", scopeDim);
        SysControl sc = sysControlFacade.getCurrentVersion(scopeDim);
        return ResponseWrapper.success(sc);
    }

    /**
     * 查询指定维度历史版本 (按 latest_data_date 倒序).
     *
     * @param scopeDim 维度
     * @param limit    最多返回条数, 默认 20
     */
    @GetMapping("/history")
    @Operation(summary = "查询历史版本")
    @BizAuth(bizType = BizType.PERF_CONFIG, action = BizAction.READ)
    public ResponseWrapper<List<SysControl>> getHistory(
            @NotBlank @RequestParam("scopeDim") String scopeDim,
            @RequestParam(value = "limit", defaultValue = "20") int limit) {
        log.debug("[SysControlController.getHistory] scopeDim={}, limit={}", scopeDim, limit);
        List<SysControl> list = sysControlFacade.listVersionHistory(scopeDim, limit);
        return ResponseWrapper.success(list);
    }

    /**
     * 初始化 EMP / ORG / CUST 三个维度的版本记录.
     * <p>幂等: 已存在时不重复新增.
     */
    @PostMapping("/init")
    @Operation(summary = "版本初始化 (EMP/ORG/CUST)")
    @BizAuth(bizType = BizType.PERF_CONFIG, action = BizAction.CONFIG)
    @AuditLog(action = "INIT", resourceType = "SYS_CONTROL", reasonRequired = true)
    public ResponseWrapper<List<SysControl>> init(@Valid @RequestBody InitSysControlReqDTO req) {
        log.info("[SysControlController.init] reason={}", req.getReason());
        List<SysControl> initialized = new ArrayList<>();
        // 基线日期使用 1970-01-01 (DDL 默认基线), 初始 version="V_INIT"
        LocalDate baselineDate = LocalDate.of(1970, 1, 1);
        for (BaseDimEnum dim : BaseDimEnum.values()) {
            SysControl sc = sysControlFacade.initIfAbsent(dim.name(), baselineDate, "V_INIT");
            initialized.add(sc);
        }
        return ResponseWrapper.success(initialized);
    }

    /**
     * 手工切换版本 (高危, @AuditLog reasonRequired=true).
     */
    @PostMapping("/switch-version")
    @Operation(summary = "手工切换版本")
    @BizAuth(bizType = BizType.PERF_CONFIG, action = BizAction.CONFIG)
    @AuditLog(action = "SWITCH", resourceType = "SYS_CONTROL", reasonRequired = true)
    public ResponseWrapper<SysControl> switchVersion(@Valid @RequestBody SwitchVersionReqDTO req) {
        log.info("[SysControlController.switchVersion] scopeDim={}, dataDate={}, newVersion={}, reason={}",
                req.getScopeDim(), req.getDataDate(), req.getNewVersion(), req.getReason());

        SwitchVersionCmd cmd = SwitchVersionCmd.builder()
                .scopeDim(req.getScopeDim())
                .dataDate(req.getDataDate())
                .newVersion(req.getNewVersion())
                .reason(req.getReason())
                .operator(currentUserApi.getCurrentEmpId())
                .build();
        SysControl sc = sysControlFacade.switchVersion(cmd);
        return ResponseWrapper.success(sc);
    }
}
