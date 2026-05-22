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
}
