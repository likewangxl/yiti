package com.bank.branch.platform.customer.api;

import com.bank.branch.platform.customer.entity.TouchTask;

/**
 * 触达任务对外查询接口。
 * <p>
 * 供其他模块查询触达任务状态使用。
 * 只提供只读查询，不暴露创建/更新操作。
 * </p>
 *
 * @author customer-marketing-center
 * @since V1.0
 */
public interface TouchTaskQueryApi {

    /**
     * 按 ID 查询触达任务详情。
     *
     * @param taskId 任务ID
     * @return 触达任务实体，不存在时返回 null
     */
    TouchTask getTaskById(String taskId);

    /**
     * 查询触达任务的当前执行状态。
     * <p>
     * 状态枚举：PENDING-待处理/SUCCESS-已完成/CANCELLED-已取消。
     * </p>
     *
     * @param taskId 任务ID
     * @return 任务状态字符串（对应 TouchTaskStatus.code），任务不存在时返回 null
     */
    String getTaskStatus(String taskId);

    /**
     * 查询触达任务的 SLA 状态。
     * <p>
     * SLA 状态枚举：GREEN-正常/YELLOW-预警/RED-超期。
     * SLA 状态由定时任务根据 warning_time 和 plan_finish_time 周期性刷新。
     * </p>
     *
     * @param taskId 任务ID
     * @return SLA 状态字符串（对应 SlaStatus.code），任务不存在时返回 null
     */
    String getSlaStatus(String taskId);
}
