package com.bank.branch.platform.customer.mapper;

import com.bank.branch.platform.customer.entity.TouchTask;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * 触达任务 Mapper 接口，操作 touch_task 表。
 * <p>
 * 该表无逻辑删除字段，通过 task_status 管理任务生命周期。
 * SLA 状态由定时任务周期性刷新，通过 selectPendingForSlaRefresh 查询待刷新任务。
 * </p>
 */
@Mapper
public interface TouchTaskMapper {

    /**
     * 按 id 查询触达任务。
     *
     * @param id 任务ID
     * @return 触达任务实体，不存在时返回 null
     */
    TouchTask selectById(@Param("id") String id);

    /**
     * 按任务编号查询（用于对外展示和唯一性预检）。
     *
     * @param taskNo 任务编号
     * @return 触达任务实体，不存在时返回 null
     */
    TouchTask selectByTaskNo(@Param("taskNo") String taskNo);

    /**
     * 分页查询触达任务列表。
     * <p>
     * keyword 模糊搜索 task_no，status 和 assigneeEmpId 精确匹配。
     * </p>
     *
     * @param keyword       关键词（搜索 task_no），可为 null
     * @param status        任务状态过滤（PENDING/SUCCESS/CANCELLED），可为 null
     * @param assigneeEmpId 执行人工号过滤，可为 null
     * @param offset        偏移量
     * @param limit         每页条数
     * @return 触达任务列表
     */
    List<TouchTask> selectPage(@Param("keyword") String keyword,
                               @Param("status") String status,
                               @Param("assigneeEmpId") String assigneeEmpId,
                               @Param("offset") int offset,
                               @Param("limit") int limit);

    /**
     * 统计分页查询的总记录数（与 selectPage 共享 WHERE 条件）。
     *
     * @param keyword       关键词，可为 null
     * @param status        任务状态过滤，可为 null
     * @param assigneeEmpId 执行人工号过滤，可为 null
     * @return 总记录数
     */
    long countPage(@Param("keyword") String keyword,
                   @Param("status") String status,
                   @Param("assigneeEmpId") String assigneeEmpId);

    /**
     * 查询待刷新 SLA 状态的 PENDING 任务（定时任务使用）。
     * <p>
     * 查询 task_status='PENDING' 且 plan_finish_time 不为 null 的任务，
     * 定时任务根据当前时间与 warning_time/plan_finish_time 对比更新 sla_status。
     * </p>
     *
     * @return 待刷新 SLA 的触达任务列表
     */
    List<TouchTask> selectPendingForSlaRefresh();

    /**
     * 插入新触达任务。
     *
     * @param entity 触达任务实体
     * @return 受影响行数
     */
    int insert(TouchTask entity);

    /**
     * 按 id 更新触达任务（动态 SET，仅更新非 null 字段）。
     *
     * @param entity 包含 id 及待更新字段的触达任务实体
     * @return 受影响行数
     */
    int updateById(TouchTask entity);

    /**
     * 统计指定客户的在途触达任务数量（PENDING 或 IN_PROGRESS 状态）。
     * <p>
     * 供 CustomerQueryApi.hasRunningProcess("TOUCH_TASK") 使用。
     * </p>
     *
     * @param custId 客户ID
     * @return 在途任务数量
     */
    Long countActiveByCust(@Param("custId") String custId);

    /**
     * 查询指定客户的所有在途触达任务（PENDING 或 IN_PROGRESS 状态）。
     * <p>
     * 供 CustomerQueryApi.listRunningProcesses 使用，按 created_time DESC 排序。
     * </p>
     *
     * @param custId 客户ID
     * @return 在途触达任务列表
     */
    List<TouchTask> selectActiveByCust(@Param("custId") String custId);
}
