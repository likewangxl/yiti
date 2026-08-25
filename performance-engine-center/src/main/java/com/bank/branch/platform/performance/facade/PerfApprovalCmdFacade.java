package com.bank.branch.platform.performance.facade;

import com.bank.branch.platform.auth.api.UserApi;
import com.bank.branch.platform.auth.api.dto.UserDTO;
import com.bank.branch.platform.performance.api.PerfApprovalCmdApi;
import com.bank.branch.platform.performance.api.dto.AllocAdjustSubmitCmd;
import com.bank.branch.platform.performance.service.adjust.AllocAdjustService;
import com.bank.branch.platform.performance.service.adjust.cmd.SubmitAllocAdjustCmd;
import com.bank.branch.platform.workflow.api.TodoQueryApi;
import com.bank.branch.platform.workflow.api.WorkflowApi;
import com.bank.branch.platform.workflow.api.dto.TaskRespDTO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 绩效审批「写」对外 Facade（{@link PerfApprovalCmdApi} 实现）。
 *
 * <p>委托项目内已有的 {@link AllocAdjustService#submit}，业务规则与 Web 管理端创建端点一致。
 * 渠道差异由本层兜底：{@code ownerOrgId} 为空时按 {@code applicant} 工号反查主机构。</p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PerfApprovalCmdFacade implements PerfApprovalCmdApi {

    /** 渠道撤回未传原因时的默认审计文案。 */
    private static final String DEFAULT_WITHDRAW_REASON = "手机端撤回";

    /** 分配关系调整在 workflow 的 businessKey 前缀（与 AllocAdjustService 落库口径一致）。 */
    private static final String BIZ_KEY_PREFIX = "ALLOC_ADJUST:";
    /** 审批结论码：通过 / 驳回（对齐手机端 apprStatus）。 */
    private static final String APPR_PASS = "1";
    private static final String APPR_REJECT = "2";
    /** 审批意见缺省时的默认审计文案。 */
    private static final String DEFAULT_APPROVE_OPINION = "手机端审批通过";
    private static final String DEFAULT_REJECT_OPINION = "手机端驳回";

    /** 排他网关路由变量名（对齐 perf_alloc_adjust_corp_v1 / perf_alloc_adjust_retail_v1 BPMN）。 */
    private static final String VAR_CORP_ROUTE_TO = "corpRouteTo";
    private static final String VAR_FIN_ROUTE_TO = "finRouteTo";
    /** 含排他网关的经办审批节点 key（对齐 BPMN userTask id）。 */
    private static final String NODE_BIZ_DEPT_REVIEW = "biz_dept_review";
    private static final String NODE_FINANCE_REVIEW = "finance_review";
    /** 手机端「同意」不带路由选择时的默认路由：走最全审批链路。 */
    private static final String DEFAULT_CORP_ROUTE = "OWNER";
    private static final String DEFAULT_FIN_ROUTE = "LEADER";

    private final AllocAdjustService allocAdjustService;
    private final UserApi userApi;
    private final TodoQueryApi todoQueryApi;
    private final WorkflowApi workflowApi;

    @Override
    public String submitAllocAdjust(AllocAdjustSubmitCmd cmd) {
        // ownerOrgId 渠道未传时按申请人工号反查主机构（管理端由表单带入，此处兜底）
        String ownerOrgId = cmd.getOwnerOrgId();
        if (!StringUtils.hasText(ownerOrgId)) {
            ownerOrgId = resolveOwnerOrg(cmd.getApplicant());
        }

        SubmitAllocAdjustCmd serviceCmd = SubmitAllocAdjustCmd.builder()
                .custType(cmd.getCustType())
                .custId(cmd.getCustId())
                .custName(cmd.getCustName())
                .allocDim(cmd.getAllocDim())
                .bizKind(cmd.getBizKind())
                .accountNo(cmd.getAccountNo())
                .ownerOrgId(ownerOrgId)
                .reason(cmd.getReason())
                .applicant(cmd.getApplicant())
                .items(toServiceItems(cmd.getItems()))
                .originalAllocList(toServiceOriginalItems(cmd.getOriginalAllocList()))
                .build();

        String applyId = allocAdjustService.submit(serviceCmd);
        log.info("[PerfApprovalCmdFacade.submitAllocAdjust] applicant={}, custId={}, applyId={}",
                cmd.getApplicant(), cmd.getCustId(), applyId);
        return applyId;
    }

    @Override
    public void withdrawAllocAdjust(String perfAdjustNo, String operator, String reason) {
        // 渠道未填撤回原因时兜底默认文案（审计留痕用，后端不强制 reason）
        String effectiveReason = StringUtils.hasText(reason) ? reason : DEFAULT_WITHDRAW_REASON;
        allocAdjustService.withdrawByApplicant(perfAdjustNo, effectiveReason, operator);
        log.info("[PerfApprovalCmdFacade.withdrawAllocAdjust] perfAdjustNo={}, operator={}",
                perfAdjustNo, operator);
    }

    @Override
    public void approveAllocAdjust(String perfAdjustNo, String empId, String apprStatus, String opinion,
                                   String routeTo) {
        String businessKey = BIZ_KEY_PREFIX + perfAdjustNo;
        // 候选组/角色可见性即权限校验：按 empId 解析其可见的当前待办 taskId，查不到=无权审批
        Map<String, TaskRespDTO> taskMap =
                todoQueryApi.findTaskRespByBusinessKeysByEmp(empId, List.of(businessKey));
        TaskRespDTO task = taskMap.get(businessKey);
        if (task == null || !StringUtils.hasText(task.getTaskId())) {
            throw new IllegalStateException("未找到待审批任务或无权审批: " + perfAdjustNo);
        }
        String taskId = task.getTaskId();

        if (APPR_PASS.equals(apprStatus)) {
            String op = StringUtils.hasText(opinion) ? opinion : DEFAULT_APPROVE_OPINION;
            // 网关路由：与管理端经办审批一致，按「当前节点」写对应排他网关变量，审批人在 callpu 端
            // 选择下一步去向（biz_dept_review→corpRouteTo: LEADER/OWNER；finance_review→finRouteTo: LEADER/END）。
            // 未传选择（老渠道）时按默认走最全链路（corp→OWNER、fin→LEADER），保证排他网关有分支命中，
            // 否则 complete() 抛异常、流程到不了 <end>、状态无法回写 APPROVED。
            boolean newDimension = isNewDimension(perfAdjustNo);
            Map<String, Object> routeVars = buildRouteVars(task.getNodeKey(), routeTo, newDimension);
            workflowApi.approveByEmp(taskId, empId, op, routeVars);
            log.info("[PerfApprovalCmdFacade.approveAllocAdjust] 通过 perfAdjustNo={}, empId={}, taskId={}, "
                            + "nodeKey={}, routeVars={}",
                    perfAdjustNo, empId, taskId, task.getNodeKey(), routeVars);
        } else if (APPR_REJECT.equals(apprStatus)) {
            String op = StringUtils.hasText(opinion) ? opinion : DEFAULT_REJECT_OPINION;
            workflowApi.rejectByEmp(taskId, empId, op);
            log.info("[PerfApprovalCmdFacade.approveAllocAdjust] 驳回 perfAdjustNo={}, empId={}, taskId={}",
                    perfAdjustNo, empId, taskId);
        } else {
            throw new IllegalArgumentException("审批状态不合法: " + apprStatus);
        }
    }

    /**
     * 按「当前经办节点」装配排他网关路由变量。
     *
     * <ul>
     *   <li>{@code biz_dept_review}（公司部/零售部经办）→ 仅写 {@code corpRouteTo}（LEADER/OWNER）；</li>
     *   <li>{@code finance_review}（资财部经办）→ 仅写 {@code finRouteTo}（LEADER/END）；</li>
     *   <li>其余节点（部门负责人、原业绩会签等无后继排他网关）→ 不写路由变量；</li>
     *   <li>nodeKey 缺失（老数据/兜底）→ 同时按默认下发两网关变量，保持老渠道兼容不卡流程。</li>
     * </ul>
     * 经办节点未传 {@code routeTo} 时回退默认（corp→OWNER、fin→LEADER）。
     */
    private Map<String, Object> buildRouteVars(String nodeKey, String routeTo, boolean newDimension) {
        Map<String, Object> routeVars = new HashMap<>();
        if (NODE_BIZ_DEPT_REVIEW.equals(nodeKey)) {
            if (newDimension && StringUtils.hasText(routeTo) && !"LEADER".equals(routeTo)) {
                throw new IllegalArgumentException("NEW（新开户）维度不支持OWNER原业绩分配审批路由");
            }
            routeVars.put(VAR_CORP_ROUTE_TO,
                    StringUtils.hasText(routeTo) ? routeTo : (newDimension ? "LEADER" : DEFAULT_CORP_ROUTE));
        } else if (NODE_FINANCE_REVIEW.equals(nodeKey)) {
            routeVars.put(VAR_FIN_ROUTE_TO, StringUtils.hasText(routeTo) ? routeTo : DEFAULT_FIN_ROUTE);
        } else if (!StringUtils.hasText(nodeKey)) {
            // nodeKey 缺失：无法定位当前网关，按老逻辑同时下发两默认变量兜底
            routeVars.put(VAR_CORP_ROUTE_TO, newDimension ? "LEADER" : DEFAULT_CORP_ROUTE);
            routeVars.put(VAR_FIN_ROUTE_TO, DEFAULT_FIN_ROUTE);
        }
        return routeVars;
    }

    /**
     * 查询申请维度，供无原业绩分配的 NEW 申请选择安全审批路由。
     * 查询异常时保守按旧数据处理，避免改变既有渠道审批行为；NEW 申请由已落库的申请快照明确识别。
     */
    private boolean isNewDimension(String perfAdjustNo) {
        try {
            AllocAdjustService.ApplyWithItems loaded = allocAdjustService.getById(perfAdjustNo);
            return loaded != null && loaded.getApply() != null
                    && "NEW".equals(loaded.getApply().getAllocDim());
        } catch (RuntimeException ex) {
            log.warn("[PerfApprovalCmdFacade.isNewDimension] 查询申请维度失败 perfAdjustNo={}, err={}",
                    perfAdjustNo, ex.toString());
            return false;
        }
    }

    /** 按员工工号反查其主机构编码（查不到返回 null，由下游按缺失处理）。 */
    private String resolveOwnerOrg(String empId) {
        if (!StringUtils.hasText(empId)) {
            return null;
        }
        UserDTO user = userApi.getUserByEmpId(empId);
        return user == null ? null : user.getMainOrgCode();
    }

    /** 对外渠道 Item → Service Cmd Item（空安全）。 */
    private List<SubmitAllocAdjustCmd.Item> toServiceItems(List<AllocAdjustSubmitCmd.Item> items) {
        if (items == null || items.isEmpty()) {
            return new ArrayList<>();
        }
        List<SubmitAllocAdjustCmd.Item> result = new ArrayList<>(items.size());
        for (AllocAdjustSubmitCmd.Item it : items) {
            result.add(SubmitAllocAdjustCmd.Item.builder()
                    .empId(it.getEmpId())
                    .ratio(it.getRatio())
                    .remark(it.getRemark())
                    .build());
        }
        return result;
    }

    /** 对外渠道原业绩分配 OriginalItem → Service Cmd OriginalItem（空安全），口径与管理端一致。 */
    private List<SubmitAllocAdjustCmd.OriginalItem> toServiceOriginalItems(
            List<AllocAdjustSubmitCmd.OriginalItem> items) {
        if (items == null || items.isEmpty()) {
            return new ArrayList<>();
        }
        List<SubmitAllocAdjustCmd.OriginalItem> result = new ArrayList<>(items.size());
        for (AllocAdjustSubmitCmd.OriginalItem it : items) {
            result.add(SubmitAllocAdjustCmd.OriginalItem.builder()
                    .acctNo(it.getAcctNo())
                    .empId(it.getEmpId())
                    .username(it.getUsername())
                    .empChnName(it.getEmpChnName())
                    .orgCode(it.getOrgCode())
                    .orgName(it.getOrgName())
                    .ratio(it.getRatio())
                    .build());
        }
        return result;
    }
}
