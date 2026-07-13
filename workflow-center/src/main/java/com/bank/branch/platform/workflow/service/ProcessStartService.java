package com.bank.branch.platform.workflow.service;

import com.bank.branch.platform.auth.api.OrgApi;
import com.bank.branch.platform.auth.api.dto.OrgDTO;
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
    /** 机构查询：解析发起人机构级别（startOrgLevel），供网关按 2级/3级机构分流 */
    private final OrgApi orgApi;
    /** 参与机构快照写入唯一入口（D5：流程发起时记录发起人机构） */
    private final WfProcessOrgService wfProcessOrgService;

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

        // 3. 启动流程实例（注入 startOrgId 到流程变量，供候选人机构过滤）
        java.util.Map<String, Object> vars = cmd.getVariables() != null
                ? new java.util.HashMap<>(cmd.getVariables()) : new java.util.HashMap<>();
        if (cmd.getStartOrgId() != null) {
            vars.put("startOrgId", cmd.getStartOrgId());
        }
        if (cmd.getStartUser() != null) {
            vars.put("startUser", cmd.getStartUser());
        }
        // 注入发起人机构级别（1=总部/2=分行/3=支行），供设计器网关按 2级/3级机构走不同审批路径
        Integer startOrgLevel = resolveStartOrgLevel(cmd);
        if (startOrgLevel != null) {
            vars.put("startOrgLevel", startOrgLevel);
        }
        ProcessInstance pi = runtimeService.startProcessInstanceByKey(
                cmd.getProcessDefinitionKey(),
                cmd.getBusinessKey(),
                vars);
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

        // 4.1 记录参与机构快照（D5：流程发起时记发起人机构，source=START）
        wfProcessOrgService.record(pi.getId(), cmd.getStartUser(), "START");

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
     * 解析发起人所在机构的级别（1=总部 / 2=分行 / 3=支行）。
     * <p>
     * 优先用 {@code startOrgId} 机构编码解析；缺失时回退用发起人 {@code startUser} 的主机构。
     * 任何异常或解析不到都返回 null（不写入 startOrgLevel），保证不阻塞流程启动。
     * </p>
     *
     * @param cmd 流程启动命令
     * @return 机构级别，无法解析时返回 null
     */
    private Integer resolveStartOrgLevel(StartProcessCmd cmd) {
        try {
            OrgDTO org = null;
            if (cmd.getStartOrgId() != null) {
                org = orgApi.getOrg(cmd.getStartOrgId());
            } else if (cmd.getStartUser() != null) {
                org = orgApi.getUserMainOrg(cmd.getStartUser());
            }
            return org != null ? org.getOrgLevel() : null;
        } catch (Exception e) {
            // 机构解析失败不影响流程启动，仅跳过 startOrgLevel 注入
            log.warn("解析发起人机构级别失败，跳过 startOrgLevel 注入: startOrgId={}, startUser={}, err={}",
                    cmd.getStartOrgId(), cmd.getStartUser(), e.getMessage());
            return null;
        }
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
