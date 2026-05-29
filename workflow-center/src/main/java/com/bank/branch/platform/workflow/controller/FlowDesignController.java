package com.bank.branch.platform.workflow.controller;

import com.bank.branch.platform.auth.api.CurrentUserApi;
import com.bank.branch.platform.common.security.annotation.BizAuth;
import com.bank.branch.platform.common.security.enums.BizAction;
import com.bank.branch.platform.common.security.enums.BizType;
import com.bank.branch.platform.common.web.ResponseWrapper;
import com.bank.branch.platform.workflow.api.dto.flow.FlowDefDTO;
import com.bank.branch.platform.workflow.api.dto.flow.FlowGraphDTO;
import com.bank.branch.platform.workflow.api.dto.flow.FlowVariableDTO;
import com.bank.branch.platform.workflow.service.flow.FlowDefService;
import com.bank.branch.platform.workflow.service.flow.FlowImportService;
import com.bank.branch.platform.workflow.service.flow.FlowPublishService;
import com.bank.branch.platform.workflow.service.flow.FlowVariableCatalog;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.extern.slf4j.Slf4j;
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
 * 审批流程设计器管理 API
 * <p>
 * 提供流程定义草稿的 CRUD、发布、以及条件变量目录查询接口。
 * URL 前缀：{@code /api/admin/workflow/flows}，全部需要 SYS_CONFIG + CONFIG 权限。
 * </p>
 *
 * <pre>
 * 端点汇总：
 *   GET    /api/admin/workflow/flows                    - 查询所有流程定义列表
 *   GET    /api/admin/workflow/flows/{id}               - 获取指定流程图（节点+连线）
 *   POST   /api/admin/workflow/flows                    - 新建流程定义草稿
 *   PUT    /api/admin/workflow/flows/{id}               - 整图替换保存
 *   POST   /api/admin/workflow/flows/{id}/publish       - 发布流程定义到 Flowable
 *   DELETE /api/admin/workflow/flows/{id}               - 删除草稿
 *   GET    /api/admin/workflow/flows/meta/variables     - 查询可用流程变量目录
 *   POST   /api/admin/workflow/flows/import-existing    - 导入内置已部署流程（幂等）
 * </pre>
 */
@Slf4j
@RestController
@RequestMapping("/api/admin/workflow/flows")
@Tag(name = "审批流程设计器", description = "流程定义草稿管理与发布")
public class FlowDesignController {

    private final FlowDefService flowDefService;
    private final FlowPublishService flowPublishService;
    private final FlowVariableCatalog flowVariableCatalog;
    private final CurrentUserApi currentUserApi;
    private final FlowImportService flowImportService;

    /**
     * 构造注入（便于单元测试 mock）。
     *
     * @param flowDefService      流程定义 CRUD 服务
     * @param flowPublishService  流程发布服务
     * @param flowVariableCatalog 流程变量白名单目录
     * @param currentUserApi      当前用户工号获取
     * @param flowImportService   现有已部署流程反向导入服务
     */
    public FlowDesignController(FlowDefService flowDefService,
                                FlowPublishService flowPublishService,
                                FlowVariableCatalog flowVariableCatalog,
                                CurrentUserApi currentUserApi,
                                FlowImportService flowImportService) {
        this.flowDefService = flowDefService;
        this.flowPublishService = flowPublishService;
        this.flowVariableCatalog = flowVariableCatalog;
        this.currentUserApi = currentUserApi;
        this.flowImportService = flowImportService;
    }

    // ── 注意：/meta/variables 必须在 /{id} 之前声明，
    //   Spring MVC 对精确路径优先匹配，实际无需此注释，但显式靠前可读性更佳。

    /**
     * 查询指定业务类型的可用流程变量目录（条件构造器下拉源）。
     * <p>
     * 仅白名单内字段可用于条件分支，防止注入。
     * </p>
     *
     * @param bizType 业务类型（如 ALLOC_ADJUST / TARGET_ADJUST）
     * @return 可用流程变量 DTO 列表
     */
    @GetMapping("/meta/variables")
    @Operation(summary = "查询可用流程变量目录")
    @BizAuth(bizType = BizType.SYS_CONFIG, action = BizAction.CONFIG)
    public ResponseWrapper<List<FlowVariableDTO>> variables(
            @RequestParam("bizType") String bizType) {
        log.debug("[FlowDesignController.variables] bizType={}", bizType);
        List<FlowVariableDTO> vars = flowVariableCatalog.variables(bizType);
        return ResponseWrapper.success(vars);
    }

    // ── 查询 ──────────────────────────────────────────────────────────────

    /**
     * 查询所有流程定义列表（不含图形数据）。
     *
     * @return 流程定义 DTO 列表
     */
    @GetMapping
    @Operation(summary = "查询流程定义列表")
    @BizAuth(bizType = BizType.SYS_CONFIG, action = BizAction.CONFIG)
    public ResponseWrapper<List<FlowDefDTO>> list() {
        log.debug("[FlowDesignController.list]");
        List<FlowDefDTO> result = flowDefService.listAll();
        return ResponseWrapper.success(result);
    }

    /**
     * 获取指定流程定义的完整图形数据（节点 + 连线 + 审批人）。
     *
     * @param id 流程定义ID
     * @return 流程图 DTO
     */
    @GetMapping("/{id}")
    @Operation(summary = "获取流程图")
    @BizAuth(bizType = BizType.SYS_CONFIG, action = BizAction.CONFIG)
    public ResponseWrapper<FlowGraphDTO> getGraph(
            @PathVariable("id") String id) {
        log.debug("[FlowDesignController.getGraph] id={}", id);
        FlowGraphDTO graph = flowDefService.getGraph(id);
        return ResponseWrapper.success(graph);
    }

    // ── 写操作 ─────────────────────────────────────────────────────────────

    /**
     * 新建流程定义草稿，并保存初始图形数据。
     *
     * @param graph 流程图 DTO（含 bizType、name、nodes、edges）
     * @return 新建流程定义的 ID
     */
    @PostMapping
    @Operation(summary = "新建流程定义草稿")
    @BizAuth(bizType = BizType.SYS_CONFIG, action = BizAction.CONFIG)
    public ResponseWrapper<String> create(
            @RequestBody FlowGraphDTO graph) {
        String operator = currentUserApi.getCurrentEmpId();
        log.info("[FlowDesignController.create] bizType={}, operator={}", graph.getBizType(), operator);
        String flowDefId = flowDefService.create(graph, operator);
        return ResponseWrapper.success(flowDefId);
    }

    /**
     * 整图替换保存流程图（先删旧节点/连线，再插入新数据）。
     *
     * @param id    流程定义ID
     * @param graph 新的流程图 DTO
     * @return 成功响应
     */
    @PutMapping("/{id}")
    @Operation(summary = "保存流程图（整图替换）")
    @BizAuth(bizType = BizType.SYS_CONFIG, action = BizAction.CONFIG)
    public ResponseWrapper<Void> save(
            @PathVariable("id") String id,
            @RequestBody FlowGraphDTO graph) {
        String operator = currentUserApi.getCurrentEmpId();
        log.info("[FlowDesignController.save] id={}, operator={}", id, operator);
        flowDefService.saveGraph(id, graph, operator);
        return ResponseWrapper.success();
    }

    /**
     * 发布流程定义到 Flowable（DRAFT → PUBLISHED，生成 Flowable 流程定义）。
     *
     * @param id 流程定义ID
     * @return 成功响应
     */
    @PostMapping("/{id}/publish")
    @Operation(summary = "发布流程定义")
    @BizAuth(bizType = BizType.SYS_CONFIG, action = BizAction.CONFIG)
    public ResponseWrapper<Void> publish(
            @PathVariable("id") String id) {
        String operator = currentUserApi.getCurrentEmpId();
        log.info("[FlowDesignController.publish] id={}, operator={}", id, operator);
        flowPublishService.publish(id, operator);
        return ResponseWrapper.success();
    }

    /**
     * 删除草稿流程定义（级联删除节点、连线、审批人）。
     * <p>
     * 仅 DRAFT 且非只读导入的流程允许删除。
     * </p>
     *
     * @param id 流程定义ID
     * @return 成功响应
     */
    @DeleteMapping("/{id}")
    @Operation(summary = "删除草稿流程定义")
    @BizAuth(bizType = BizType.SYS_CONFIG, action = BizAction.CONFIG)
    public ResponseWrapper<Void> delete(
            @PathVariable("id") String id) {
        log.info("[FlowDesignController.delete] id={}", id);
        flowDefService.deleteDraft(id);
        return ResponseWrapper.success();
    }

    // ── 导入 ────────────────────────────────────────────────────────────────

    /**
     * 导入内置已部署流程为只读流程定义（幂等）。
     * <p>
     * 内置 3 个 Flowable procKey：
     * <ul>
     *   <li>{@code perf_target_adjust_v1}        — 绩效目标调整</li>
     *   <li>{@code perf_alloc_adjust_corp_v1}     — 公司条线调配调整</li>
     *   <li>{@code perf_alloc_adjust_retail_v1}   — 零售条线调配调整</li>
     * </ul>
     * 逐个调用 {@link FlowImportService#importFromDeployed(String)}；
     * 单个 key 失败时不中断其它 key，将错误信息收入结果列表。
     * </p>
     *
     * @return 各 key 的导入结果（flowDefId 或简要错误串）
     */
    @PostMapping("/import-existing")
    @Operation(summary = "导入内置已部署流程（幂等）")
    @BizAuth(bizType = BizType.SYS_CONFIG, action = BizAction.CONFIG)
    public ResponseWrapper<List<String>> importExisting() {
        // 内置 3 个需反向导入的 Flowable 流程定义 KEY
        List<String> procKeys = List.of(
                "perf_target_adjust_v1",
                "perf_alloc_adjust_corp_v1",
                "perf_alloc_adjust_retail_v1"
        );
        log.info("[FlowDesignController.importExisting] 开始导入内置流程，共 {} 个 key", procKeys.size());

        List<String> results = new ArrayList<>();
        for (String procKey : procKeys) {
            try {
                // 幂等：已存在时 importFromDeployed 直接返回已有 flowDefId
                String flowDefId = flowImportService.importFromDeployed(procKey);
                log.info("[FlowDesignController.importExisting] 导入成功: procKey={}, flowDefId={}", procKey, flowDefId);
                results.add(flowDefId);
            } catch (Exception e) {
                // 单个 key 失败不阻断其它，记入结果并继续
                String errMsg = "FAILED:" + procKey + "(" + e.getMessage() + ")";
                log.warn("[FlowDesignController.importExisting] 导入失败，已跳过: procKey={}, err={}", procKey, e.getMessage());
                results.add(errMsg);
            }
        }

        log.info("[FlowDesignController.importExisting] 完成，results={}", results);
        return ResponseWrapper.success(results);
    }
}
