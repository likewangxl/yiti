package com.bank.branch.platform.workflow.facade;

import com.bank.branch.platform.workflow.api.WorkflowApi;
import com.bank.branch.platform.workflow.api.dto.BizProcessMapDTO;
import com.bank.branch.platform.workflow.api.dto.StartProcessCmd;
import com.bank.branch.platform.workflow.api.dto.WorkflowLaunchResp;
import com.bank.branch.platform.workflow.api.dto.CancelProcessReqDTO;
import com.bank.branch.platform.workflow.api.dto.ApproveReqDTO;
import com.bank.branch.platform.workflow.api.dto.RejectReqDTO;
import com.bank.branch.platform.workflow.service.ProcessCommandService;
import com.bank.branch.platform.workflow.service.ProcessStartService;
import com.bank.branch.platform.workflow.service.TaskOperationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.flowable.engine.HistoryService;
import org.flowable.variable.api.history.HistoricVariableInstance;
import org.springframework.stereotype.Service;

import java.util.Optional;

/**
 * 工作流 Facade 实现
 * <p>
 * 实现 WorkflowApi 接口，委托给 ProcessStartService 处理具体逻辑。
 * 作为跨模块调用的统一入口。
 * </p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class WorkflowFacade implements WorkflowApi {

    private final ProcessStartService processStartService;
    private final ProcessCommandService processCommandService;
    private final HistoryService historyService;
    private final TaskOperationService taskOperationService;
    private final com.bank.branch.platform.workflow.service.flow.DesignerFlowLookupService designerFlowLookupService;

    /**
     * {@inheritDoc}
     */
    @Override
    public WorkflowLaunchResp startProcess(StartProcessCmd cmd) {
        return processStartService.startProcess(cmd);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void cancelProcess(String processInstanceId, String reason) {
        CancelProcessReqDTO req = new CancelProcessReqDTO();
        req.setReason(reason);
        processCommandService.cancelProcess(processInstanceId, req);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public BizProcessMapDTO getProcessByBusinessKey(String businessKey) {
        return processStartService.getProcessByBusinessKey(businessKey);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public BizProcessMapDTO getProcessByBizTypeAndBizId(String bizType, String bizId) {
        return processStartService.getProcessByBizTypeAndBizId(bizType, bizId);
    }

    /**
     * {@inheritDoc}
     *
     * <p><strong>实现策略</strong>：</p>
     * <ul>
     *   <li>调 Flowable {@code HistoryService.createHistoricVariableInstanceQuery}
     *       按 {@code processInstanceId} + {@code variableName=approved} 查历史变量；</li>
     *   <li>变量值类型为 {@code Boolean}：
     *     <ul>
     *       <li>{@code Boolean.TRUE} → "APPROVED"</li>
     *       <li>{@code Boolean.FALSE} → "REJECTED"</li>
     *       <li>null（变量未设置 / 非 Boolean 类型）→ {@link Optional#empty()}</li>
     *     </ul>
     *   </li>
     *   <li>语义与 {@code ProcessCompletedListener.notify()} 内 {@code event.outcome()}
     *       计算逻辑保持一致，确保补偿路径与主路径行为对齐。</li>
     * </ul>
     */
    @Override
    public Optional<String> getProcessOutcome(String processInstanceId) {
        if (processInstanceId == null || processInstanceId.isEmpty()) {
            return Optional.empty();
        }
        try {
            HistoricVariableInstance variable = historyService.createHistoricVariableInstanceQuery()
                    .processInstanceId(processInstanceId)
                    .variableName("approved")
                    .singleResult();
            if (variable == null || variable.getValue() == null) {
                return Optional.empty();
            }
            Object value = variable.getValue();
            if (!(value instanceof Boolean)) {
                log.warn("[WorkflowFacade.getProcessOutcome] 流程 {} 的 approved 变量类型异常: {}",
                        processInstanceId, value.getClass().getName());
                return Optional.empty();
            }
            return Optional.of(Boolean.TRUE.equals(value) ? "APPROVED" : "REJECTED");
        } catch (Exception e) {
            // Flowable 查询异常（如 H2 测试环境 ACT_HI_VARINST 表不存在）—— 返 empty 让调用方按"未知 outcome"处理
            log.warn("[WorkflowFacade.getProcessOutcome] 查询流程 {} 历史变量失败: {}",
                    processInstanceId, e.getMessage());
            return Optional.empty();
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void approveByEmp(String taskId, String empId, String opinion) {
        approveByEmp(taskId, empId, opinion, null);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void approveByEmp(String taskId, String empId, String opinion, java.util.Map<String, Object> formData) {
        taskOperationService.approveTaskByEmp(taskId, empId, new ApproveReqDTO(opinion, formData));
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void rejectByEmp(String taskId, String empId, String opinion) {
        taskOperationService.rejectTaskByEmp(taskId, empId, new RejectReqDTO(opinion));
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public String resolveDesignerProcDefKey(String flowKey) {
        return designerFlowLookupService.resolveDeployedProcDefKey(flowKey);
    }
}
