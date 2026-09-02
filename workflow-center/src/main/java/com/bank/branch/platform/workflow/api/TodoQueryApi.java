package com.bank.branch.platform.workflow.api;

import com.bank.branch.platform.workflow.api.dto.TaskRespDTO;

import java.util.List;
import java.util.Map;

/**
 * 待办查询 API（供 perf 等业务模块按 businessKey 反查 task 元信息）。
 * <p>设计目的：业务模块需要按业务字段二次过滤待办，但不应直连 Flowable 表；
 * 本 API 提供 businessKey 列表 + 反向 join 能力，把"业务字段过滤"留给业务模块，
 * "Flowable 查询"留在 workflow-center。</p>
 */
public interface TodoQueryApi {

    /**
     * 查询某员工某 bizType 的所有待办 task 的 processInstanceBusinessKey。
     *
     * @param empId   员工 ID
     * @param bizType 业务类型，匹配 processInstanceBusinessKey 前缀（如 "ALLOC_ADJUST"）
     * @return businessKey 列表（去重，可能为空）
     */
    List<String> listMyTodoBusinessKeys(String empId, String bizType);

    /**
     * 按 businessKey 批量反查 TaskRespDTO（含 taskId/nodeKey/title/SLA 等完整 task 元信息）。
     * empId 用于鉴权：只返该员工候选或受理的 task，防越权。
     *
     * @param empId        员工 ID
     * @param businessKeys 待查的 businessKey 列表
     * @return 以 businessKey 为 key 的 Map（不命中的 key 在 Map 中缺失）
     */
    Map<String, TaskRespDTO> findTaskRespByBusinessKeys(String empId, List<String> businessKeys);

    /**
     * 按流程实例 ID 批量查询当前活动任务。
     * <p>本方法不做员工候选人鉴权，仅供业务模块在完成自身数据范围过滤后补充
     * 当前节点、办理人和 SLA 信息；调用方不得用它扩大可见数据范围。</p>
     *
     * @param processInstanceIds 已通过业务数据权限校验的流程实例 ID
     * @return 以流程实例 ID 为 key 的活动任务 Map（不命中的 key 在 Map 中缺失）
     */
    Map<String, TaskRespDTO> findActiveTaskRespByProcessInstanceIds(List<String> processInstanceIds);

    /**
     * 已办：查询某员工某 bizType 下所有历史已办 task 的 processInstanceBusinessKey（去重）。
     *
     * @param empId   员工 ID
     * @param bizType 业务类型（如 "ALLOC_ADJUST"）
     * @return businessKey 列表（去重，可能为空）
     */
    List<String> listMyDoneBusinessKeys(String empId, String bizType);

    /**
     * 已办：按 businessKey 反查 TaskRespDTO（走 HistoryService，仅返该员工 assignee 的）。
     *
     * @param empId        员工 ID（鉴权用）
     * @param businessKeys 待查的 businessKey 列表
     * @return 以 businessKey 为 key 的 Map（不命中的 key 在 Map 中缺失）
     */
    Map<String, TaskRespDTO> findDoneTaskRespByBusinessKeys(String empId, List<String> businessKeys);

    /**
     * 同 {@link #listMyTodoBusinessKeys}，但候选组按传入 empId 查库实时解析，<b>不依赖登录会话</b>。
     * <p>供 SOAP 网关 / callpu 等<b>无会话上下文</b>链路使用（请求线程无登录态，用会话版会抛 AUTH-40105）；
     * PC 管理端请继续用 {@link #listMyTodoBusinessKeys} 以保持会话登录语义。</p>
     *
     * @param empId   员工 ID（由上游渠道认证后透传）
     * @param bizType 业务类型
     * @return businessKey 列表（去重，可能为空）
     */
    List<String> listTodoBusinessKeysByEmp(String empId, String bizType);

    /**
     * 同 {@link #findTaskRespByBusinessKeys}，但候选组按传入 empId 查库实时解析，<b>不依赖登录会话</b>。
     * <p>供 SOAP 网关 / callpu 等无会话上下文链路使用；PC 管理端请继续用 {@link #findTaskRespByBusinessKeys}。</p>
     *
     * @param empId        员工 ID（由上游渠道认证后透传）
     * @param businessKeys 待查的 businessKey 列表
     * @return 以 businessKey 为 key 的 Map（不命中的 key 在 Map 中缺失）
     */
    Map<String, TaskRespDTO> findTaskRespByBusinessKeysByEmp(String empId, List<String> businessKeys);
}
