package com.bank.branch.platform.performance.mapper;

import com.bank.branch.platform.performance.entity.PerfKpiItem;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * KPI 方案项表 Mapper.
 *
 * <p>父子表 CRUD 的子表操作，属于 perf_kpi_scheme 的附属表。
 * 批量插入 {@link #insertBatch(List)} 用于一次创建方案时一次性写入所有项，
 * 单条插入 {@link #insert(PerfKpiItem)} 用于增量追加（需与父方案同事务）。
 */
@Mapper
public interface PerfKpiItemMapper {

    /**
     * 批量新增方案项（创建方案时一次写入）.
     *
     * @param items 方案项列表
     * @return 受影响行数
     */
    int insertBatch(@Param("list") List<PerfKpiItem> items);

    /**
     * 单条新增方案项（追加场景）.
     *
     * @param item 方案项
     * @return 受影响行数
     */
    int insert(PerfKpiItem item);

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
     * 按主键删除.
     *
     * @param id 主键
     * @return 受影响行数
     */
    int deleteById(@Param("id") String id);

    /**
     * 按方案ID删除所有项（删除方案时级联清理）.
     *
     * @param schemeId 方案ID
     * @return 受影响行数
     */
    int deleteBySchemeId(@Param("schemeId") String schemeId);

    /**
     * 按主键查询.
     *
     * @param id 主键
     * @return 方案项，不存在返回 null
     */
    PerfKpiItem selectById(@Param("id") String id);

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
}
