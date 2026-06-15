package com.bank.branch.platform.performance.mapper;

import com.bank.branch.platform.performance.entity.PerfTargetValue;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;
import java.util.Map;

/**
 * 目标值/基础值表 Mapper.
 *
 * <p>核心方法 {@link #upsertBatch(List)} 通过 MySQL {@code ON DUPLICATE KEY UPDATE}
 * 语义实现"以 UK (plan_id, subject_type, subject_id, cycle_key, metric_code) 为粒度"的
 * 批量落库；新 UK 插入、旧 UK 更新 target_value/base_value。
 * <p>selectById 由 MyBatis-Plus BaseMapper 提供.
 */
@Mapper
public interface PerfTargetValueMapper extends BaseMapper<PerfTargetValue> {

    /**
     * 批量 upsert（基于 uk_plan_subject_cycle_metric 的 ON DUPLICATE KEY UPDATE）.
     *
     * <p>MySQL INSERT ... ON DUPLICATE KEY UPDATE 返回的"受影响行数"语义：新插入记 1，
     * 已存在且发生字段更新记 2；因此入参 N 条时，返回值介于 [N, 2N] 之间。
     *
     * <p>**调用契约**（由 {@link com.bank.branch.platform.performance.service.TargetValueService} 保证）：
     * <ul>
     *   <li>{@code createdBy} 语义为本次操作人，冲突时 XML 的
     *       {@code updated_by = VALUES(created_by)} 会将其覆盖到冲突行的 updated_by，
     *       因此 Service 层必须在调用前统一覆写为当前操作人（防止调用方伪造审计字段）</li>
     *   <li>{@code baseValue=null} 时 XML 的 {@code base_value = VALUES(base_value)} 会将
     *       冲突行的 base_value 更新为 null（语义：显式清除基础值），调用方应按此约定传值</li>
     * </ul>
     *
     * @param list 目标值列表
     * @return 受影响行数（MySQL 语义：新增 1、更新 2）
     */
    int upsertBatch(@Param("list") List<PerfTargetValue> list);

    /**
     * 按 UK 查询单条目标值.
     *
     * @param planId      方案ID
     * @param subjectType 对象类型
     * @param subjectId   对象ID
     * @param cycleKey    周期键
     * @param metricCode  指标编码
     * @return 目标值，不存在返回 null
     */
    PerfTargetValue selectByUniqueKey(@Param("planId") String planId,
                                      @Param("subjectType") String subjectType,
                                      @Param("subjectId") String subjectId,
                                      @Param("cycleKey") String cycleKey,
                                      @Param("metricCode") String metricCode);

    /**
     * 按方案 + 可选过滤条件分页查询目标值.
     *
     * @param planId      方案ID（必填）
     * @param subjectType 对象类型（可空）
     * @param subjectId   对象ID（可空）
     * @param cycleKey    周期键（可空）
     * @param offset      偏移量
     * @param limit       每页大小
     * @return 目标值列表
     */
    List<PerfTargetValue> listByPlan(@Param("planId") String planId,
                                     @Param("subjectType") String subjectType,
                                     @Param("subjectId") String subjectId,
                                     @Param("cycleKey") String cycleKey,
                                     @Param("offset") int offset,
                                     @Param("limit") int limit);

    /**
     * 按方案 + 可选过滤条件计数（与 listByPlan 条件对齐）.
     *
     * @param planId      方案ID
     * @param subjectType 对象类型（可空）
     * @param subjectId   对象ID（可空）
     * @param cycleKey    周期键（可空）
     * @return 总数
     */
    long countByPlan(@Param("planId") String planId,
                     @Param("subjectType") String subjectType,
                     @Param("subjectId") String subjectId,
                     @Param("cycleKey") String cycleKey);

    /**
     * 按方案ID 级联删除所有目标值（删除方案时清值用）.
     *
     * @param planId 方案ID
     * @return 受影响行数
     */
    int deleteByPlanId(@Param("planId") String planId);

    /**
     * V1.3 R1.1 新增: 基于 {@link com.bank.branch.platform.performance.service.scope.PerfScopeHelper}
     * 的数据范围注入分页查询. planId 可空（null 时不加 plan_id 条件, 允许跨方案按 scope 查询）.
     *
     * @param planId        方案ID (可空)
     * @param subjectType   对象类型 (可空)
     * @param subjectId     对象ID (可空)
     * @param cycleKey      周期键 (可空)
     * @param offset        偏移量
     * @param limit         每页大小
     * @param scopeFragment PerfScopeHelper.Fragment#getSql()
     * @param scopeParams   PerfScopeHelper.Fragment#getParams()
     * @return 目标值列表
     */
    List<PerfTargetValue> selectByConditionWithScope(@Param("planId") String planId,
                                                     @Param("subjectType") String subjectType,
                                                     @Param("subjectId") String subjectId,
                                                     @Param("cycleKey") String cycleKey,
                                                     @Param("offset") int offset,
                                                     @Param("limit") int limit,
                                                     @Param("scopeFragment") String scopeFragment,
                                                     @Param("scopeParams") Map<String, Object> scopeParams);

    /**
     * V1.3 R1.1 新增: 基于 PerfScopeHelper 的数据范围注入计数.
     *
     * @param planId        方案ID (可空)
     * @param subjectType   对象类型 (可空)
     * @param subjectId     对象ID (可空)
     * @param cycleKey      周期键 (可空)
     * @param scopeFragment PerfScopeHelper.Fragment#getSql()
     * @param scopeParams   PerfScopeHelper.Fragment#getParams()
     * @return 总数
     */
    long countByConditionWithScope(@Param("planId") String planId,
                                   @Param("subjectType") String subjectType,
                                   @Param("subjectId") String subjectId,
                                   @Param("cycleKey") String cycleKey,
                                   @Param("scopeFragment") String scopeFragment,
                                   @Param("scopeParams") Map<String, Object> scopeParams);

    /**
     * 2026-06-15：查询某目标方案下所有目标值去重后的对象（subject_type + subject_id）。
     *
     * <p>用于目标值管理页查询区的「对象」下拉。仅投影 subject_type / subject_id 两列，
     * 标签（工号+姓名 / 部门编号+机构名称）由 Service 层调用 UserApi / OrgApi 解析。
     *
     * @param planId 目标方案ID（必填）
     * @return 去重的对象列表（仅填充 subjectType / subjectId）
     */
    List<PerfTargetValue> selectDistinctSubjectsByPlan(@Param("planId") String planId);
}
