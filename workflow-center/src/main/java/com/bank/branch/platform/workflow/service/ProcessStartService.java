package com.bank.branch.platform.workflow.service;

import com.bank.branch.platform.common.web.exception.BizException;
import com.bank.branch.platform.workflow.api.dto.BizProcessMapDTO;
import com.bank.branch.platform.workflow.api.dto.StartProcessCmd;
import com.bank.branch.platform.workflow.api.dto.WorkflowLaunchResp;
import com.bank.branch.platform.workflow.entity.BizProcessMap;
import com.bank.branch.platform.workflow.enums.ProcessStatus;
import com.bank.branch.platform.workflow.enums.WfErrorCode;
import com.bank.branch.platform.workflow.mapper.BizProcessMapMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.flowable.engine.RepositoryService;
import org.flowable.engine.RuntimeService;
import org.flowable.engine.TaskService;
import org.flowable.engine.repository.ProcessDefinition;
import org.flowable.engine.runtime.ProcessInstance;
import org.flowable.task.api.Task;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * 流程启动服务
 * <p>
 * 负责流程实例的启动、业务流程映射记录的写入与查询。
 * 所有 Flowable 引擎交互均封装在此服务中。
 * </p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ProcessStartService {

    private final RepositoryService repositoryService;
    private final RuntimeService runtimeService;
    private final TaskService taskService;
    private final BizProcessMapMapper bizProcessMapMapper;
    private final ApplicationEventPublisher eventPublisher;

    /**
     * 启动流程实例
     * <p>
     * 执行逻辑：
     * 1. 校验流程定义存在
     * 2. 校验业务键无运行中流程
     * 3. 调用 Flowable RuntimeService 启动流程
     * 4. 写入 biz_process_map 映射记录
     * 5. 查询首个任务
     * 6. 发布事件
     * </p>
     *
     * @param cmd 流程启动命令
     * @return 流程启动响应
     */
    public WorkflowLaunchResp startProcess(StartProcessCmd cmd) {
        // 1. 校验流程定义是否存在
        ProcessDefinition processDef = repositoryService.createProcessDefinitionQuery()
                .processDefinitionKey(cmd.getProcessDefinitionKey())
                .latestVersion()
                .singleResult();
        if (processDef == null) {
            log.warn("流程定义不存在: processDefinitionKey={}", cmd.getProcessDefinitionKey());
            throw new BizException(
                    WfErrorCode.PROCESS_DEF_NOT_FOUND.getCode(),
                    WfErrorCode.PROCESS_DEF_NOT_FOUND.getMessage());
        }

        // 2. 校验业务键是否已有运行中的流程
        if (bizProcessMapMapper.existsRunningByBusinessKey(cmd.getBusinessKey())) {
            log.warn("业务键已有运行中流程: businessKey={}", cmd.getBusinessKey());
            throw new BizException(
                    WfErrorCode.BUSINESS_KEY_ALREADY_RUNNING.getCode(),
                    WfErrorCode.BUSINESS_KEY_ALREADY_RUNNING.getMessage());
        }

        // 3. 启动流程实例
        ProcessInstance pi = runtimeService.startProcessInstanceByKey(
                cmd.getProcessDefinitionKey(),
                cmd.getBusinessKey(),
                cmd.getVariables());
        log.info("流程启动成功: processInstanceId={}, businessKey={}", pi.getId(), cmd.getBusinessKey());

        // 4. 写入 biz_process_map 映射记录
        BizProcessMap map = new BizProcessMap();
        map.setId(UUID.randomUUID().toString().replace("-", ""));
        map.setBizType(cmd.getBizType());
        map.setBizId(cmd.getBizId());
        map.setBusinessKey(cmd.getBusinessKey());
        map.setProcessDefinitionKey(cmd.getProcessDefinitionKey());
        map.setProcessInstanceId(pi.getId());
        map.setProcessStatus(ProcessStatus.RUNNING.getCode());
        map.setStartUser(cmd.getStartUser());
        map.setTitle(cmd.getTitle());
        map.setStartTime(LocalDateTime.now());
        bizProcessMapMapper.insert(map);

        // 5. 查询首个任务（可能为 null，如第一个节点是自动任务）
        Task firstTask = taskService.createTaskQuery()
                .processInstanceId(pi.getId())
                .singleResult();
        String firstTaskId = firstTask != null ? firstTask.getId() : null;

        // 6. 发布流程启动事件
        eventPublisher.publishEvent(new ProcessStartedEvent(pi.getId(), cmd.getBusinessKey(), cmd.getBizType()));

        return new WorkflowLaunchResp(pi.getId(), cmd.getBusinessKey(), firstTaskId);
    }

    /**
     * 根据业务键查询流程映射记录
     *
     * @param businessKey 业务键
     * @return 业务流程映射 DTO
     */
    public BizProcessMapDTO getProcessByBusinessKey(String businessKey) {
        BizProcessMap entity = bizProcessMapMapper.selectByBusinessKey(businessKey);
        if (entity == null) {
            throw new BizException(
                    WfErrorCode.PROCESS_INSTANCE_NOT_FOUND.getCode(),
                    WfErrorCode.PROCESS_INSTANCE_NOT_FOUND.getMessage());
        }
        return toDTO(entity);
    }

    /**
     * 根据业务类型和业务ID查询流程映射记录
     *
     * @param bizType 业务类型
     * @param bizId   业务ID
     * @return 业务流程映射 DTO
     */
    public BizProcessMapDTO getProcessByBizTypeAndBizId(String bizType, String bizId) {
        BizProcessMap entity = bizProcessMapMapper.selectByBizTypeAndBizId(bizType, bizId);
        if (entity == null) {
            throw new BizException(
                    WfErrorCode.PROCESS_INSTANCE_NOT_FOUND.getCode(),
                    WfErrorCode.PROCESS_INSTANCE_NOT_FOUND.getMessage());
        }
        return toDTO(entity);
    }

    /**
     * 将实体转换为 DTO
     *
     * @param entity 业务流程映射实体
     * @return 业务流程映射 DTO
     */
    private BizProcessMapDTO toDTO(BizProcessMap entity) {
        BizProcessMapDTO dto = new BizProcessMapDTO();
        dto.setId(entity.getId());
        dto.setBizType(entity.getBizType());
        dto.setBizId(entity.getBizId());
        dto.setBusinessKey(entity.getBusinessKey());
        dto.setProcessDefinitionKey(entity.getProcessDefinitionKey());
        dto.setProcessInstanceId(entity.getProcessInstanceId());
        dto.setProcessStatus(entity.getProcessStatus());
        dto.setStartUser(entity.getStartUser());
        dto.setCurrentAssignee(entity.getCurrentAssignee());
        dto.setStartTime(entity.getStartTime());
        dto.setEndTime(entity.getEndTime());
        return dto;
    }

    /**
     * 流程启动事件，用于通知其他组件流程已启动
     *
     * @param processInstanceId 流程实例ID
     * @param businessKey       业务键
     * @param bizType           业务类型
     */
    public record ProcessStartedEvent(String processInstanceId, String businessKey, String bizType) {
    }
}
