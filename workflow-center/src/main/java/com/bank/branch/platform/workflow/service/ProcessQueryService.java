package com.bank.branch.platform.workflow.service;

import com.bank.branch.platform.common.web.exception.BizException;
import com.bank.branch.platform.workflow.enums.WfErrorCode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.flowable.engine.HistoryService;
import org.flowable.engine.RepositoryService;
import org.flowable.engine.RuntimeService;
import org.flowable.engine.history.HistoricActivityInstance;
import org.flowable.engine.history.HistoricProcessInstance;
import org.flowable.engine.repository.ProcessDefinition;
import org.flowable.engine.runtime.ProcessInstance;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.io.InputStream;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

/**
 * 流程查询服务
 * <p>
 * 提供流程实例详情、流程图和流程历史的查询功能。
 * 所有 Flowable 查询 API 均封装在此服务中。
 * </p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ProcessQueryService {

    private final RepositoryService repositoryService;
    private final RuntimeService runtimeService;
    private final HistoryService historyService;

    /**
     * 查询流程实例详情（C.1）
     *
     * @param processInstanceId 流程实例ID
     * @return 流程实例信息（运行时 + 历史合并）
     */
    public ProcessInstanceInfo getProcessInstanceInfo(String processInstanceId) {
        log.debug("[ProcessQueryService.getProcessInstanceInfo] processInstanceId={}", processInstanceId);

        // 先查运行时表
        ProcessInstance runtimeInstance = runtimeService.createProcessInstanceQuery()
                .processInstanceId(processInstanceId)
                .singleResult();

        if (runtimeInstance != null) {
            return ProcessInstanceInfo.fromRuntime(runtimeInstance, repositoryService, runtimeService);
        }

        // 再查历史表
        HistoricProcessInstance historicInstance = historyService.createHistoricProcessInstanceQuery()
                .processInstanceId(processInstanceId)
                .singleResult();

        if (historicInstance == null) {
            throw new BizException(
                    WfErrorCode.PROCESS_INSTANCE_NOT_FOUND.getCode(),
                    WfErrorCode.PROCESS_INSTANCE_NOT_FOUND.getMessage());
        }

        return ProcessInstanceInfo.fromHistoric(historicInstance);
    }

    /**
     * 生成流程进度图 PNG 字节数组（C.2）
     * <p>
     * 使用 Flowable RepositoryService.getProcessDiagram 直接返回流程定义对应的 PNG 图像。
     * 该图像包含流程各节点及其连线，不含高亮。
     * 如需高亮当前活动节点，可在获取 activeActivityIds 后自行叠加渲染。
     * </p>
     *
     * @param processInstanceId 流程实例ID
     * @return PNG 图像字节数组
     */
    public byte[] generateProcessDiagram(String processInstanceId) {
        log.debug("[ProcessQueryService.generateProcessDiagram] processInstanceId={}", processInstanceId);

        String processDefinitionId = null;

        // 先查运行时表
        ProcessInstance runtimeInstance = runtimeService.createProcessInstanceQuery()
                .processInstanceId(processInstanceId)
                .singleResult();

        if (runtimeInstance != null) {
            processDefinitionId = runtimeInstance.getProcessDefinitionId();
        } else {
            // 查历史表
            HistoricProcessInstance historicInstance = historyService.createHistoricProcessInstanceQuery()
                    .processInstanceId(processInstanceId)
                    .singleResult();
            if (historicInstance == null) {
                throw new BizException(
                        WfErrorCode.PROCESS_INSTANCE_NOT_FOUND.getCode(),
                        WfErrorCode.PROCESS_INSTANCE_NOT_FOUND.getMessage());
            }
            processDefinitionId = historicInstance.getProcessDefinitionId();
        }

        try (InputStream is = repositoryService.getProcessDiagram(processDefinitionId)) {
            return is.readAllBytes();
        } catch (IOException e) {
            log.error("生成流程图失败: processDefinitionId={}", processDefinitionId, e);
            throw new BizException(
                    WfErrorCode.ENGINE_ERROR.getCode(),
                    "生成流程图失败: " + e.getMessage());
        }
    }

    /**
     * 查询流程历史节点列表（C.3）
     *
     * @param processInstanceId 流程实例ID
     * @return 历史活动节点列表（不含服务任务等自动节点）
     */
    public List<ProcessHistoryDTO> getProcessHistory(String processInstanceId) {
        log.debug("[ProcessQueryService.getProcessHistory] processInstanceId={}", processInstanceId);

        // 校验流程实例是否存在
        HistoricProcessInstance instance = historyService.createHistoricProcessInstanceQuery()
                .processInstanceId(processInstanceId)
                .singleResult();
        if (instance == null) {
            throw new BizException(
                    WfErrorCode.PROCESS_INSTANCE_NOT_FOUND.getCode(),
                    WfErrorCode.PROCESS_INSTANCE_NOT_FOUND.getMessage());
        }

        List<HistoricActivityInstance> activities = historyService.createHistoricActivityInstanceQuery()
                .processInstanceId(processInstanceId)
                .orderByHistoricActivityInstanceStartTime()
                .asc()
                .list();

        // 仅返回用户任务类型的历史节点
        return activities.stream()
                .filter(a -> a.getActivityType() != null && a.getActivityType().startsWith("userTask"))
                .map(this::toHistoryDTO)
                .collect(Collectors.toList());
    }

    private ProcessHistoryDTO toHistoryDTO(HistoricActivityInstance activity) {
        ProcessHistoryDTO dto = new ProcessHistoryDTO();
        dto.setActivityId(activity.getActivityId());
        dto.setActivityName(activity.getActivityName());
        dto.setActivityType(activity.getActivityType());
        dto.setAssignee(activity.getAssignee());
        if (activity.getStartTime() != null) {
            dto.setStartTime(activity.getStartTime().toInstant().atZone(ZoneId.systemDefault()).toLocalDateTime());
        }
        if (activity.getEndTime() != null) {
            dto.setEndTime(activity.getEndTime().toInstant().atZone(ZoneId.systemDefault()).toLocalDateTime());
        }
        if (activity.getDurationInMillis() != null) {
            dto.setDurationMs(activity.getDurationInMillis());
        }
        return dto;
    }

    /**
     * 流程实例信息 DTO
     */
    public record ProcessInstanceInfo(
            String processInstanceId,
            String processDefinitionKey,
            String processDefinitionName,
            Integer processDefinitionVersion,
            String businessKey,
            String startUserId,
            String currentActivityId,
            Boolean isEnded,
            java.time.LocalDateTime startTime,
            java.time.LocalDateTime endTime,
            Long durationMs
    ) {
        private static String extractKey(String processDefinitionId) {
            if (processDefinitionId == null) return null;
            return processDefinitionId.split(":")[0];
        }

        public static ProcessInstanceInfo fromRuntime(ProcessInstance pi, RepositoryService repoService, RuntimeService runtimeService) {
            String pdId = pi.getProcessDefinitionId();
            ProcessDefinition pd = null;
            try {
                pd = repoService.getProcessDefinition(pdId);
            } catch (Exception e) {
                log.warn("获取流程定义失败: {}", pdId, e);
            }

            List<String> activeActivityIds = new ArrayList<>(runtimeService.getActiveActivityIds(pi.getId()));

            return new ProcessInstanceInfo(
                    pi.getId(),
                    extractKey(pdId),
                    pd != null ? pd.getName() : null,
                    pd != null ? pd.getVersion() : null,
                    pi.getBusinessKey(),
                    pi.getStartUserId(),
                    activeActivityIds.isEmpty() ? null : activeActivityIds.get(0),
                    false,
                    pi.getStartTime() != null
                            ? pi.getStartTime().toInstant().atZone(ZoneId.systemDefault()).toLocalDateTime()
                            : null,
                    null,
                    null
            );
        }

        public static ProcessInstanceInfo fromHistoric(HistoricProcessInstance hpi) {
            return new ProcessInstanceInfo(
                    hpi.getId(),
                    hpi.getProcessDefinitionKey(),
                    null, // HistoricProcessInstance 不直接提供名称
                    null, // HistoricProcessInstance 不直接提供版本
                    hpi.getBusinessKey(),
                    hpi.getStartUserId(),
                    null, // 历史实例无法确定当前节点
                    hpi.getEndTime() != null,
                    hpi.getStartTime() != null
                            ? hpi.getStartTime().toInstant().atZone(ZoneId.systemDefault()).toLocalDateTime()
                            : null,
                    hpi.getEndTime() != null
                            ? hpi.getEndTime().toInstant().atZone(ZoneId.systemDefault()).toLocalDateTime()
                            : null,
                    hpi.getDurationInMillis()
            );
        }
    }

    /**
     * 流程历史节点 DTO
     */
    @lombok.Data
    public static class ProcessHistoryDTO {
        /** 活动节点ID（BPMN node ID） */
        private String activityId;
        /** 活动节点名称 */
        private String activityName;
        /** 活动类型 */
        private String activityType;
        /** 办理人 */
        private String assignee;
        /** 开始时间 */
        private java.time.LocalDateTime startTime;
        /** 结束时间 */
        private java.time.LocalDateTime endTime;
        /** 耗时（毫秒） */
        private Long durationMs;
    }
}
