package com.bank.branch.platform.performance.mapper;

import com.bank.branch.platform.performance.entity.PerfMetricDef;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 指标定义表 Mapper.
 */
@Mapper
public interface PerfMetricDefMapper {

    /**
     * 新增指标定义.
     *
     * @param def 指标定义
     * @return 受影响行数
     */
    int insert(PerfMetricDef def);

    /**
     * 按主键选择性更新.
     *
     * @param def 指标定义
     * @return 受影响行数
     */
    int updateByIdSelective(PerfMetricDef def);

    /**
     * 按主键更新状态.
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
     * 按主键查询.
     *
     * @param id 主键
     * @return 指标定义，不存在时返回 null
     */
    PerfMetricDef selectById(@Param("id") String id);

    /**
     * 按指标编码查询.
     *
     * @param metricCode 指标编码
     * @return 指标定义，不存在时返回 null
     */
    PerfMetricDef selectByMetricCode(@Param("metricCode") String metricCode);

    /**
     * 批量按指标编码查询.
     *
     * @param codes 指标编码列表
     * @return 指标定义列表
     */
    List<PerfMetricDef> selectByMetricCodes(@Param("codes") List<String> codes);

    /**
     * 查询某维度已占用的全部槽位。
     *
     * @param baseDim 基础维度
     * @return 槽位集合
     */
    Set<Integer> selectOccupiedSlots(@Param("baseDim") String baseDim);

    /**
     * 分页条件查询.
     *
     * @param baseDim     基础维度
     * @param metricLevel 指标层级
     * @param status      状态
     * @param keyword     关键字（编码或名称）
     * @param offset      偏移量
     * @param limit       每页大小
     * @return 指标定义列表
     */
    List<PerfMetricDef> selectByCondition(@Param("baseDim") String baseDim,
                                          @Param("metricLevel") Integer metricLevel,
                                          @Param("status") String status,
                                          @Param("keyword") String keyword,
                                          @Param("offset") int offset,
                                          @Param("limit") int limit);

    /**
     * 条件计数.
     *
     * @param baseDim     基础维度
     * @param metricLevel 指标层级
     * @param status      状态
     * @param keyword     关键字
     * @return 总数
     */
    long countByCondition(@Param("baseDim") String baseDim,
                          @Param("metricLevel") Integer metricLevel,
                          @Param("status") String status,
                          @Param("keyword") String keyword);

    /**
     * Q7.3 新增: 基于 PerfScopeHelper 的数据范围注入分页查询.
     *
     * @param baseDim       基础维度
     * @param metricLevel   指标层级
     * @param status        状态
     * @param keyword       关键字
     * @param offset        偏移量
     * @param limit         每页大小
     * @param scopeFragment PerfScopeHelper.Fragment#getSql()
     * @param scopeParams   PerfScopeHelper.Fragment#getParams()
     * @return 指标定义列表
     */
    List<PerfMetricDef> selectByConditionWithScope(@Param("baseDim") String baseDim,
                                                    @Param("metricLevel") Integer metricLevel,
                                                    @Param("status") String status,
                                                    @Param("keyword") String keyword,
                                                    @Param("offset") int offset,
                                                    @Param("limit") int limit,
                                                    @Param("scopeFragment") String scopeFragment,
                                                    @Param("scopeParams") Map<String, Object> scopeParams);

    /**
     * Q7.3 新增: 基于 PerfScopeHelper 的数据范围注入计数.
     *
     * @param baseDim       基础维度
     * @param metricLevel   指标层级
     * @param status        状态
     * @param keyword       关键字
     * @param scopeFragment PerfScopeHelper.Fragment#getSql()
     * @param scopeParams   PerfScopeHelper.Fragment#getParams()
     * @return 总数
     */
    long countByConditionWithScope(@Param("baseDim") String baseDim,
                                   @Param("metricLevel") Integer metricLevel,
                                   @Param("status") String status,
                                   @Param("keyword") String keyword,
                                   @Param("scopeFragment") String scopeFragment,
                                   @Param("scopeParams") Map<String, Object> scopeParams);

    /**
     * 释放槽位，仅允许 DISABLED 状态的指标执行.
     *
     * @param id        主键
     * @param updatedBy 更新人
     * @return 受影响行数
     */
    int releaseSlotById(@Param("id") String id,
                        @Param("updatedBy") String updatedBy);

    /**
     * 软删除：设置 deleted=1（Task B5）.
     *
     * @param id 主键
     * @return 受影响行数
     */
    int softDelete(@Param("id") String id);
}
