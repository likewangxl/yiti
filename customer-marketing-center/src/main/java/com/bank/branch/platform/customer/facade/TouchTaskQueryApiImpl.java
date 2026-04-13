package com.bank.branch.platform.customer.facade;

import com.bank.branch.platform.customer.api.TouchTaskQueryApi;
import com.bank.branch.platform.customer.entity.TouchTask;
import com.bank.branch.platform.customer.mapper.TouchTaskMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * 触达任务对外查询接口实现。
 * <p>
 * 实现 {@link TouchTaskQueryApi} 接口，直接委托 {@link TouchTaskMapper} 完成只读查询。
 * 提供任务状态和 SLA 状态的快捷查询方法，避免调用方多次查询实体。
 * </p>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class TouchTaskQueryApiImpl implements TouchTaskQueryApi {

    private final TouchTaskMapper touchTaskMapper;

    /**
     * 按 ID 查询触达任务详情。
     *
     * @param taskId 任务ID
     * @return 触达任务实体，不存在时返回 null
     */
    @Override
    public TouchTask getTaskById(String taskId) {
        log.debug("[TouchTaskQueryApiImpl.getTaskById] taskId={}", taskId);
        return touchTaskMapper.selectById(taskId);
    }

    /**
     * 查询触达任务的当前执行状态。
     *
     * @param taskId 任务ID
     * @return 任务状态字符串，任务不存在时返回 null
     */
    @Override
    public String getTaskStatus(String taskId) {
        log.debug("[TouchTaskQueryApiImpl.getTaskStatus] taskId={}", taskId);
        TouchTask task = touchTaskMapper.selectById(taskId);
        return task != null ? task.getTaskStatus() : null;
    }

    /**
     * 查询触达任务的 SLA 状态。
     *
     * @param taskId 任务ID
     * @return SLA 状态字符串，任务不存在时返回 null
     */
    @Override
    public String getSlaStatus(String taskId) {
        log.debug("[TouchTaskQueryApiImpl.getSlaStatus] taskId={}", taskId);
        TouchTask task = touchTaskMapper.selectById(taskId);
        return task != null ? task.getSlaStatus() : null;
    }
}
