package com.bank.branch.platform.workflow.listener;

import com.bank.branch.platform.workflow.api.dto.ApproverGroupDTO;
import com.bank.branch.platform.workflow.service.CandidateResolverService;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.flowable.engine.RepositoryService;
import org.flowable.engine.delegate.DelegateExecution;
import org.flowable.engine.delegate.ExecutionListener;
import org.flowable.engine.repository.ProcessDefinition;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;

/**
 * 按机构分组会签的多实例入口监听器。
 *
 * <p>{@code GROUP_ALL} 节点的多实例集合是 {@code approverGroups}，每个元素为一个
 * {@link ApproverGroupDTO}。监听器只接受节点候选配置中的 {@code VAR:变量名}，从启动
 * 流程变量读取机构组快照并做严格校验；配置缺失、类型不匹配或任一组无审批人时直接
 * 抛错，使流程无法在审批人不完整的情况下静默流转。</p>
 */
@Slf4j
@Component("multiInstanceApproverGroupResolver")
@RequiredArgsConstructor
public class MultiInstanceApproverGroupResolver implements ExecutionListener {

    /** BPMN 多实例集合变量名。 */
    public static final String VAR_APPROVER_GROUPS = "approverGroups";

    /** BPMN 多实例元素变量名；任务监听器从当前元素读取。 */
    public static final String VAR_APPROVER_GROUP = "approverGroup";

    private static final String VAR_REJECTED = "rejected";
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();
    private static final TypeReference<List<ApproverGroupDTO>> GROUP_LIST_TYPE = new TypeReference<>() {};

    private final CandidateResolverService candidateResolverService;
    private final RepositoryService repositoryService;

    /**
     * 在分组多实例节点入口解析并写入机构组集合。
     *
     * @param execution Flowable 委托执行对象
     */
    @Override
    public void notify(DelegateExecution execution) {
        String processDefinitionId = execution.getProcessDefinitionId();
        String nodeKey = execution.getCurrentActivityId();
        if (execution.getVariable(VAR_REJECTED) == null) {
            execution.setVariable(VAR_REJECTED, false);
        }
        try {
            ProcessDefinition processDefinition = repositoryService.getProcessDefinition(processDefinitionId);
            String processDefinitionKey = processDefinition != null
                    ? processDefinition.getKey() : processDefinitionId;
            List<String> candidates = candidateResolverService
                    .resolveCandidates(processDefinitionKey, nodeKey);
            List<ApproverGroupDTO> groups = resolveGroups(candidates, execution);
            execution.setVariable(VAR_APPROVER_GROUPS, groups);
            log.info("[MultiInstanceApproverGroupResolver] 节点 {} 解析机构审批组 {} 个", nodeKey, groups.size());
        } catch (IllegalStateException e) {
            log.warn("[MultiInstanceApproverGroupResolver] 节点 {} 机构审批组配置无效，流程拒绝继续: {}",
                    nodeKey, e.getMessage());
            throw e;
        } catch (Exception e) {
            log.warn("[MultiInstanceApproverGroupResolver] 节点 {} 机构审批组解析失败，流程拒绝继续: {}",
                    nodeKey, e.getMessage());
            throw new IllegalStateException("机构审批分组解析失败，流程无法继续", e);
        }
    }

    private List<ApproverGroupDTO> resolveGroups(List<String> candidates, DelegateExecution execution) {
        if (candidates == null || candidates.isEmpty()) {
            throw invalid("未配置 VAR 审批人变量");
        }
        List<ApproverGroupDTO> groups = new ArrayList<>();
        for (String candidate : candidates) {
            if (candidate == null || candidate.isBlank()) {
                continue;
            }
            if (!candidate.startsWith("VAR:")) {
                throw invalid("仅支持 VAR 审批人变量，实际为 " + candidate);
            }
            String variableName = candidate.substring("VAR:".length()).trim();
            if (variableName.isEmpty()) {
                throw invalid("VAR 审批人变量名为空");
            }
            groups.addAll(readGroups(execution.getVariable(variableName)));
        }
        return normalizeGroups(groups);
    }

    /**
     * 将流程变量中的 DTO、Map、JSON 数组或单个对象统一解析为分组列表。
     * 包级可见，供任务创建监听器将 Flowable 反序列化后的元素恢复为强类型 DTO。
     */
    static List<ApproverGroupDTO> readGroups(Object value) {
        if (value == null) {
            throw invalid("VAR 流程变量为空");
        }
        try {
            if (value instanceof String json) {
                if (json.isBlank()) {
                    throw invalid("VAR 流程变量为空");
                }
                return OBJECT_MAPPER.readValue(json, GROUP_LIST_TYPE);
            }
            if (value instanceof ApproverGroupDTO group) {
                return List.of(group);
            }
            if (value instanceof Map<?, ?> map) {
                return List.of(OBJECT_MAPPER.convertValue(map, ApproverGroupDTO.class));
            }
            if (value instanceof Collection<?> collection) {
                List<ApproverGroupDTO> groups = new ArrayList<>();
                for (Object item : collection) {
                    if (item instanceof ApproverGroupDTO group) {
                        groups.add(group);
                    } else if (item instanceof Map<?, ?> map) {
                        groups.add(OBJECT_MAPPER.convertValue(map, ApproverGroupDTO.class));
                    } else {
                        throw invalid("分组元素类型不支持: "
                                + (item == null ? "null" : item.getClass().getName()));
                    }
                }
                return groups;
            }
            throw invalid("VAR 流程变量类型不支持: " + value.getClass().getName());
        } catch (IllegalStateException e) {
            throw e;
        } catch (Exception e) {
            throw invalid("VAR 流程变量不是合法机构审批分组 JSON");
        }
    }

    /** 将单个当前多实例元素解析成经过相同校验的 DTO。 */
    static ApproverGroupDTO readGroup(Object value) {
        List<ApproverGroupDTO> groups = normalizeGroups(readGroups(value));
        if (groups.size() != 1) {
            throw invalid("当前多实例元素必须且只能包含一个机构审批分组");
        }
        return groups.get(0);
    }

    private static List<ApproverGroupDTO> normalizeGroups(List<ApproverGroupDTO> groups) {
        if (groups == null || groups.isEmpty()) {
            throw invalid("机构审批分组为空");
        }
        Map<String, ApproverGroupDTO> unique = new LinkedHashMap<>();
        for (ApproverGroupDTO group : groups) {
            if (group == null || group.getGroupKey() == null || group.getGroupKey().isBlank()) {
                throw invalid("机构审批分组机构编码为空");
            }
            if (group.getGroupName() == null || group.getGroupName().isBlank()) {
                throw invalid("机构审批分组机构名称为空: " + group.getGroupKey());
            }
            if (group.getApproverEmpIds() == null || group.getApproverEmpIds().isEmpty()) {
                throw invalid("机构审批分组未配置负责人: " + group.getGroupKey());
            }
            LinkedHashSet<String> empIds = new LinkedHashSet<>();
            for (String empId : group.getApproverEmpIds()) {
                if (empId != null && !empId.isBlank()) {
                    empIds.add(empId);
                }
            }
            if (empIds.isEmpty()) {
                throw invalid("机构审批分组未配置有效负责人: " + group.getGroupKey());
            }
            String groupKey = group.getGroupKey().trim();
            if (unique.containsKey(groupKey)) {
                throw invalid("机构审批分组重复: " + groupKey);
            }
            unique.put(groupKey, new ApproverGroupDTO(
                    groupKey,
                    group.getGroupName().trim(),
                    List.copyOf(empIds)));
        }
        return Collections.unmodifiableList(new ArrayList<>(unique.values()));
    }

    private static IllegalStateException invalid(String message) {
        return new IllegalStateException("机构审批分组配置无效: " + message);
    }
}
