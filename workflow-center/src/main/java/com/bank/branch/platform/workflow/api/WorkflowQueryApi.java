package com.bank.branch.platform.workflow.api;

import com.bank.branch.platform.common.web.PageResult;
import com.bank.branch.platform.workflow.api.dto.ApprovalLogDTO;
import com.bank.branch.platform.workflow.api.dto.BizProcessMapDTO;
import com.bank.branch.platform.workflow.api.dto.ProcessDiagramDTO;
import com.bank.branch.platform.workflow.api.dto.TaskDetailRespDTO;
import com.bank.branch.platform.workflow.api.dto.TaskCandidateUserDTO;
import com.bank.branch.platform.workflow.api.dto.TaskRespDTO;

import java.util.List;
import java.util.Set;

/**
 * 工作流只读查询 API。
 * <p>
 * 为其他业务模块提供任务查询、流程历史和流程映射等只读能力。
 * 所有实现必须复用 workflow-center 现有查询 service 和 DTO，避免平行模型。
 * </p>
 */
public interface WorkflowQueryApi {

    /**
     * 查询待办列表。
     *
     * @param empId 当前员工工号
     * @param bizType 业务类型过滤，可为空
     * @param keyword 关键字过滤，可为空
     * @param pageNo 页码
     * @param pageSize 每页大小
     * @return 待办分页结果
     */
    PageResult<TaskRespDTO> queryTodoList(String empId, String bizType, String keyword, int pageNo, int pageSize);

    /**
     * 查询已办列表。
     *
     * @param empId 当前员工工号
     * @param bizType 业务类型过滤，可为空
     * @param keyword 关键字过滤，可为空
     * @param pageNo 页码
     * @param pageSize 每页大小
     * @return 已办分页结果
     */
    PageResult<TaskRespDTO> queryDoneList(String empId, String bizType, String keyword, int pageNo, int pageSize);

    /**
     * 统计待办总数。
     *
     * @param empId 当前员工工号
     * @return 待办总数
     */
    int countPendingTasks(String empId);

    /**
     * 查询最近待办。
     *
     * @param empId 当前员工工号
     * @param limit 最大返回条数
     * @return 最近待办列表
     */
    List<TaskRespDTO> listRecentPendingTasks(String empId, int limit);

    /**
     * 获取任务详情。
     *
     * @param taskId 任务 ID
     * @param empId 当前员工工号
     * @return 任务详情
     */
    TaskDetailRespDTO getTaskDetail(String taskId, String empId);

    /**
     * 获取流程历史。
     *
     * @param processInstanceId 流程实例 ID
     * @return 流程历史节点列表
     */
    List<ApprovalLogDTO> getProcessHistory(String processInstanceId);

    /**
     * 获取流程进度节点。
     *
     * @param processInstanceId 流程实例 ID
     * @return 流程进度节点图
     */
    ProcessDiagramDTO getProcessNodes(String processInstanceId);

    /**
     * 查询流程当前活动任务的可审批员工。
     *
     * <p>以运行时任务的 assignee / candidate 身份链接为准；流程已结束或当前节点
     * 已审核、没有活动任务时返回空列表。</p>
     *
     * @param processInstanceId 流程实例 ID
     * @return 去重后的员工姓名和工号列表
     */
    List<TaskCandidateUserDTO> getActiveTaskCandidates(String processInstanceId);

    /**
     * 根据业务键获取流程映射。
     *
     * @param businessKey 业务键
     * @return 流程映射
     */
    BizProcessMapDTO getProcessByBusinessKey(String businessKey);

    /**
     * 根据业务类型和业务 ID 获取流程映射。
     *
     * @param bizType 业务类型
     * @param bizId 业务 ID
     * @return 流程映射
     */
    BizProcessMapDTO getProcessByBizTypeAndBizId(String bizType, String bizId);

    /**
     * 查询指定员工参与过的流程实例 businessKey 集合（去重，V1.4 S1.1 新增）。
     *
     * <p>"参与"定义（模式 B 简化版）：
     * <ul>
     *   <li>主路径：{@code HistoricProcessInstanceQuery.involvedUser(empId)} 覆盖
     *       assignee / owner / 显式 {@code addUserIdentityLink} 的用户
     *       （Flowable 在任务 claim / complete 时会自动登记 involvedUser）</li>
     *   <li>辅助路径：若 {@code empId} 为当前登录用户
     *       （{@link com.bank.branch.platform.auth.api.CurrentUserApi#getCurrentEmpId}），
     *       合并当前候选组下未领取的任务
     *       （{@code TaskService.createTaskQuery().taskCandidateGroupIn(groups).taskUnassigned()}）。
     *       若 empId ≠ 当前用户则仅走主路径（避免 workflow-center 反查其他用户的
     *       候选组，此场景通常出现在跨模块 scope 过滤：performance ctx.empId() 恒等于
     *       当前登录用户）</li>
     * </ul>
     *
     * <p>processDefinitionKey 前缀过滤在 Java 侧做 {@code startsWith} 过滤
     * （Flowable 7 {@code HistoricProcessInstanceQuery} 只支持精确 key
     * 或 keyIn，不支持 keyLike）。
     *
     * <p>Fail-safe 设计：
     * <ul>
     *   <li>empId 为 null/空 → 返回空集，不调用 Flowable（避免 NPE 和无效查询）</li>
     *   <li>结果 businessKey 为 null/空 的历史实例会被过滤</li>
     *   <li>limit null 或 ≤ 0 → 兜底 10000；&gt;10000 → 截断 10000</li>
     * </ul>
     *
     * @param empId 员工 ID（null/空则返回空集）
     * @param processDefinitionKeyPrefix 流程定义 key 前缀（null/空 = 不过滤，
     *                                    如 "perf_alloc_adjust_" 限定本模块）
     * @param timeWindowDays 时间窗口（天）；null/≤0 = 无限制；推荐 ≤ 365
     * @param limit 最大返回数量（兜底防 OOM）；null/≤0 = 10000；上限 10000
     * @return businessKey 集合（去重，保持查询顺序；永不返回 null）
     * @since V1.4 S1.1
     */
    Set<String> queryParticipatedBusinessKeys(String empId,
                                              String processDefinitionKeyPrefix,
                                              Integer timeWindowDays,
                                              Integer limit);
}
