package com.bank.branch.platform.workflow.listener;

import com.bank.branch.platform.governance.api.NotifyApi;
import com.bank.branch.platform.governance.api.dto.NotificationCmd;
import com.bank.branch.platform.workflow.service.CandidateResolverService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.flowable.engine.RepositoryService;
import org.flowable.engine.repository.ProcessDefinition;
import org.flowable.task.service.delegate.DelegateTask;
import org.flowable.task.service.delegate.TaskListener;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.stream.Collectors;

/**
 * 任务分配监听器。
 * <p>
 * 在 Flowable 用户任务创建时触发，根据流程定义KEY和节点KEY
 * 解析候选人配置并设置到任务的候选组上。
 * 同时尝试通过 NotifyApi 发送通知给候选人，通知失败不影响流程继续。
 * </p>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class TaskAssignmentListener implements TaskListener {

    private final CandidateResolverService candidateResolverService;
    private final NotifyApi notifyApi;
    private final RepositoryService repositoryService;

    /**
     * 任务创建事件回调。
     * <p>
     * 1. 从 delegateTask 中提取流程定义KEY和节点KEY
     * 2. 通过 CandidateResolverService 解析候选组列表
     * 3. 将候选组设置到 delegateTask 上
     * 4. 尝试发送通知（异常不中断流程）
     * </p>
     *
     * @param delegateTask Flowable 委托任务对象
     */
    @Override
    public void notify(DelegateTask delegateTask) {
        // Flowable 7 默认使用 UUID 作 processDefinitionId（无 ":" 分隔），不能 split(":")[0]。
        // 走 RepositoryService 反查 ProcessDefinition.getKey() 拿真实 BPMN KEY。
        String processDefinitionId = delegateTask.getProcessDefinitionId();
        ProcessDefinition pd = repositoryService.getProcessDefinition(processDefinitionId);
        String processDefinitionKey = pd != null ? pd.getKey() : processDefinitionId;
        String nodeKey = delegateTask.getTaskDefinitionKey();
        String taskId = delegateTask.getId();

        // 解析候选组
        List<String> candidates = candidateResolverService.resolveCandidates(processDefinitionKey, nodeKey);

        // 设置候选组到任务
        for (String group : candidates) {
            delegateTask.addCandidateGroup(group);
        }

        log.info("[TaskAssignmentListener] 任务 {} 已设置候选组 {}", taskId, candidates);

        // 尝试发送通知，失败不影响流程
        if (!candidates.isEmpty()) {
            try {
                List<NotificationCmd> cmds = candidates.stream()
                        .map(group -> NotificationCmd.builder()
                                .targetEmpId(group)
                                .title("您有新的待办任务")
                                .content("任务ID: " + taskId + "，请及时处理")
                                .notifyType("WORKFLOW")
                                .build())
                        .collect(Collectors.toList());
                notifyApi.batchSendNotifications(cmds);
            } catch (Exception e) {
                log.warn("[TaskAssignmentListener] 发送通知失败，任务 {}，原因: {}", taskId, e.getMessage());
            }
        }
    }
}
