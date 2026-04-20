package com.bank.branch.platform.performance.facade.assembler;

import com.bank.branch.platform.performance.api.dto.PerfRunTaskDTO;
import com.bank.branch.platform.performance.entity.PerfRunTask;

/**
 * 运行任务日志 DTO 装配器 (骨架, 实现在绿阶段).
 *
 * <p>职责仅做字段映射, 不查 DB / 不调 Service, 以便 Facade 可单元测试隔离。
 *
 * <p>v1.2: id 为 String (对齐生产 DDL varchar(32))。
 * DDL 字段 {@code task_key} 直接映射为 DTO 同名字段 {@code taskKey};
 * DTO 的 "任务编号" 语义对齐 Task 4.1 决策 (Mapper 层 selectByTaskNo 映射 task_key)。
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
        // 红阶段骨架: 绿阶段补实现
        return null;
    }
}
