package com.bank.branch.platform.workflow.service.flow;

import com.bank.branch.platform.common.web.exception.BizException;
import com.bank.branch.platform.workflow.entity.WfFlowDef;
import com.bank.branch.platform.workflow.enums.WfErrorCode;
import com.bank.branch.platform.workflow.mapper.WfFlowDefMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/**
 * 设计器流程「已发布定义」查找服务。
 * <p>
 * 按 flowKey 解析其已发布的 Flowable 流程定义 KEY（{@code deployed_proc_def_key}），
 * 供业务模块（如 perf 业绩调整审批）起流程时按客户类型选对公/零售设计器流程。
 * 单独成 Service（仅依赖 {@link WfFlowDefMapper}）便于隔离单测与复用。
 * </p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class DesignerFlowLookupService {

    private final WfFlowDefMapper flowDefMapper;

    /**
     * 解析设计器流程已发布的流程定义 KEY。
     *
     * @param flowKey 设计器流程唯一键（如 alloc_corp_designer）
     * @return deployed_proc_def_key（如 DSN_alloc_corp_designer）
     * @throws BizException WF-40401 流程不存在或未发布（status≠PUBLISHED / deployed_proc_def_key 为空）
     */
    public String resolveDeployedProcDefKey(String flowKey) {
        WfFlowDef def = flowDefMapper.selectByFlowKey(flowKey);
        if (def == null || !"PUBLISHED".equals(def.getStatus())
                || def.getDeployedProcDefKey() == null || def.getDeployedProcDefKey().isBlank()) {
            log.warn("[DesignerFlowLookupService.resolveDeployedProcDefKey] 流程未发布或不存在: flowKey={}, "
                            + "status={}", flowKey, def == null ? "<null>" : def.getStatus());
            throw new BizException(WfErrorCode.PROCESS_DEF_NOT_FOUND.getCode(),
                    "设计器审批流程未发布或不存在，flowKey=" + flowKey);
        }
        return def.getDeployedProcDefKey();
    }
}
