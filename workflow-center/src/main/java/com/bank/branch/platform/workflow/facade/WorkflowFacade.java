package com.bank.branch.platform.workflow.facade;

import com.bank.branch.platform.workflow.api.WorkflowApi;
import com.bank.branch.platform.workflow.api.dto.BizProcessMapDTO;
import com.bank.branch.platform.workflow.api.dto.StartProcessCmd;
import com.bank.branch.platform.workflow.api.dto.WorkflowLaunchResp;
import com.bank.branch.platform.workflow.api.dto.CancelProcessReqDTO;
import com.bank.branch.platform.workflow.service.ProcessCommandService;
import com.bank.branch.platform.workflow.service.ProcessStartService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/**
 * 工作流 Facade 实现
 * <p>
 * 实现 WorkflowApi 接口，委托给 ProcessStartService 处理具体逻辑。
 * 作为跨模块调用的统一入口。
 * </p>
 */
@Service
@RequiredArgsConstructor
public class WorkflowFacade implements WorkflowApi {

    private final ProcessStartService processStartService;
    private final ProcessCommandService processCommandService;

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
}
