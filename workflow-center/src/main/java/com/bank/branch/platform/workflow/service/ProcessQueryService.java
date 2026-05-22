package com.bank.branch.platform.workflow.service;

import com.bank.branch.platform.auth.api.OrgApi;
import com.bank.branch.platform.auth.api.UserApi;
import com.bank.branch.platform.auth.api.dto.OrgDTO;
import com.bank.branch.platform.common.web.exception.BizException;
import com.bank.branch.platform.workflow.api.dto.ApprovalLogDTO;
import com.bank.branch.platform.workflow.entity.BizProcessMap;
import com.bank.branch.platform.workflow.api.dto.ProcessDiagramDTO;
import com.bank.branch.platform.workflow.api.dto.ProcessDiagramNodeDTO;
import com.bank.branch.platform.workflow.enums.WfErrorCode;
import com.bank.branch.platform.workflow.mapper.BizProcessMapMapper;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.flowable.engine.HistoryService;
import org.flowable.engine.RepositoryService;
import org.flowable.engine.RuntimeService;
import org.flowable.engine.history.HistoricActivityInstance;
import org.flowable.engine.history.HistoricProcessInstance;
import org.flowable.engine.repository.ProcessDefinition;
import org.flowable.engine.runtime.ProcessInstance;
import org.flowable.task.api.TaskInfo;
import org.flowable.task.api.history.HistoricTaskInstance;
import org.flowable.engine.TaskService;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.io.InputStream;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
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
    private final TaskService taskService;
    private final UserApi userApi;
    private final OrgApi orgApi;
    private final BizProcessMapMapper bizProcessMapMapper;
    private final ObjectMapper objectMapper;
    private final JdbcTemplate jdbcTemplate;

    // ========== 流程实例详情 ==========

    /**
     * 查询流程实例详情（C.1）
     * <p>
     * 先查运行时表，再查历史表，最后关联业务映射补充业务字段。
     * 业务字段包括：bizType、bizId、title、发起人姓名、发起人机构、
     * 当前节点处理人信息、候选组列表和流程状态。
     * </p>
     *
     * @param processInstanceId 流程实例ID
     * @return 流程实例信息（运行时 + 历史 + 业务合并）
     */
    public ProcessInstanceInfo getProcessInstanceInfo(String processInstanceId) {
        log.debug("[ProcessQueryService.getProcessInstanceInfo] processInstanceId={}", processInstanceId);

        // 先查运行时表
        ProcessInstance runtimeInstance = runtimeService.createProcessInstanceQuery()
                .processInstanceId(processInstanceId)
                .singleResult();

        ProcessInstanceInfo info;
        if (runtimeInstance != null) {
            info = ProcessInstanceInfo.fromRuntime(runtimeInstance, repositoryService, runtimeService);
        } else {
            // 再查历史表
            HistoricProcessInstance historicInstance = historyService.createHistoricProcessInstanceQuery()
                    .processInstanceId(processInstanceId)
                    .singleResult();

            if (historicInstance == null) {
                throw new BizException(
                        WfErrorCode.PROCESS_INSTANCE_NOT_FOUND.getCode(),
                        WfErrorCode.PROCESS_INSTANCE_NOT_FOUND.getMessage());
            }
            info = ProcessInstanceInfo.fromHistoric(historicInstance);
        }

        // 补充业务字段 - 关联查询 BizProcessMap
        var map = bizProcessMapMapper.selectByProcessInstanceId(processInstanceId);
        if (map != null) {
            info.setBizType(map.getBizType());
            info.setBizId(map.getBizId());
            info.setTitle(map.getTitle());
            info.setCandidateGroups(parseJsonToList(map.getCandidateGroups()));

            // 补充发起人姓名
            if (map.getStartUser() != null) {
                info.setStartUserName(userApi.getUserName(map.getStartUser()));
                OrgDTO org = orgApi.getUserMainOrg(map.getStartUser());
                if (org != null) {
                    info.setStartOrgName(org.getOrgName());
                }
            }
        }

        // 补充当前节点信息（仅运行时实例）
        if (runtimeInstance != null) {
            List<String> activeActivityIds = runtimeService.getActiveActivityIds(processInstanceId);
            if (!activeActivityIds.isEmpty()) {
                info.setCurrentNodeId(activeActivityIds.get(0));
                // 节点名称需从 BPMN 模型获取，当前仅设置 nodeId
            }
            info.setProcessStatus("RUNNING");
        } else if (info.getEndTime() != null) {
            info.setProcessStatus("COMPLETED");
        } else {
            info.setProcessStatus("CANCELLED");
        }

        return info;
    }

    // ========== 流程进度图 ==========

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
     * 获取流程进度图结构化数据（C.2 JSON）
     *
     * @param processInstanceId 流程实例ID
     * @return 流程进度图结构化数据
     */
    public ProcessDiagramDTO getProcessNodes(String processInstanceId) {
        log.debug("[ProcessQueryService.getProcessNodes] processInstanceId={}", processInstanceId);

        ProcessDiagramDTO dto = new ProcessDiagramDTO();
        dto.setProcessInstanceId(processInstanceId);

        // 获取流程定义KEY
        ProcessInstance runtimeInstance = runtimeService.createProcessInstanceQuery()
                .processInstanceId(processInstanceId)
                .singleResult();

        String processDefinitionKey = null;
        if (runtimeInstance != null) {
            processDefinitionKey = extractKey(runtimeInstance.getProcessDefinitionId());
        } else {
            HistoricProcessInstance hpi = historyService.createHistoricProcessInstanceQuery()
                    .processInstanceId(processInstanceId)
                    .singleResult();
            if (hpi != null) {
                processDefinitionKey = hpi.getProcessDefinitionKey();
            } else {
                throw new BizException(
                        WfErrorCode.PROCESS_INSTANCE_NOT_FOUND.getCode(),
                        WfErrorCode.PROCESS_INSTANCE_NOT_FOUND.getMessage());
            }
        }
        dto.setProcessDefinitionKey(processDefinitionKey);

        // 获取历史活动节点
        List<HistoricActivityInstance> activities = historyService.createHistoricActivityInstanceQuery()
                .processInstanceId(processInstanceId)
                .orderByHistoricActivityInstanceStartTime()
                .asc()
                .list();

        // 获取当前活动节点
        Set<String> activeActivityIds = new HashSet<>();
        if (runtimeInstance != null) {
            activeActivityIds = new HashSet<>(runtimeService.getActiveActivityIds(processInstanceId));
        }

        List<ProcessDiagramNodeDTO> nodes = new ArrayList<>();
        for (HistoricActivityInstance activity : activities) {
            if (!"userTask".equals(activity.getActivityType())
                    && !"startEvent".equals(activity.getActivityType())
                    && !"endEvent".equals(activity.getActivityType())
                    && !"exclusiveGateway".equals(activity.getActivityType())) {
                continue;
            }

            ProcessDiagramNodeDTO node = new ProcessDiagramNodeDTO();
            node.setNodeKey(activity.getActivityId());
            node.setNodeName(activity.getActivityName());
            node.setNodeType(activity.getActivityType());
            node.setAssignee(activity.getAssignee());

            if (activity.getEndTime() != null) {
                node.setStatus("COMPLETED");
                node.setStartTime(convertToLocalDateTime(activity.getStartTime()));
                node.setEndTime(convertToLocalDateTime(activity.getEndTime()));
            } else if (activeActivityIds.contains(activity.getActivityId())) {
                node.setStatus("ACTIVE");
                node.setStartTime(convertToLocalDateTime(activity.getStartTime()));
            } else {
                node.setStatus("PENDING");
            }

            // 补充处理人姓名
            if (activity.getAssignee() != null) {
                node.setAssigneeName(userApi.getUserName(activity.getAssignee()));
            }

            nodes.add(node);
        }

        dto.setNodes(nodes);
        return dto;
    }

    // ========== 审批日志 ==========

    /**
     * 查询流程历史节点列表（C.3）
     *
     * @param processInstanceId 流程实例ID
     * @return 审批日志列表（不含服务任务等自动节点）
     */
    public List<ApprovalLogDTO> getProcessHistory(String processInstanceId) {
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

        // 用户任务类型的历史节点 + 末尾追加一条 SUBMIT "申请提交" 节点
        // （Flowable HistoricActivityInstance startEvent 不归 userTask，且没有 assignee/taskId/comment，
        //  无法直接转 ApprovalLogDTO；从 HistoricProcessInstance 取 startUserId/startTime 拼出来）
        List<ApprovalLogDTO> logs = activities.stream()
                .filter(a -> a.getActivityType() != null && a.getActivityType().startsWith("userTask"))
                .map(this::toApprovalLogDTO)
                .collect(Collectors.toCollection(ArrayList::new));
        logs.add(buildSubmitLog(instance));
        return logs;
    }

    /**
     * 构造"申请提交" SUBMIT 节点，operator 取 HistoricProcessInstance.startUserId，
     * operateTime 取 startTime，opinion 留 null（业务层若有 reason 字段由前端兜底填）。
     */
    private ApprovalLogDTO buildSubmitLog(HistoricProcessInstance instance) {
        ApprovalLogDTO dto = new ApprovalLogDTO();
        dto.setNodeKey("start_event");
        dto.setNodeName("申请提交");
        dto.setAction("SUBMIT");
        String startUserId = instance.getStartUserId();
        // ProcessStartService 启动流程时没调 identityService.setAuthenticatedUserId，
        // 所以 ACT_HI_PROCINST.START_USER_ID_ 历史值都是 NULL，从 BIZ_PROCESS_MAP.start_user
        // 兜底取（业务侧 startProcess 时已写入该列）
        if (startUserId == null || startUserId.isBlank()) {
            try {
                BizProcessMap map = bizProcessMapMapper.selectByProcessInstanceId(instance.getId());
                if (map != null) {
                    startUserId = map.getStartUser();
                }
            } catch (Exception e) {
                log.warn("BIZ_PROCESS_MAP 查询失败 pid={}", instance.getId(), e);
            }
        }
        if (startUserId != null && !startUserId.isBlank()) {
            dto.setOperator(startUserId);
            try {
                dto.setOperatorName(userApi.getUserName(startUserId));
            } catch (Exception e) {
                log.warn("申请人姓名查询失败: startUserId={}", startUserId, e);
            }
            try {
                OrgDTO org = orgApi.getUserMainOrg(startUserId);
                if (org != null) {
                    dto.setOperatorOrgName(org.getOrgName());
                }
            } catch (Exception e) {
                log.warn("申请人主机构查询失败: startUserId={}", startUserId, e);
            }
        }
        if (instance.getStartTime() != null) {
            dto.setOperateTime(instance.getStartTime().toInstant().atZone(ZoneId.systemDefault()).toLocalDateTime());
        }
        return dto;
    }

    private ApprovalLogDTO toApprovalLogDTO(HistoricActivityInstance activity) {
        ApprovalLogDTO dto = new ApprovalLogDTO();
        dto.setNodeKey(activity.getActivityId());
        dto.setNodeName(activity.getActivityName());

        // 获取操作人信息
        if (activity.getAssignee() != null) {
            dto.setOperator(activity.getAssignee());
            // 获取操作人姓名
            String userName = userApi.getUserName(activity.getAssignee());
            dto.setOperatorName(userName);
            // 获取操作人机构名称
            OrgDTO org = orgApi.getUserMainOrg(activity.getAssignee());
            if (org != null) {
                dto.setOperatorOrgName(org.getOrgName());
            }
        }

        // 获取审批结果与审批意见（2026-05-20 修复）
        //  - 审批结果：从历史任务的 task local variable `approved` 取（true→APPROVE / false→REJECT）
        //  - 审批意见：从 ACT_HI_COMMENT 取 type=APPROVE/REJECT 的 fullMessage
        //  注意：原代码用 activity.getActivityId()（节点 key，如 branch_mgr_review）当作 taskId
        //  查 HistoricTaskInstance 永远查不到；taskId 必须用 activity.getTaskId()。
        //  另外原代码从 task local var 取 opinion，但 TaskOperationService 是通过
        //  addComment 存的，task vars 里不会有 opinion 字段。
        String taskId = activity.getTaskId();
        if (taskId != null) {
            try {
                HistoricTaskInstance hti = historyService.createHistoricTaskInstanceQuery()
                        .taskId(taskId)
                        .includeTaskLocalVariables()
                        .singleResult();
                if (hti != null) {
                    Map<String, Object> vars = hti.getTaskLocalVariables();
                    if (vars != null && vars.containsKey("approved")) {
                        Boolean approved = (Boolean) vars.get("approved");
                        dto.setAction(approved != null && approved ? "APPROVE" : "REJECT");
                    }
                }
                // Flowable 7 的 taskService.getTaskComments(taskId) 在 read 路径上对已完成任务
                // 返回空 list（数据库 ACT_HI_COMMENT 里 INSERT 是有的，但 API 读不出来）。
                // 实测：5 个已完成 userTask 的 ACT_HI_COMMENT type=APPROVE/REJECT 行均存在
                // 但 taskService.getTaskComments 返回空。这里直接走 native SQL 查 ACT_HI_COMMENT。
                List<Map<String, Object>> commentRows = jdbcTemplate.queryForList(
                        "SELECT TYPE_, FULL_MSG_ FROM ACT_HI_COMMENT "
                        + "WHERE TASK_ID_ = ? AND TYPE_ IN ('APPROVE', 'REJECT') "
                        + "ORDER BY TIME_ ASC",
                        taskId);
                for (Map<String, Object> row : commentRows) {
                    String type = (String) row.get("TYPE_");
                    if ("APPROVE".equals(type) || "REJECT".equals(type)) {
                        if (dto.getAction() == null) {
                            dto.setAction(type);
                        }
                        // FULL_MSG_ 列类型是 LONGBLOB，JdbcTemplate 取到的是 byte[]，
                        // 直接 toString() 会拿到 "[B@xxx" 哈希形式，必须按 UTF-8 解码。
                        Object fullMsg = row.get("FULL_MSG_");
                        if (fullMsg instanceof byte[]) {
                            dto.setOpinion(new String((byte[]) fullMsg, java.nio.charset.StandardCharsets.UTF_8));
                        } else if (fullMsg != null) {
                            dto.setOpinion(fullMsg.toString());
                        }
                        break;
                    }
                }
            } catch (Exception e) {
                log.warn("获取审批结果/意见失败: activityId={}, taskId={}", activity.getActivityId(), taskId, e);
            }
        }

        // 设置操作时间
        if (activity.getStartTime() != null) {
            dto.setOperateTime(activity.getStartTime().toInstant().atZone(ZoneId.systemDefault()).toLocalDateTime());
        }

        return dto;
    }

    // ========== 工具方法 ==========

    private String extractKey(String processDefinitionId) {
        if (processDefinitionId == null) return null;
        return processDefinitionId.split(":")[0];
    }

    private LocalDateTime convertToLocalDateTime(java.util.Date date) {
        if (date == null) return null;
        return date.toInstant().atZone(ZoneId.systemDefault()).toLocalDateTime();
    }

    private List<String> parseJsonToList(String json) {
        if (json == null || json.isEmpty()) {
            return new ArrayList<>();
        }
        try {
            return objectMapper.readValue(json, new TypeReference<List<String>>() {});
        } catch (Exception e) {
            log.warn("JSON 解析失败: {}", json, e);
            return new ArrayList<>();
        }
    }

    // ========== 内部 DTO ==========

    /**
     * 流程实例信息 DTO
     * <p>
     * 包含流程定义基础信息和业务扩展字段。
     * 基础字段由 Flowable 运行时/历史数据填充，
     * 业务字段（bizType、bizId、title 等）由 BizProcessMap 关联查询补充。
     * </p>
     */
    @lombok.Data
    public static class ProcessInstanceInfo {
        // --- 基础字段 ---
        private String processInstanceId;
        private String processDefinitionKey;
        private String processDefinitionName;
        private Integer processDefinitionVersion;
        private String businessKey;
        private String startUserId;
        private String currentActivityId;
        private Boolean isEnded;
        private LocalDateTime startTime;
        private LocalDateTime endTime;
        private Long durationMs;

        // --- 业务字段 ---
        /** 业务类型 */
        private String bizType;
        /** 业务ID */
        private String bizId;
        /** 流程标题 */
        private String title;
        /** 发起人姓名 */
        private String startUserName;
        /** 发起人机构名称 */
        private String startOrgName;
        /** 流程状态：RUNNING / COMPLETED / CANCELLED */
        private String processStatus;
        /** 当前节点ID */
        private String currentNodeId;
        /** 当前节点名称 */
        private String currentNodeName;
        /** 当前处理人工号 */
        private String currentAssignee;
        /** 当前处理人姓名 */
        private String currentAssigneeName;
        /** 候选组列表 */
        private List<String> candidateGroups;

        public static ProcessInstanceInfo fromRuntime(ProcessInstance pi, RepositoryService repoService, RuntimeService runtimeService) {
            String pdId = pi.getProcessDefinitionId();
            ProcessDefinition pd = null;
            try {
                pd = repoService.getProcessDefinition(pdId);
            } catch (Exception e) {
                log.warn("获取流程定义失败: {}", pdId, e);
            }

            List<String> activeActivityIds = new ArrayList<>(runtimeService.getActiveActivityIds(pi.getId()));

            ProcessInstanceInfo info = new ProcessInstanceInfo();
            info.setProcessInstanceId(pi.getId());
            info.setProcessDefinitionKey(extractKey(pdId));
            info.setProcessDefinitionName(pd != null ? pd.getName() : null);
            info.setProcessDefinitionVersion(pd != null ? pd.getVersion() : null);
            info.setBusinessKey(pi.getBusinessKey());
            info.setStartUserId(pi.getStartUserId());
            info.setCurrentActivityId(activeActivityIds.isEmpty() ? null : activeActivityIds.get(0));
            info.setIsEnded(false);
            info.setStartTime(pi.getStartTime() != null
                    ? pi.getStartTime().toInstant().atZone(ZoneId.systemDefault()).toLocalDateTime()
                    : null);
            return info;
        }

        public static ProcessInstanceInfo fromHistoric(HistoricProcessInstance hpi) {
            ProcessInstanceInfo info = new ProcessInstanceInfo();
            info.setProcessInstanceId(hpi.getId());
            info.setProcessDefinitionKey(hpi.getProcessDefinitionKey());
            info.setProcessDefinitionName(null); // HistoricProcessInstance 不直接提供名称
            info.setProcessDefinitionVersion(null); // HistoricProcessInstance 不直接提供版本
            info.setBusinessKey(hpi.getBusinessKey());
            info.setStartUserId(hpi.getStartUserId());
            info.setCurrentActivityId(null); // 历史实例无法确定当前节点
            info.setIsEnded(hpi.getEndTime() != null);
            info.setStartTime(hpi.getStartTime() != null
                    ? hpi.getStartTime().toInstant().atZone(ZoneId.systemDefault()).toLocalDateTime()
                    : null);
            info.setEndTime(hpi.getEndTime() != null
                    ? hpi.getEndTime().toInstant().atZone(ZoneId.systemDefault()).toLocalDateTime()
                    : null);
            info.setDurationMs(hpi.getDurationInMillis());
            return info;
        }

        private static String extractKey(String processDefinitionId) {
            if (processDefinitionId == null) return null;
            return processDefinitionId.split(":")[0];
        }
    }

}
