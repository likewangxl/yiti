package com.bank.branch.platform.report.controller;

import com.bank.branch.platform.common.security.annotation.BizAuth;
import com.bank.branch.platform.common.security.enums.BizAction;
import com.bank.branch.platform.common.security.enums.BizType;
import com.bank.branch.platform.common.web.ResponseWrapper;
import com.bank.branch.platform.report.dto.req.ScreenDatasourceSaveReqDTO;
import com.bank.branch.platform.report.dto.req.ScreenDatasourceProbeReqDTO;
import com.bank.branch.platform.report.dto.req.ScreenTryRunReqDTO;
import com.bank.branch.platform.report.dto.resp.ScreenDataRespDTO;
import com.bank.branch.platform.report.dto.resp.ScreenDatasourceRespDTO;
import com.bank.branch.platform.report.dto.resp.ScreenKpiSchemeRespDTO;
import com.bank.branch.platform.report.service.screen.ScreenDatasourceService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 大屏数据源配置管理（管理端）.
 *
 * <p>类级映射为 /api/screen/admin（数据源 CRUD 挂 /datasources 前缀、URL 保持不变），
 * 以便同时承载 KPI 方案下拉端点 /kpi-schemes。</p>
 */
@Slf4j
@RestController
@RequestMapping("/api/screen/admin")
@Tag(name = "大屏-数据源配置", description = "数据源定义 CRUD + 试跑（CUSTOM_SQL 高危独立授权）+ KPI 方案下拉")
@Validated
@RequiredArgsConstructor
public class ScreenDatasourceAdminController {

    private final ScreenDatasourceService datasourceService;

    @GetMapping("/datasources")
    @BizAuth(bizType = BizType.REPORT, action = BizAction.LIST)
    @Operation(summary = "数据源列表（可按能力标签/关键字过滤）")
    public ResponseWrapper<List<ScreenDatasourceRespDTO>> list(
            @RequestParam(required = false) String dsType,
            @RequestParam(required = false) String keyword) {
        return ResponseWrapper.success(datasourceService.list(dsType, keyword));
    }

    @PostMapping("/datasources")
    @BizAuth(bizType = BizType.REPORT, action = BizAction.WRITE)
    @Operation(summary = "新建数据源（宽表引导式保存时翻译槽位）")
    public ResponseWrapper<Long> save(@Valid @RequestBody ScreenDatasourceSaveReqDTO req) {
        return ResponseWrapper.success(datasourceService.save(req));
    }

    @PutMapping("/datasources/{id}")
    @BizAuth(bizType = BizType.REPORT, action = BizAction.WRITE)
    @Operation(summary = "更新数据源")
    public ResponseWrapper<Void> update(@PathVariable Long id,
                                        @Valid @RequestBody ScreenDatasourceSaveReqDTO req) {
        datasourceService.update(id, req);
        return ResponseWrapper.success();
    }

    @DeleteMapping("/datasources/{id}")
    @BizAuth(bizType = BizType.REPORT, action = BizAction.DELETE)
    @Operation(summary = "删除数据源（被区块引用时拒绝；reason 必填）")
    public ResponseWrapper<Void> delete(@PathVariable Long id,
                                        @RequestParam @NotBlank String reason) {
        datasourceService.delete(id, reason);
        return ResponseWrapper.success();
    }

    @PostMapping("/datasources/try-run")
    @BizAuth(bizType = BizType.REPORT, action = BizAction.EXECUTE_SQL)
    @Operation(summary = "配置态试跑（LIMIT 10，高危：白名单校验 + 手工审计）")
    public ResponseWrapper<ScreenDataRespDTO> tryRun(@Valid @RequestBody ScreenTryRunReqDTO req) {
        return ResponseWrapper.success(datasourceService.tryRun(req));
    }

    @PostMapping("/datasources/{id}/probe-columns")
    @BizAuth(bizType = BizType.REPORT, action = BizAction.EXECUTE_SQL)
    @Operation(summary = "已保存数据源列探测（独立高危资源+审计）")
    public ResponseWrapper<ScreenDataRespDTO> probeColumns(@PathVariable Long id,
                                                             @Valid @RequestBody ScreenDatasourceProbeReqDTO req) {
        return ResponseWrapper.success(datasourceService.probeColumns(id, req));
    }

    /** 资源 R_RPT_SCR_KPI_SCH（PT_RESOURCE 注册 SQL 由后续统一脚本处理） */
    @GetMapping("/kpi-schemes")
    @BizAuth(bizType = BizType.REPORT, action = BizAction.LIST)
    @Operation(summary = "KPI 方案下拉（KPI_DETAIL 数据源配置用，仅 ACTIVE）")
    public ResponseWrapper<List<ScreenKpiSchemeRespDTO>> listKpiSchemes() {
        return ResponseWrapper.success(datasourceService.listKpiSchemes());
    }
}
