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

    // ===================== 契约 §5 扩展方法 =====================

    /**
     * 按工作流业务键查询触达任务（格式 TOUCH:{taskId}）。
     *
     * @param businessKey 工作流业务键
     * @return 触达任务实体，不存在时返回 null
     */
    TouchTask selectByBusinessKey(@Param("businessKey") String businessKey);

    /**
     * 查询指定员工的所有触达任务（全状态，按 created_time DESC）。
     *
     * @param empId 员工工号
     * @return 触达任务列表
     */
    List<TouchTask> selectByEmp(@Param("empId") String empId);

    /**
     * 查询指定员工指定状态的触达任务（按 created_time DESC）。
     *
     * @param empId  员工工号
     * @param status 任务状态
     * @return 触达任务列表
     */
    List<TouchTask> selectByEmpAndStatus(@Param("empId") String empId, @Param("status") String status);

    /**
     * 统计指定员工在给定状态列表中的触达任务数量。
     * <p>
     * 供 countRunningTouchTasks 使用，传入 ["PENDING","IN_PROGRESS"]。
     * </p>
     *
     * @param empId    员工工号
     * @param statuses 状态列表
     * @return 任务数量
     */
    Long countByEmpAndStatuses(@Param("empId") String empId, @Param("statuses") List<String> statuses);

    /**
     * 查询指定客户的所有触达历史（含所有状态，按 created_time DESC）。
     *
     * @param custId 客户ID
     * @return 触达任务列表
     */
    List<TouchTask> selectByCustOrderByCreatedDesc(@Param("custId") String custId);

    /**
     * 查询指定客户在指定机构的触达历史（按 created_time DESC）。
     *
     * @param custId  客户ID
     * @param orgCode 机构代码（对应 org_id 字段）
     * @return 触达任务列表
     */
    List<TouchTask> selectByCustAndOrg(@Param("custId") String custId, @Param("orgCode") String orgCode);

    /**
     * 统计指定客户在指定机构已完成首次触达的次数。
     * <p>
     * 查询条件：task_type='FIRST_TOUCH' AND task_status='SUCCESS'
     * AND cust_id=? AND org_id=?
     * </p>
     *
     * @param custId  客户ID
     * @param orgCode 机构代码
     * @return 完成首次触达的记录数
     */
    Long countFirstTouchSuccess(@Param("custId") String custId, @Param("orgCode") String orgCode);

    /**
     * 统计指定机构在时间范围内的触达任务数量（status 为 null 则统计全部状态）。
     *
     * @param orgCode   机构代码
     * @param startDate 开始日期（yyyy-MM-dd）
     * @param endDate   结束日期（yyyy-MM-dd）
     * @param status    任务状态，为 null 时不过滤状态
     * @return 任务数量
     */
    Long countByOrgBetween(@Param("orgCode") String orgCode,
                            @Param("startDate") String startDate,
                            @Param("endDate") String endDate,
                            @Param("status") String status);

    /**
     * 统计指定机构在时间范围内 SLA 预警的触达任务数量（sla_warning=true）。
     *
     * @param orgCode   机构代码
     * @param startDate 开始日期（yyyy-MM-dd）
     * @param endDate   结束日期（yyyy-MM-dd）
     * @return SLA 预警任务数量
     */
    Long countSlaWarningByOrgBetween(@Param("orgCode") String orgCode,
                                      @Param("startDate") String startDate,
                                      @Param("endDate") String endDate);

    /**
     * 统计指定机构在时间范围内已完成触达任务的平均完成时长（小时）。
     * <p>
     * 计算方式：TIMESTAMPDIFF(MINUTE, created_time, success_time) / 60.0
     * 仅统计 task_status='SUCCESS' 且 success_time IS NOT NULL 的任务。
     * </p>
     *
     * @param orgCode   机构代码
     * @param startDate 开始日期（yyyy-MM-dd）
     * @param endDate   结束日期（yyyy-MM-dd）
     * @return 平均完成时长（小时），无数据时返回 null
     */
    Double avgDurationHoursByOrgBetween(@Param("orgCode") String orgCode,
                                         @Param("startDate") String startDate,
                                         @Param("endDate") String endDate);
}
