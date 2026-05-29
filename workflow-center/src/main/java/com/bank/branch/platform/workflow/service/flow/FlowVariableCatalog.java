package com.bank.branch.platform.workflow.service.flow;

import com.bank.branch.platform.workflow.api.dto.flow.FlowVariableDTO;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 审批流程条件分支可用的流程变量白名单（按 bizType 维护）。
 *
 * <p>仅白名单内字段可用于条件构造器，杜绝注入风险。
 * 变量来源以各业务模块 startProcess 时实际写入的 vars.put 为准：
 * <ul>
 *   <li>ALLOC_ADJUST（分配关系调整）：AllocAdjustService.submit 实际写入
 *       bizKind / allocDim（custType 字段不存在于该流程）</li>
 *   <li>TARGET_ADJUST（目标修正）：TargetAdjustService.submit 实际写入
 *       subjectType / subjectId / cycleKey（bizKind/custType 字段不存在于该流程）</li>
 * </ul>
 * applyId / planId / custId / custNo / originalOwnerEmpId 等系统内部键
 * 属于流程路由系统键，不开放给条件构造器使用，故不在白名单内。
 * </p>
 */
@Component
public class FlowVariableCatalog {

    private static final Map<String, List<FlowVariableDTO>> CATALOG = Map.of(
            // ALLOC_ADJUST：AllocAdjustService 实际写入 bizKind / allocDim
            "ALLOC_ADJUST", List.of(
                    new FlowVariableDTO("bizKind",  "业务种类",  "string"),
                    new FlowVariableDTO("allocDim", "分配维度",  "string")),
            // TARGET_ADJUST：TargetAdjustService 实际写入 subjectType / subjectId / cycleKey
            "TARGET_ADJUST", List.of(
                    new FlowVariableDTO("subjectType", "主体类型", "string"),
                    new FlowVariableDTO("subjectId",   "主体标识", "string"),
                    new FlowVariableDTO("cycleKey",    "周期键",   "string"))
    );

    /**
     * 返回该业务类型可用的流程变量列表；未知 bizType 返回空列表。
     *
     * @param bizType 业务类型（如 ALLOC_ADJUST / TARGET_ADJUST）
     * @return 可用变量 DTO 列表，不可变
     */
    public List<FlowVariableDTO> variables(String bizType) {
        return CATALOG.getOrDefault(bizType, List.of());
    }

    /**
     * 返回该业务类型可用变量的 field 集合，供校验器判断条件字段是否在白名单内。
     *
     * @param bizType 业务类型
     * @return field 集合，不可变
     */
    public Set<String> fields(String bizType) {
        return variables(bizType).stream()
                .map(FlowVariableDTO::getField)
                .collect(Collectors.toSet());
    }
}
