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
import com.bank.branch.platform.performance.service.adjust.AllocAdjustService;
import com.bank.branch.platform.performance.service.adjust.cmd.SubmitAllocAdjustCmd;
import com.bank.branch.platform.workflow.api.WorkflowQueryApi;
import com.bank.branch.platform.workflow.api.dto.ApprovalLogDTO;
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
import java.util.Comparator;
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
 *
 * <p>V1.3 R4.1 改造：Controller 不再 import / 使用 entity，装配全部下沉到
 * {@link AllocAdjustService#submitDto} / {@link AllocAdjustService#getByIdDto}
 * / {@link AllocAdjustService#pageDto}.
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
    private final WorkflowQueryApi workflowQueryApi;
    private final com.bank.branch.platform.auth.api.OrgApi orgApi;

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
        log.info("[AllocAdjustController.create] custNo={}, bizKind={}, itemCount={}",
                req.getCustNo(), req.getBizKind(),
                req.getItems() == null ? 0 : req.getItems().size());

        SubmitAllocAdjustCmd cmd = SubmitAllocAdjustCmd.builder()
                .custType(req.getCustType())
                .custNo(req.getCustNo())
                .custName(req.getCustName())
                .currBal(req.getCurrBal())
                .mAvgBal(req.getMAvgBal())
                .qAvgBal(req.getQAvgBal())
                .yAvgBal(req.getYAvgBal())
                .allocDim(req.getAllocDim())
                .bizKind(req.getBizKind())
                .accountNo(req.getAccountNo())
                .ownerOrgId(req.getOwnerOrgId())
                .reason(req.getReason())
                .applicant(currentUserApi.getCurrentEmpId())
                .items(toCmdItems(req.getItems()))
                .originalAllocList(toCmdOriginalItems(req.getOriginalAllocList()))
                .build();

        return ResponseWrapper.success(allocAdjustService.submitDto(cmd));
    }

    /**
     * 分配明细员工号输入框自动补齐：按关键字模糊匹配 PT_USER 工号/登录名/中文名.
     *
     * <p>放在 alloc-adjust 下、与新建/查看申请同 PERF_CONFIG 域 READ 权限，
     * 保证调整申请页用户可调用（不复用 SYS_CONFIG 的 /admin/users，避免业务角色 403）.
     */
    @GetMapping("/emp-suggest")
    @Operation(summary = "员工自动补齐（工号/登录名/中文名模糊匹配）")
    @BizAuth(bizType = BizType.PERF_CONFIG, action = BizAction.READ)
    public ResponseWrapper<List<com.bank.branch.platform.performance.controller.dto.EmpSuggestRespDTO>> empSuggest(
            @RequestParam("keyword") String keyword,
            @RequestParam(value = "limit", required = false) Integer limit) {
        log.debug("[AllocAdjustController.empSuggest] keyword={}, limit={}", keyword, limit);
        return ResponseWrapper.success(allocAdjustService.suggestEmployees(keyword, limit));
    }

    /**
     * 所属机构自动补齐：按机构号/名称模糊匹配 EXT_ORG_INFO，返回 [{orgCode, orgName, ...}].
     * <p>供原业绩分配「所属机构」下拉联想（el-autocomplete）。复用 /api/perf/alloc-adjust/* GET
     * 通配资源 + PERF_CONFIG READ，保证调整申请页用户可调用（不复用 SYS_CONFIG 的 /api/orgs，避免业务角色 403）.
     */
    @GetMapping("/org-suggest")
    @Operation(summary = "机构自动补齐（机构号/名称模糊匹配）")
    @BizAuth(bizType = BizType.PERF_CONFIG, action = BizAction.READ)
    public ResponseWrapper<List<com.bank.branch.platform.auth.api.dto.OrgDTO>> orgSuggest(
            @RequestParam("keyword") String keyword,
            @RequestParam(value = "limit", required = false) Integer limit) {
        log.debug("[AllocAdjustController.orgSuggest] keyword={}, limit={}", keyword, limit);
        int lim = (limit == null || limit <= 0) ? 20 : Math.min(limit, 50);
        return ResponseWrapper.success(orgApi.searchOrgs(keyword, lim));
    }

    /**
     * 查询分配调整申请详情（含 items）.
     */
    @GetMapping("/{id}")
    @Operation(summary = "查询调整申请详情")
    @BizAuth(bizType = BizType.PERF_CONFIG, action = BizAction.READ)
    public ResponseWrapper<AllocAdjustRespDTO> getById(@PathVariable("id") @NotBlank String id) {
        log.debug("[AllocAdjustController.getById] id={}", id);
        return ResponseWrapper.success(allocAdjustService.getByIdDto(id));
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
        PageResult<AllocAdjustRespDTO> dtoPage = allocAdjustService.pageDto(
                status, bizKind, custId, ownerOrgId, createdBy, pageNo, pageSize);
        return ResponseWrapper.page(dtoPage);
    }

    /**
     * 查询审批流记录（时间倒序，最新在上）.
     *
     * <p>聚合 workflow-center {@link WorkflowQueryApi#getProcessHistory}，避免前端跨模块绕路、
     * 并统一走 perf 模块 {@code @BizAuth} 权限链路。
     *
     * <p>边界：
     * <ul>
     *   <li>{@code id} 不存在 → service 抛 PERF-404xx（保持既有语义）</li>
     *   <li>{@code processInstanceId} 为空（DRAFT 尚未启动流程）→ 返回空数组</li>
     *   <li>{@code operateTime} 为 null 的条目排在最末，避免 NPE</li>
     * </ul>
     */
    @GetMapping("/{id}/approval-history")
    @Operation(summary = "查询调整申请审批流记录（时间倒序）")
    @BizAuth(bizType = BizType.PERF_CONFIG, action = BizAction.READ)
    public ResponseWrapper<List<ApprovalLogDTO>> getApprovalHistory(
            @PathVariable("id") @NotBlank String id) {
        log.debug("[AllocAdjustController.getApprovalHistory] id={}", id);
        AllocAdjustRespDTO detail = allocAdjustService.getByIdDto(id);
        String processInstanceId = detail.getProcessInstanceId();
        if (processInstanceId == null || processInstanceId.isEmpty()) {
            return ResponseWrapper.success(Collections.emptyList());
        }
        List<ApprovalLogDTO> logs = workflowQueryApi.getProcessHistory(processInstanceId);
        if (logs == null || logs.isEmpty()) {
            return ResponseWrapper.success(Collections.emptyList());
        }
        List<ApprovalLogDTO> sorted = new ArrayList<>(logs);
        sorted.sort(Comparator.comparing(ApprovalLogDTO::getOperateTime,
                Comparator.nullsLast(Comparator.reverseOrder())));
        return ResponseWrapper.success(sorted);
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
                    .remark(it.getRemark())
                    .build());
        }
        return cmds;
    }

    /**
     * 请求 DTO → Service Cmd 的原业绩分配列表转换（空安全）.
     */
    private List<SubmitAllocAdjustCmd.OriginalItem> toCmdOriginalItems(
            List<AllocAdjustCreateReqDTO.OriginalItem> reqItems) {
        if (reqItems == null || reqItems.isEmpty()) {
            return Collections.emptyList();
        }
        List<SubmitAllocAdjustCmd.OriginalItem> cmds = new ArrayList<>(reqItems.size());
        for (AllocAdjustCreateReqDTO.OriginalItem it : reqItems) {
            cmds.add(SubmitAllocAdjustCmd.OriginalItem.builder()
                    .acctNo(it.getAcctNo())
                    .empId(it.getEmpId())
                    .username(it.getUsername())
                    .empChnName(it.getEmpChnName())
                    .orgCode(it.getOrgCode())
                    .orgName(it.getOrgName())
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
