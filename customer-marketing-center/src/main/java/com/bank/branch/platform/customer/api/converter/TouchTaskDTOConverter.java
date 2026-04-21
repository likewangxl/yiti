package com.bank.branch.platform.customer.api.converter;

import com.bank.branch.platform.customer.api.dto.TouchTaskDTO;
import com.bank.branch.platform.customer.entity.TouchTask;

import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * TouchTask → TouchTaskDTO 转换器（纯静态工具类）。
 *
 * <p>转换规则：
 * <ul>
 *   <li>标量字段直接映射</li>
 *   <li>时间字段：planFinishTime → expectedFinishAt，successTime → actualFinishAt</li>
 *   <li>时间字段：createdTime → createdAt</li>
 *   <li>finishResult：仅在 taskStatus 为 SUCCESS 或 CANCELLED 时填入 taskStatus 值，否则为 null</li>
 *   <li>slaWarning：slaStatus 为 YELLOW 或 RED 时为 true，其余为 false，null 时为 false</li>
 *   <li>slaDeadline：来自 planFinishTime</li>
 *   <li>custName、assigneeEmpName、logCount 需二次查询，转换器层暂置 null，由上层 Service 补充</li>
 * </ul>
 */
public final class TouchTaskDTOConverter {

    private TouchTaskDTOConverter() {
        // 纯静态工具类，禁止实例化
    }

    /** 表示任务已终态（有 finishResult）的状态集合 */
    private static final Set<String> FINISHED_STATUSES = Set.of("SUCCESS", "CANCELLED");

    /**
     * 将单个 TouchTask 实体转换为 TouchTaskDTO。
     *
     * @param entity 实体，允许为 null
     * @return DTO；entity 为 null 时返回 null
     */
    public static TouchTaskDTO toDTO(TouchTask entity) {
        if (entity == null) {
            return null;
        }
        TouchTaskDTO dto = new TouchTaskDTO();
        dto.setId(entity.getId());
        dto.setCustId(entity.getCustId());
        dto.setOrgId(entity.getOrgId());
        dto.setAssigneeEmpId(entity.getAssigneeEmpId());
        dto.setTaskType(entity.getTaskType());
        dto.setTaskStatus(entity.getTaskStatus());
        dto.setBusinessKey(entity.getBusinessKey());
        // 时间字段重命名映射
        dto.setExpectedFinishAt(entity.getPlanFinishTime());
        dto.setSlaDeadline(entity.getPlanFinishTime());
        dto.setActualFinishAt(entity.getSuccessTime());
        dto.setCreatedAt(entity.getCreatedTime());
        // finishResult：仅终态时填入，其余置 null
        dto.setFinishResult(resolveFinishResult(entity.getTaskStatus()));
        // slaWarning：YELLOW/RED 表示已预警
        dto.setSlaWarning(resolveSlaWarning(entity.getSlaStatus()));
        // 以下字段需上层 Service 查询后补充，转换器层暂置 null
        dto.setCustName(null);              // 需查 CustMaster
        dto.setAssigneeEmpName(null);       // 需查 EmpApi
        dto.setLogCount(null);              // 需查 touch_log 计数
        return dto;
    }

    /**
     * 将实体列表转换为 DTO 列表，自动过滤 null 元素。
     *
     * @param entities 实体列表，允许为 null
     * @return DTO 列表；entities 为 null 时返回空列表
     */
    public static List<TouchTaskDTO> toDTOList(List<TouchTask> entities) {
        if (entities == null) {
            return Collections.emptyList();
        }
        return entities.stream()
                .filter(Objects::nonNull)
                .map(TouchTaskDTOConverter::toDTO)
                .collect(Collectors.toList());
    }

    /**
     * 根据 taskStatus 解析 finishResult。
     * 仅当任务处于终态（SUCCESS/CANCELLED）时返回 taskStatus，否则返回 null。
     *
     * @param taskStatus 任务状态
     * @return finishResult 值或 null
     */
    private static String resolveFinishResult(String taskStatus) {
        if (taskStatus == null) {
            return null;
        }
        return FINISHED_STATUSES.contains(taskStatus) ? taskStatus : null;
    }

    /**
     * 根据 slaStatus 判断是否触发 SLA 预警。
     * YELLOW（达预警时间）或 RED（超期）均视为已预警。
     *
     * @param slaStatus SLA 状态
     * @return true 表示已预警；null 或 GREEN 返回 false
     */
    private static Boolean resolveSlaWarning(String slaStatus) {
        if (slaStatus == null) {
            return false;
        }
        return "YELLOW".equals(slaStatus) || "RED".equals(slaStatus);
    }
}
