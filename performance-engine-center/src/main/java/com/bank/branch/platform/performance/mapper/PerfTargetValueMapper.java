package com.bank.branch.platform.performance.mapper;

import com.bank.branch.platform.performance.entity.PerfTargetValue;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * 目标值/基础值表 Mapper.
 *
 * <p>核心方法 {@link #upsertBatch(List)} 通过 MySQL {@code ON DUPLICATE KEY UPDATE}
 * 语义实现"以 UK (plan_id, subject_type, subject_id, cycle_key, metric_code) 为粒度"的
 * 批量落库；新 UK 插入、旧 UK 更新 target_value/base_value。
 */
@Mapper
public interface PerfTargetValueMapper {

    /**
     * 批量 upsert（基于 uk_plan_subject_cycle_metric 的 ON DUPLICATE KEY UPDATE）.
     *
     * <p>MySQL INSERT ... ON DUPLICATE KEY UPDATE 返回的"受影响行数"语义：新插入记 1，
     * 已存在且发生字段更新记 2；因此入参 N 条时，返回值介于 [N, 2N] 之间。
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
     * 按主键查询.
     *
     * @param id 主键
     * @return 目标值，不存在返回 null
     */
    PerfTargetValue selectById(@Param("id") String id);

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
}
