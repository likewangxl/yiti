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
    public void approveAllocAdjust(String perfAdjustNo, String empId, String apprStatus, String opinion) {
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
            // 手机端「同意」不带网关路由选择，后端按业务约定默认走最全审批链路：
            // gw1_corp_route → OWNER（原业绩所属人会签）、gw2_fin_route → LEADER（资财部负责人审批）。
            // 否则排他网关无分支条件命中，complete() 抛异常，流程到不了 <end>，
            // ProcessCompletedEvent(APPROVED) 不会发布，apply.status 无法回写 APPROVED。
            Map<String, Object> routeVars = new HashMap<>();
            routeVars.put(VAR_CORP_ROUTE_TO, DEFAULT_CORP_ROUTE);
            routeVars.put(VAR_FIN_ROUTE_TO, DEFAULT_FIN_ROUTE);
            workflowApi.approveByEmp(taskId, empId, op, routeVars);
            log.info("[PerfApprovalCmdFacade.approveAllocAdjust] 通过 perfAdjustNo={}, empId={}, taskId={}",
                    perfAdjustNo, empId, taskId);
        } else if (APPR_REJECT.equals(apprStatus)) {
            String op = StringUtils.hasText(opinion) ? opinion : DEFAULT_REJECT_OPINION;
            workflowApi.rejectByEmp(taskId, empId, op);
            log.info("[PerfApprovalCmdFacade.approveAllocAdjust] 驳回 perfAdjustNo={}, empId={}, taskId={}",
                    perfAdjustNo, empId, taskId);
        } else {
            throw new IllegalArgumentException("审批状态不合法: " + apprStatus);
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
