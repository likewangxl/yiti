package com.bank.branch.platform.performance.facade.assembler;

import com.bank.branch.platform.performance.api.dto.PerfRunTaskDTO;
import com.bank.branch.platform.performance.entity.PerfRunTask;

/**
 * 运行任务日志 DTO 装配器.
 *
 * <p>职责仅做字段映射, 不查 DB / 不调 Service, 以便 Facade 可单元测试隔离。
 *
 * <p>v1.2: id 为 String (对齐生产 DDL varchar(32))。
 * DDL 字段 {@code task_key} 直接映射为 DTO 同名字段 {@code taskKey};
 * DTO 的 "任务编号" 语义对齐 Task 4.1 决策 (Mapper 层 selectByTaskNo 内部映射 task_key,
 * 对外 Facade 层则沿用原字段名 taskKey 暴露, 不做命名翻译)。
 *
 * <p>对外字段对齐 {@link PerfRunTaskDTO} 的 12 个字段:
 * id / taskType / taskKey / dataDate / dataVersion / status / startedBy /
 * startTime / endTime / errorMsg / resultPreviewJson / createdTime;
 * Entity 的 {@code paramsJson} 属执行参数 (可能含敏感上下文), 不对外暴露。
 */
public final class RunTaskAssembler {

    private RunTaskAssembler() {
    }

    /**
     * 将运行任务实体装配为对外 DTO.
     *
     * @param entity 任务实体, 允许为 null (返回 null)
     * @return DTO 或 null
     */
    public static PerfRunTaskDTO toDto(PerfRunTask entity) {
        if (entity == null) {
            return null;
        }
        return PerfRunTaskDTO.builder()
                .id(entity.getId())
                .taskType(entity.getTaskType())
                .taskKey(entity.getTaskKey())
                .dataDate(entity.getDataDate())
                .dataVersion(entity.getDataVersion())
                .status(entity.getStatus())
                .startedBy(entity.getStartedBy())
                .startTime(entity.getStartTime())
                .endTime(entity.getEndTime())
                .errorMsg(entity.getErrorMsg())
                .resultPreviewJson(entity.getResultPreviewJson())
                .createdTime(entity.getCreatedTime())
                .build();
    }
}
