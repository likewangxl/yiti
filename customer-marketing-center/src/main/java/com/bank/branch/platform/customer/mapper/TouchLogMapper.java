package com.bank.branch.platform.customer.mapper;

import com.bank.branch.platform.customer.entity.TouchLog;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * 触达日志 Mapper 接口，操作 touch_log 表。
 * <p>
 * 该表无逻辑删除字段，日志记录不可删除，保证审计可追溯。
 * 唯一索引 uk_task_uuid(touch_task_id, client_uuid) 保证移动端幂等性。
 * </p>
 */
@Mapper
public interface TouchLogMapper {

    /**
     * 按触达任务ID查询该任务的所有日志（按 log_time 降序）。
     *
     * @param touchTaskId 触达任务ID
     * @return 该任务的触达日志列表
     */
    List<TouchLog> selectByTaskId(@Param("touchTaskId") String touchTaskId);

    /**
     * 按触达任务ID和客户端幂等键查询（幂等性检查用，防重复提交）。
     *
     * @param touchTaskId 触达任务ID
     * @param clientUuid  客户端幂等键
     * @return 触达日志实体，不存在时返回 null
     */
    TouchLog selectByTaskIdAndClientUuid(@Param("touchTaskId") String touchTaskId,
                                         @Param("clientUuid") String clientUuid);

    /**
     * 插入新触达日志。
     *
     * @param entity 触达日志实体
     * @return 受影响行数
     */
    int insert(TouchLog entity);

    /**
     * 统计某个触达任务的日志总数（用于判断是否为首次日志）。
     *
     * @param taskId 触达任务ID
     * @return 日志条数
     */
    Long countByTaskId(@Param("taskId") String taskId);
}
