package com.bank.branch.platform.workflow.api;

import com.bank.branch.platform.workflow.api.dto.BizProcessMapDTO;
import com.bank.branch.platform.workflow.api.dto.StartProcessCmd;
import com.bank.branch.platform.workflow.api.dto.WorkflowLaunchResp;

import java.util.Optional;

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
     * 取消流程实例。
     *
     * @param processInstanceId 流程实例 ID
     * @param reason 取消原因
     */
    void cancelProcess(String processInstanceId, String reason);

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

    /**
     * 查询已完成流程的审批结论（用于补偿场景）。
     * <p>
     * 调用方在已知流程 status=COMPLETED 的前提下，通过该方法读取 Flowable
     * {@code HistoryService} 历史变量 {@code approved}（Boolean 类型），并按业务语义
     * 转换为 {@code "APPROVED"}（{@code true}）/ {@code "REJECTED"}（{@code false}）。
     * </p>
     * <p>
     * <strong>语义对齐</strong>：返回值与 {@code ProcessCompletedListener} 发布
     * {@code ProcessCompletedEvent.outcome()} 字段保持一致，便于补偿任务复用 listener
     * 现有分支处理逻辑。
     * </p>
     * <p>
     * <strong>边界</strong>：
     * <ul>
     *   <li>流程不存在 / 仍 RUNNING / 历史变量未设置 → 返回 {@link Optional#empty()}（不抛异常）；</li>
     *   <li>调用方应自行先经 {@link #getProcessByBizTypeAndBizId} 等接口确认 processStatus=COMPLETED
     *       后再调用，避免对未完成流程产生误判。</li>
     * </ul>
     * </p>
     *
     * @param processInstanceId Flowable 流程实例 ID
     * @return {@code Optional.of("APPROVED" / "REJECTED")} 或 {@link Optional#empty()}
     */
    Optional<String> getProcessOutcome(String processInstanceId);

    /**
     * 审批通过指定任务（<b>无会话版</b>，供外部渠道按显式 empId 调用）。
     * <p>不依赖登录态 ThreadLocal、不校验 assignee（不要求签收）；调用方（如 perf 渠道审批）须先
     * 按候选组/角色可见性确认该 empId 能审批此任务（查出 taskId）后再调用。</p>
     *
     * @param taskId  任务ID
     * @param empId   审批人工号（外部渠道认证后透传）
     * @param opinion 审批意见（可空）
     * @throws com.bank.branch.platform.common.web.exception.BizException WF-40403 任务不存在
     */
    void approveByEmp(String taskId, String empId, String opinion);

    /**
     * 审批通过指定任务（<b>无会话版，带节点表单/路由变量</b>，供外部渠道按显式 empId 调用）。
     * <p>语义同 {@link #approveByEmp(String, String, String)}，额外把 {@code formData} 作为流程变量
     * 写入 {@code complete}，供 BPMN 排他网关路由（如 perf 分配调整流程的 {@code corpRouteTo} /
     * {@code finRouteTo}）。{@code formData} 为空时等价于三参版本。</p>
     *
     * @param taskId   任务ID
     * @param empId    审批人工号（外部渠道认证后透传）
     * @param opinion  审批意见（可空）
     * @param formData 节点表单/路由变量（可空）
     * @throws com.bank.branch.platform.common.web.exception.BizException WF-40403 任务不存在
     */
    void approveByEmp(String taskId, String empId, String opinion, java.util.Map<String, Object> formData);

    /**
     * 驳回指定任务（<b>无会话版</b>，供外部渠道按显式 empId 调用）。可见性约定同 {@link #approveByEmp}。
     *
     * @param taskId  任务ID
     * @param empId   审批人工号（外部渠道认证后透传）
     * @param opinion 驳回意见（可空）
     * @throws com.bank.branch.platform.common.web.exception.BizException WF-40403 任务不存在
     */
    void rejectByEmp(String taskId, String empId, String opinion);
}
