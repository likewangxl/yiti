package com.bank.branch.platform.performance.controller;

import com.bank.branch.platform.auth.api.CurrentUserApi;
import com.bank.branch.platform.common.aop.annotation.AuditLog;
import com.bank.branch.platform.common.security.annotation.BizAuth;
import com.bank.branch.platform.common.security.enums.BizAction;
import com.bank.branch.platform.common.security.enums.BizType;
import com.bank.branch.platform.common.web.PageResult;
import com.bank.branch.platform.common.web.ResponseWrapper;
import com.bank.branch.platform.performance.api.dto.KpiItemDTO;
import com.bank.branch.platform.performance.api.dto.KpiSchemeDTO;
import com.bank.branch.platform.performance.controller.dto.AddKpiItemReqDTO;
import com.bank.branch.platform.performance.controller.dto.CreateKpiSchemeReqDTO;
import com.bank.branch.platform.performance.controller.dto.PublishKpiSchemeReqDTO;
import com.bank.branch.platform.performance.controller.dto.ReleaseSlotReqDTO;
import com.bank.branch.platform.performance.controller.dto.UpdateKpiItemReqDTO;
import com.bank.branch.platform.performance.controller.dto.UpdateKpiSchemeReqDTO;
import com.bank.branch.platform.performance.service.KpiItemService;
import com.bank.branch.platform.performance.service.KpiSchemeService;
import com.bank.branch.platform.performance.service.cmd.AddKpiItemCmd;
import com.bank.branch.platform.performance.service.cmd.CreateKpiSchemeCmd;
import com.bank.branch.platform.performance.service.cmd.UpdateKpiItemCmd;
import com.bank.branch.platform.performance.service.cmd.UpdateKpiSchemeCmd;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
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

import java.util.ArrayList;
import java.util.List;

/**
 * KPI 方案 REST 控制器 (9 个端点, 对齐 PT_RESOURCE P_PERF_KPI_*).
 *
 * <p>路径映射:
 * <ul>
 *   <li>GET    /api/perf/kpi-schemes                    → P_PERF_KPI_LIST</li>
 *   <li>GET    /api/perf/kpi-schemes/{id}               → P_PERF_KPI_GET</li>
 *   <li>POST   /api/perf/kpi-schemes                    → P_PERF_KPI_ADD</li>
 *   <li>PUT    /api/perf/kpi-schemes/{id}               → P_PERF_KPI_UPD</li>
 *   <li>DELETE /api/perf/kpi-schemes/{id}               → P_PERF_KPI_DEL (高危, reason 必填)</li>
 *   <li>POST   /api/perf/kpi-schemes/{id}/publish       → P_PERF_KPI_PUB (高危, reason 必填)</li>
 *   <li>POST   /api/perf/kpi-schemes/{id}/items         → P_PERF_KPI_IADD</li>
 *   <li>PUT    /api/perf/kpi-schemes/{id}/items/{itemId}→ P_PERF_KPI_IUPD</li>
 *   <li>DELETE /api/perf/kpi-schemes/{id}/items/{itemId}→ P_PERF_KPI_IDEL (高危, reason 必填)</li>
 * </ul>
 *
 * <p>异常策略: Controller 不做 try-catch, PerfException 冒泡至全局异常处理器,
 * 业务错误统一以 200 + 错误码返回。
 *
 * <p>V1.3 R4.1 改造：Controller 不再 import / 使用 entity，所有 CRUD 改调
 * {@link KpiSchemeService#pageDto} / {@link KpiSchemeService#getByIdDto} 等 DTO 方法.
 */
@Slf4j
@RestController
@RequestMapping("/api/perf/kpi-schemes")
@Tag(name = "Performance KPI Scheme", description = "KPI 方案配置")
@Validated
@RequiredArgsConstructor
public class KpiSchemeController {

    private final CurrentUserApi currentUserApi;
    private final KpiSchemeService kpiSchemeService;
    private final KpiItemService kpiItemService;

    /**
     * 分页查询 KPI 方案.
     *
     * <p>过滤条件:
     * <ul>
     *   <li>{@code cycleType} / {@code status}: 精确过滤</li>
     *   <li>{@code keyword}: scheme_code 或 scheme_name 的模糊匹配 (SQL LIKE)</li>
     * </ul>
     * <p>V1.0 不提供 schemeCode 精确过滤 RequestParam (之前曾作为 keyword 特例合并,
     * 语义上会误命中 name 字段, 故移除; 如需精确查询单个方案用 GET /{id} 路径).
     */
    @GetMapping
    @Operation(summary = "分页查询 KPI 方案")
    @BizAuth(bizType = BizType.PERF_CONFIG, action = BizAction.LIST)
    public ResponseWrapper<KpiSchemeDTO> list(
            @RequestParam(value = "cycleType", required = false) String cycleType,
            @RequestParam(value = "status", required = false) String status,
            @RequestParam(value = "keyword", required = false) String keyword,
            @RequestParam(value = "pageNo", defaultValue = "1") @Min(1) int pageNo,
            @RequestParam(value = "pageSize", defaultValue = "20") @Min(1) @Max(100) int pageSize) {
        log.debug("[KpiSchemeController.list] cycleType={}, status={}, keyword={}, pageNo={}, pageSize={}",
                cycleType, status, keyword, pageNo, pageSize);
        PageResult<KpiSchemeDTO> dtoPage = kpiSchemeService.pageDto(cycleType, status, keyword, pageNo, pageSize);
        return ResponseWrapper.page(dtoPage);
    }

    /**
     * 获取 KPI 方案详情 (含方案项).
     */
    @GetMapping("/{id}")
    @Operation(summary = "获取 KPI 方案详情")
    @BizAuth(bizType = BizType.PERF_CONFIG, action = BizAction.READ)
    public ResponseWrapper<KpiSchemeDTO> getById(@PathVariable("id") @NotBlank String id) {
        log.debug("[KpiSchemeController.getById] id={}", id);
        return ResponseWrapper.success(kpiSchemeService.getByIdDto(id));
    }

    /**
     * 新建方案 (父子聚合写入, 单事务).
     */
    @PostMapping
    @Operation(summary = "新增 KPI 方案")
    @BizAuth(bizType = BizType.PERF_CONFIG, action = BizAction.WRITE)
    @AuditLog(action = "CREATE", resourceType = "KPI_SCHEME")
    public ResponseWrapper<KpiSchemeDTO> create(@Valid @RequestBody CreateKpiSchemeReqDTO req) {
        log.info("[KpiSchemeController.create] schemeCode={}, cycleType={}, itemCount={}",
                req.getSchemeCode(), req.getCycleType(),
                req.getItems() == null ? 0 : req.getItems().size());
        CreateKpiSchemeCmd cmd = CreateKpiSchemeCmd.builder()
                .schemeCode(req.getSchemeCode())
                .schemeName(req.getSchemeName())
                .cycleType(req.getCycleType())
                .openDetail(Boolean.TRUE.equals(req.getOpenDetail()) ? 1 : 0)
                .items(toAddItemCmds(req.getItems()))
                .operator(currentUserApi.getCurrentEmpId())
                .build();
        return ResponseWrapper.success(kpiSchemeService.createDto(cmd));
    }

    /**
     * 编辑方案 (部分更新).
     */
    @PutMapping("/{id}")
    @Operation(summary = "编辑 KPI 方案")
    @BizAuth(bizType = BizType.PERF_CONFIG, action = BizAction.WRITE)
    @AuditLog(action = "UPDATE", resourceType = "KPI_SCHEME")
    public ResponseWrapper<KpiSchemeDTO> update(@PathVariable("id") @NotBlank String id,
                                                @Valid @RequestBody UpdateKpiSchemeReqDTO req) {
        log.info("[KpiSchemeController.update] id={}, schemeName={}, cycleType={}, openDetail={}",
                id, req.getSchemeName(), req.getCycleType(), req.getOpenDetail());
        UpdateKpiSchemeCmd cmd = UpdateKpiSchemeCmd.builder()
                .schemeName(req.getSchemeName())
                .cycleType(req.getCycleType())
                .openDetail(req.getOpenDetail() == null ? null : (Boolean.TRUE.equals(req.getOpenDetail()) ? 1 : 0))
                .operator(currentUserApi.getCurrentEmpId())
                .build();
        return ResponseWrapper.success(kpiSchemeService.updateByIdDto(id, cmd));
    }

    /**
     * 删除 / 停用方案 (高危, reason 必填).
     *
     * <p>V1.0 删除映射为停用 (status=DISABLED), 保留历史配置可查。
     */
    @DeleteMapping("/{id}")
    @Operation(summary = "删除 KPI 方案 (高危)")
    @BizAuth(bizType = BizType.PERF_CONFIG, action = BizAction.DELETE)
    @AuditLog(action = "DELETE", resourceType = "KPI_SCHEME", reasonRequired = true)
    public ResponseWrapper<Void> delete(@PathVariable("id") @NotBlank String id,
                                        @Valid @RequestBody ReleaseSlotReqDTO req) {
        log.info("[KpiSchemeController.delete] id={}, reason={}", id, req.getReason());
        kpiSchemeService.disable(id, req.getReason(), currentUserApi.getCurrentEmpId());
        return ResponseWrapper.success();
    }

    /**
     * 发布方案 (高危, reason 必填).
     */
    @PostMapping("/{id}/publish")
    @Operation(summary = "发布 KPI 方案 (高危)")
    @BizAuth(bizType = BizType.PERF_CONFIG, action = BizAction.EXECUTE)
    @AuditLog(action = "PUBLISH", resourceType = "KPI_SCHEME", reasonRequired = true)
    public ResponseWrapper<KpiSchemeDTO> publish(@PathVariable("id") @NotBlank String id,
                                                 @Valid @RequestBody PublishKpiSchemeReqDTO req) {
        log.info("[KpiSchemeController.publish] id={}, reason={}", id, req.getReason());
        return ResponseWrapper.success(kpiSchemeService.publishDto(id, currentUserApi.getCurrentEmpId()));
    }

    /**
     * 方案内新增指标项.
     */
    @PostMapping("/{id}/items")
    @Operation(summary = "方案内新增指标项")
    @BizAuth(bizType = BizType.PERF_CONFIG, action = BizAction.WRITE)
    @AuditLog(action = "CREATE", resourceType = "KPI_SCHEME")
    public ResponseWrapper<KpiItemDTO> addItem(@PathVariable("id") @NotBlank String id,
                                               @Valid @RequestBody AddKpiItemReqDTO req) {
        log.info("[KpiSchemeController.addItem] schemeId={}, metricCode={}, weight={}",
                id, req.getMetricCode(), req.getWeight());
        // 父方案存在性校验已下沉至 KpiItemService.addItem (Service 层), Controller 不再兜底;
        // 这样 V1.1 Facade 路径也能复用同一校验, 同时减少双查询。
        AddKpiItemCmd cmd = AddKpiItemCmd.builder()
                .schemeId(id)
                .metricCode(req.getMetricCode())
                .weight(req.getWeight())
                .multiplier(req.getMultiplier())
                .minScore(req.getMinScore())
                .maxScore(req.getMaxScore())
                .operator(currentUserApi.getCurrentEmpId())
                .build();
        return ResponseWrapper.success(kpiItemService.addItemDto(cmd));
    }

    /**
     * 编辑指标项.
     */
    @PutMapping("/{id}/items/{itemId}")
    @Operation(summary = "编辑指标项")
    @BizAuth(bizType = BizType.PERF_CONFIG, action = BizAction.WRITE)
    @AuditLog(action = "UPDATE", resourceType = "KPI_SCHEME")
    public ResponseWrapper<KpiItemDTO> updateItem(@PathVariable("id") @NotBlank String id,
                                                  @PathVariable("itemId") @NotBlank String itemId,
                                                  @Valid @RequestBody UpdateKpiItemReqDTO req) {
        log.info("[KpiSchemeController.updateItem] schemeId={}, itemId={}", id, itemId);
        UpdateKpiItemCmd cmd = UpdateKpiItemCmd.builder()
                .weight(req.getWeight())
                .multiplier(req.getMultiplier())
                .minScore(req.getMinScore())
                .maxScore(req.getMaxScore())
                .operator(currentUserApi.getCurrentEmpId())
                .build();
        return ResponseWrapper.success(kpiItemService.updateItemDto(itemId, cmd));
    }

    /**
     * 删除指标项 (高危, reason 必填).
     */
    @DeleteMapping("/{id}/items/{itemId}")
    @Operation(summary = "删除指标项 (高危)")
    @BizAuth(bizType = BizType.PERF_CONFIG, action = BizAction.DELETE)
    @AuditLog(action = "DELETE", resourceType = "KPI_SCHEME", reasonRequired = true)
    public ResponseWrapper<Void> deleteItem(@PathVariable("id") @NotBlank String id,
                                            @PathVariable("itemId") @NotBlank String itemId,
                                            @Valid @RequestBody ReleaseSlotReqDTO req) {
        log.info("[KpiSchemeController.deleteItem] schemeId={}, itemId={}, reason={}", id, itemId, req.getReason());
        kpiItemService.deleteItem(itemId, req.getReason(), currentUserApi.getCurrentEmpId());
        return ResponseWrapper.success();
    }

    /**
     * DTO 列表转 Service 层 Cmd 列表 (空安全).
     */
    private List<AddKpiItemCmd> toAddItemCmds(List<AddKpiItemReqDTO> reqItems) {
        if (reqItems == null || reqItems.isEmpty()) {
            return List.of();
        }
        List<AddKpiItemCmd> cmds = new ArrayList<>(reqItems.size());
        for (AddKpiItemReqDTO item : reqItems) {
            cmds.add(AddKpiItemCmd.builder()
                    .metricCode(item.getMetricCode())
                    .weight(item.getWeight())
                    .multiplier(item.getMultiplier())
                    .minScore(item.getMinScore())
                    .maxScore(item.getMaxScore())
                    .build());
        }
        return cmds;
    }
}
