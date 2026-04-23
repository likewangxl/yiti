package com.bank.branch.platform.performance.controller;

import com.bank.branch.platform.auth.api.CurrentUserApi;
import com.bank.branch.platform.common.aop.annotation.AuditLog;
import com.bank.branch.platform.common.security.annotation.BizAuth;
import com.bank.branch.platform.common.security.enums.BizAction;
import com.bank.branch.platform.common.security.enums.BizType;
import com.bank.branch.platform.common.web.PageResult;
import com.bank.branch.platform.common.web.ResponseWrapper;
import com.bank.branch.platform.performance.controller.dto.AllocAdjustCreateReqDTO;
import com.bank.branch.platform.performance.controller.dto.AllocAdjustRespDTO;
import com.bank.branch.platform.performance.entity.PerfAllocAdjustApply;
import com.bank.branch.platform.performance.entity.PerfAllocAdjustItem;
import com.bank.branch.platform.performance.service.adjust.AllocAdjustService;
import com.bank.branch.platform.performance.service.adjust.cmd.SubmitAllocAdjustCmd;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
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

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

/**
 * 分配关系调整申请 REST 控制器 (V1.2 Q2.4).
 *
 * <p>路径映射：
 * <ul>
 *   <li>POST /api/perf/alloc-adjust/create → 提交申请（@AuditLog reason）</li>
 *   <li>GET /api/perf/alloc-adjust/{id} → 查询详情（含 items）</li>
 *   <li>GET /api/perf/alloc-adjust/list → 分页列表（status/bizKind/custId 过滤）</li>
 *   <li>POST /api/perf/alloc-adjust/{id}/withdraw → 撤回（@AuditLog reason）</li>
 * </ul>
 *
 * <p>单档授权：所有端点 {@code @BizAuth(bizType=PERF_CONFIG, action=<具体动作>)}，
 * 细粒度资源通过 PT_RESOURCE 绑定，当前阶段未新增 P_PERF_ALLOC_ADJ_* 资源条目，
 * 留待 Q8 种子数据阶段补齐.
 */
@Slf4j
@RestController
@RequestMapping("/api/perf/alloc-adjust")
@Tag(name = "Performance Allocation Adjust", description = "分配关系调整申请")
@Validated
@RequiredArgsConstructor
public class AllocAdjustController {

    private final CurrentUserApi currentUserApi;
    private final AllocAdjustService allocAdjustService;

    /**
     * 提交分配关系调整申请.
     *
     * <p>启动对应 BPMN 流程（corp_v1 / retail_v1），返回申请 ID 与初始状态.
     */
    @PostMapping("/create")
    @Operation(summary = "提交分配调整申请")
    @BizAuth(bizType = BizType.PERF_CONFIG, action = BizAction.WRITE)
    @AuditLog(action = "ALLOC_ADJUST_CREATE", resourceType = "PERF_ALLOC_ADJUST", reasonRequired = true)
    public ResponseWrapper<Map<String, String>> create(@Valid @RequestBody AllocAdjustCreateReqDTO req) {
        log.info("[AllocAdjustController.create] custId={}, bizKind={}, itemCount={}",
                req.getCustId(), req.getBizKind(),
                req.getItems() == null ? 0 : req.getItems().size());

        SubmitAllocAdjustCmd cmd = SubmitAllocAdjustCmd.builder()
                .custId(req.getCustId())
                .allocDim(req.getAllocDim())
                .bizKind(req.getBizKind())
                .accountNo(req.getAccountNo())
                .ownerOrgId(req.getOwnerOrgId())
                .reason(req.getReason())
                .applicant(currentUserApi.getCurrentEmpId())
                .items(toCmdItems(req.getItems()))
                .build();

        String id = allocAdjustService.submit(cmd);
        // Submit 后立即查询，返回完整状态给前端
        AllocAdjustService.ApplyWithItems loaded = allocAdjustService.getById(id);
        return ResponseWrapper.success(Map.of(
                "id", loaded.getApply().getId(),
                "applyNo", loaded.getApply().getApplyNo(),
                "status", loaded.getApply().getStatus()));
    }

    /**
     * 查询分配调整申请详情（含 items）.
     */
    @GetMapping("/{id}")
    @Operation(summary = "查询调整申请详情")
    @BizAuth(bizType = BizType.PERF_CONFIG, action = BizAction.READ)
    public ResponseWrapper<AllocAdjustRespDTO> getById(@PathVariable("id") @NotBlank String id) {
        log.debug("[AllocAdjustController.getById] id={}", id);
        AllocAdjustService.ApplyWithItems bundle = allocAdjustService.getById(id);
        return ResponseWrapper.success(toDto(bundle.getApply(), bundle.getItems()));
    }

    /**
     * 分页列表（支持 status / bizKind / custId 过滤）.
     *
     * <p>V1.2 Q2 阶段不叠加 BizScopeApi 的 owner_org_id 数据范围过滤，
     * 留待 Q7 阶段接入（BizScopeApi 注入）.
     */
    @GetMapping("/list")
    @Operation(summary = "分页查询调整申请")
    @BizAuth(bizType = BizType.PERF_CONFIG, action = BizAction.LIST)
    public ResponseWrapper<AllocAdjustRespDTO> list(
            @RequestParam(value = "status", required = false) String status,
            @RequestParam(value = "bizKind", required = false) String bizKind,
            @RequestParam(value = "custId", required = false) String custId,
            @RequestParam(value = "ownerOrgId", required = false) String ownerOrgId,
            @RequestParam(value = "createdBy", required = false) String createdBy,
            @RequestParam(value = "pageNo", defaultValue = "1") @Min(1) int pageNo,
            @RequestParam(value = "pageSize", defaultValue = "20") @Min(1) @Max(100) int pageSize) {
        log.debug("[AllocAdjustController.list] status={}, bizKind={}, custId={}, pageNo={}",
                status, bizKind, custId, pageNo);
        PageResult<PerfAllocAdjustApply> raw = allocAdjustService.page(
                status, bizKind, custId, ownerOrgId, createdBy, pageNo, pageSize);
        List<AllocAdjustRespDTO> dtos = new ArrayList<>(raw.getRecords().size());
        for (PerfAllocAdjustApply apply : raw.getRecords()) {
            // 列表视图不带 items 减少数据库压力
            dtos.add(toDto(apply, Collections.emptyList()));
        }
        return ResponseWrapper.page(PageResult.of(raw.getPageNo(), raw.getPageSize(), raw.getTotal(), dtos));
    }

    /**
     * 撤回申请（IN_APPROVAL / DRAFT → REJECTED）.
     *
     * <p>V1.2 简化：仅置本地状态为 REJECTED，不同步 Flowable 取消流程，
     * 生产完整方案需调 WorkflowApi.cancelProcess（留待后续迭代）.
     */
    @PostMapping("/{id}/withdraw")
    @Operation(summary = "撤回分配调整申请（高危）")
    @BizAuth(bizType = BizType.PERF_CONFIG, action = BizAction.EXECUTE)
    @AuditLog(action = "ALLOC_ADJUST_WITHDRAW", resourceType = "PERF_ALLOC_ADJUST", reasonRequired = true)
    public ResponseWrapper<Void> withdraw(@PathVariable("id") @NotBlank String id,
                                          @RequestBody WithdrawReq req) {
        log.info("[AllocAdjustController.withdraw] id={}, reason={}", id, req == null ? null : req.getReason());
        String reason = req == null ? null : req.getReason();
        allocAdjustService.withdraw(id, reason, currentUserApi.getCurrentEmpId());
        return ResponseWrapper.success();
    }

    /**
     * DTO 装配：Entity → 响应 DTO.
     */
    private AllocAdjustRespDTO toDto(PerfAllocAdjustApply apply, List<PerfAllocAdjustItem> items) {
        AllocAdjustRespDTO dto = new AllocAdjustRespDTO();
        dto.setId(apply.getId());
        dto.setApplyNo(apply.getApplyNo());
        dto.setCustId(apply.getCustId());
        dto.setAllocDim(apply.getAllocDim());
        dto.setBizKind(apply.getBizKind());
        dto.setAccountNo(apply.getAccountNo());
        dto.setStatus(apply.getStatus());
        dto.setBusinessKey(apply.getBusinessKey());
        dto.setProcessInstanceId(apply.getProcessInstanceId());
        dto.setOwnerOrgId(apply.getOwnerOrgId());
        dto.setRemark(apply.getRemark());
        dto.setCreatedBy(apply.getCreatedBy());
        dto.setCreatedTime(apply.getCreatedTime());
        dto.setUpdatedBy(apply.getUpdatedBy());
        dto.setUpdatedTime(apply.getUpdatedTime());
        List<AllocAdjustRespDTO.Item> itemDtos = new ArrayList<>(items == null ? 0 : items.size());
        if (items != null) {
            for (PerfAllocAdjustItem it : items) {
                AllocAdjustRespDTO.Item iDto = new AllocAdjustRespDTO.Item();
                iDto.setId(it.getId());
                iDto.setEmpId(it.getEmpId());
                iDto.setRatio(it.getRatio());
                iDto.setCreatedTime(it.getCreatedTime());
                itemDtos.add(iDto);
            }
        }
        dto.setItems(itemDtos);
        return dto;
    }

    /**
     * 请求 DTO → Service Cmd 的 Item 列表转换（空安全）.
     */
    private List<SubmitAllocAdjustCmd.Item> toCmdItems(List<AllocAdjustCreateReqDTO.Item> reqItems) {
        if (reqItems == null || reqItems.isEmpty()) {
            return Collections.emptyList();
        }
        List<SubmitAllocAdjustCmd.Item> cmds = new ArrayList<>(reqItems.size());
        for (AllocAdjustCreateReqDTO.Item it : reqItems) {
            cmds.add(SubmitAllocAdjustCmd.Item.builder()
                    .empId(it.getEmpId())
                    .ratio(it.getRatio())
                    .build());
        }
        return cmds;
    }

    /**
     * 撤回请求简单 body（仅含 reason）.
     */
    @lombok.Data
    public static class WithdrawReq {
        private String reason;
    }
}
