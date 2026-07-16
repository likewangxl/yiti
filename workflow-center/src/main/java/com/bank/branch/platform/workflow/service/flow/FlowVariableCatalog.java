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

    /**
     * 发起人机构级别变量：由 {@code ProcessStartService} 启动流程时统一注入
     * （1=总部 / 2=分行 / 3=支行）。供网关按 2级/3级机构走不同审批路径，
     * 所有 bizType 均可用，故各业务变量列表都追加该项。
     */
    private static final FlowVariableDTO START_ORG_LEVEL =
            new FlowVariableDTO("startOrgLevel", "发起人机构级别", "number");

    private static final Map<String, List<FlowVariableDTO>> CATALOG = Map.of(
            // ALLOC_ADJUST：AllocAdjustService 写入 bizKind / allocDim；系统注入 startOrgLevel；
            // 经办审批时产出路由变量 corpRouteTo（部门走向 LEADER/OWNER）/ finRouteTo（资财部走向 LEADER/END）
            "ALLOC_ADJUST", List.of(
                    new FlowVariableDTO("bizKind",  "业务种类",  "string"),
                    new FlowVariableDTO("allocDim", "分配维度",  "string"),
                    START_ORG_LEVEL,
                    new FlowVariableDTO("corpRouteTo", "部门审批走向", "string"),
                    new FlowVariableDTO("finRouteTo",  "资财部审批走向", "string")),
            // TARGET_ADJUST：TargetAdjustService 实际写入 subjectType / subjectId / cycleKey + 系统注入 startOrgLevel
            "TARGET_ADJUST", List.of(
                    new FlowVariableDTO("subjectType", "主体类型", "string"),
                    new FlowVariableDTO("subjectId",   "主体标识", "string"),
                    new FlowVariableDTO("cycleKey",    "周期键",   "string"),
                    START_ORG_LEVEL)
    );

    /**
     * VAR 审批人可选的「名单类流程变量」目录（按 bizType）。
     * <p>这些变量由提交方启动流程时写入，值为审批人工号（单值或列表），供审批节点
     * 选「流程变量」类型审批人时下拉选择，避免手输变量名出错。</p>
     */
    private static final Map<String, List<FlowVariableDTO>> APPROVER_VAR_CATALOG = Map.of(
            // ALLOC_ADJUST：AllocAdjustService.submit 写入 originalOwnerEmpIds（原业绩分配名单）
            // 与 originalOwnerOrgLeaderEmpIds（原业绩所属 2 级机构 BRANCH_HEAD 负责人名单）
            "ALLOC_ADJUST", List.of(
                    new FlowVariableDTO("originalOwnerEmpIds", "原业绩所属人", "list"),
                    new FlowVariableDTO("originalOwnerOrgLeaderEmpIds", "原业绩所属机构负责人", "list"))
            // TARGET_ADJUST 无 VAR 审批人变量
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
     * 返回该业务类型 VAR 审批人可选的「名单类流程变量」；未知 bizType 返回空列表。
     *
     * @param bizType 业务类型
     * @return 审批人变量 DTO 列表，不可变
     */
    public List<FlowVariableDTO> approverVariables(String bizType) {
        return APPROVER_VAR_CATALOG.getOrDefault(bizType, List.of());
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
