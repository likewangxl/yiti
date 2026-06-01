package com.bank.branch.platform.performance.mapper;

import com.bank.branch.platform.performance.entity.PerfKpiItem;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * KPI 方案项表 Mapper.
 *
 * <p>父子表 CRUD 的子表操作，属于 perf_kpi_scheme 的附属表。
 * 批量插入 {@link #insertBatch(List)} 用于一次创建方案时一次性写入所有项，
 * 单条新增走 BaseMapper.insert(PerfKpiItem) 增量追加（需与父方案同事务）。
 * <p>insert / selectById / deleteById 由 MyBatis-Plus BaseMapper 提供.
 */
@Mapper
public interface PerfKpiItemMapper extends BaseMapper<PerfKpiItem> {

    /**
     * 批量新增方案项（创建方案时一次写入）.
     *
     * @param items 方案项列表
     * @return 受影响行数
     */
    int insertBatch(@Param("list") List<PerfKpiItem> items);

    /**
     * 按主键选择性更新.
     *
     * <p>perf_kpi_item 表无 updated_time 列作为 {@code <set>} 兜底锚点,
     * 调用方必须保证至少有一个非 id 字段非空, 否则生成非法 SQL (由 DB 抛
     * {@link org.springframework.jdbc.BadSqlGrammarException}, fail-fast).
     *
     * @param item 方案项
     * @return 受影响行数
     */
    int updateByIdSelective(PerfKpiItem item);

    /**
     * 按方案ID删除所有项（删除方案时级联清理）.
     *
     * @param schemeId 方案ID
     * @return 受影响行数
     */
    int deleteBySchemeId(@Param("schemeId") String schemeId);

    /**
     * 按方案ID查询所有项.
     *
     * @param schemeId 方案ID
     * @return 方案项列表
     */
    List<PerfKpiItem> selectBySchemeId(@Param("schemeId") String schemeId);

    /**
     * 按 (schemeId, metricCode) 查询（UK 支撑，用于"重复项"校验）.
     *
     * @param schemeId   方案ID
     * @param metricCode 指标编码
     * @return 方案项，不存在返回 null
     */
    PerfKpiItem selectBySchemeAndMetric(@Param("schemeId") String schemeId,
                                        @Param("metricCode") String metricCode);

    /**
     * V1.7：反查依赖某指标的 ACTIVE KPI 方案 ID 列表.
     *
     * <p>用于 KpiCascadeListener 在指标计算完成后，找出所有引用该指标且状态为 ACTIVE 的 KPI 方案，
     * 触发相应的 KPI 方案重算.
     *
     * @param metricCode 指标编码
     * @return ACTIVE KPI 方案 ID 列表（可能为空）
     */
    List<String> selectActiveSchemeIdsByMetric(@Param("metricCode") String metricCode);

    /**
     * 反查依赖某指标的 ACTIVE KPI 方案 (scheme_code, scheme_name)，
     * 用于禁用指标时给前端列出受影响方案的友好提示。
     */
    List<java.util.Map<String, Object>> selectActiveSchemeRefsByMetric(@Param("metricCode") String metricCode);
}
