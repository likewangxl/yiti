package com.bank.branch.platform.performance.mapper;

import com.bank.branch.platform.performance.entity.PerfTargetPlan;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;
import java.util.Map;

/**
 * 目标方案表 Mapper.
 *
 * <p>负责 perf_target_plan 父表的 CRUD。目标值由 {@link PerfTargetValueMapper} 管理，
 * 父子同事务由 Service 层编排（见 Task 3.2 TargetPlanService）。
 * <p>insert / selectById / deleteById 由 MyBatis-Plus BaseMapper 提供.
 */
@Mapper
public interface PerfTargetPlanMapper extends BaseMapper<PerfTargetPlan> {

    /**
     * 按主键选择性更新（非空字段才更新，updated_time 固定写入 NOW()）.
     *
     * <p>不接受 patch created_by/created_time; 若传值将被忽略（XML 刻意不提供对应 {@code <if>} 分支）.
     *
     * @param plan 目标方案
     * @return 受影响行数
     */
    int updateByIdSelective(PerfTargetPlan plan);

    /**
     * 按主键更新状态（用于发布 / 禁用流转）.
     *
     * @param id        主键
     * @param status    新状态
     * @param updatedBy 更新人
     * @return 受影响行数
     */
    int updateStatusById(@Param("id") String id,
                         @Param("status") String status,
                         @Param("updatedBy") String updatedBy);

    /**
     * 按方案编码查询（UK 支撑）.
     *
     * @param planCode 方案编码
     * @return 方案，不存在返回 null
     */
    PerfTargetPlan selectByPlanCode(@Param("planCode") String planCode);

    /**
     * 分页条件查询.
     *
     * @param kpiSchemeId 关联 KPI 方案ID
     * @param status      状态
     * @param keyword     关键字（编码或名称模糊匹配）
     * @param offset      偏移量
     * @param limit       每页大小
     * @return 方案列表
     */
    List<PerfTargetPlan> selectByCondition(@Param("kpiSchemeId") String kpiSchemeId,
                                           @Param("status") String status,
                                           @Param("keyword") String keyword,
                                           @Param("offset") int offset,
                                           @Param("limit") int limit);

    /**
     * 条件计数（与 selectByCondition 保持一致）.
     *
     * @param kpiSchemeId 关联 KPI 方案ID
     * @param status      状态
     * @param keyword     关键字
     * @return 总数
     */
    long countByCondition(@Param("kpiSchemeId") String kpiSchemeId,
                          @Param("status") String status,
                          @Param("keyword") String keyword);

    /**
     * V1.3 R1.2 新增: 基于 {@link com.bank.branch.platform.performance.service.scope.PerfScopeHelper}
     * 的数据范围注入分页查询.
     *
     * @param kpiSchemeId   关联 KPI 方案ID (可空)
     * @param status        状态 (可空)
     * @param keyword       关键字 (可空)
     * @param offset        偏移量
     * @param limit         每页大小
     * @param scopeFragment PerfScopeHelper.Fragment#getSql()
     * @param scopeParams   PerfScopeHelper.Fragment#getParams()
     * @return 方案列表
     */
    List<PerfTargetPlan> selectByConditionWithScope(@Param("kpiSchemeId") String kpiSchemeId,
                                                    @Param("status") String status,
                                                    @Param("keyword") String keyword,
                                                    @Param("offset") int offset,
                                                    @Param("limit") int limit,
                                                    @Param("scopeFragment") String scopeFragment,
                                                    @Param("scopeParams") Map<String, Object> scopeParams);

    /**
     * V1.3 R1.2 新增: 基于 PerfScopeHelper 的数据范围注入计数.
     *
     * @param kpiSchemeId   关联 KPI 方案ID (可空)
     * @param status        状态 (可空)
     * @param keyword       关键字 (可空)
     * @param scopeFragment PerfScopeHelper.Fragment#getSql()
     * @param scopeParams   PerfScopeHelper.Fragment#getParams()
     * @return 总数
     */
    long countByConditionWithScope(@Param("kpiSchemeId") String kpiSchemeId,
                                   @Param("status") String status,
                                   @Param("keyword") String keyword,
                                   @Param("scopeFragment") String scopeFragment,
                                   @Param("scopeParams") Map<String, Object> scopeParams);
}
