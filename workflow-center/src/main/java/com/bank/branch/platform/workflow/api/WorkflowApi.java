package com.bank.branch.platform.workflow.api;

import com.bank.branch.platform.workflow.api.dto.BizProcessMapDTO;
import com.bank.branch.platform.workflow.api.dto.StartProcessCmd;
import com.bank.branch.platform.workflow.api.dto.WorkflowLaunchResp;

/**
 * 工作流核心 API -- 流程启动与控制
 * <p>
 * 所有业务模块通过此接口发起和控制流程实例。
 * 调用方必须在自身事务内调用（嵌入式 Flowable 共享事务）。
 * </p>
 * <p>
 * 调用顺序：业务模块先完成 RBAC + DATA_SCOPE + 状态守卫 + 表单校验 + 业务主表保存，
 * 再调用 startProcess()。
 * </p>
 */
public interface WorkflowApi {

    /**
     * 启动流程实例
     *
     * @param cmd 流程启动命令，不可为 null
     * @return 流程启动响应，包含 processInstanceId、businessKey、firstTaskId
     * @throws com.bank.branch.platform.common.web.exception.BizException WF-40401 流程定义不存在
     * @throws com.bank.branch.platform.common.web.exception.BizException WF-40901 业务键已存在运行中流程
     */
    WorkflowLaunchResp startProcess(StartProcessCmd cmd);

    /**
     * 根据业务键查询流程映射记录
     *
     * @param businessKey 业务键
     * @return 业务流程映射 DTO
     * @throws com.bank.branch.platform.common.web.exception.BizException WF-40402 流程实例不存在
     */
    BizProcessMapDTO getProcessByBusinessKey(String businessKey);

    /**
     * 根据业务类型和业务ID查询流程映射记录
     *
     * @param bizType 业务类型
     * @param bizId   业务ID
     * @return 业务流程映射 DTO
     * @throws com.bank.branch.platform.common.web.exception.BizException WF-40402 流程实例不存在
     */
    BizProcessMapDTO getProcessByBizTypeAndBizId(String bizType, String bizId);
}
