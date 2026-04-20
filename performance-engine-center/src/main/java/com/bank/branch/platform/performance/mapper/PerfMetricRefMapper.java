package com.bank.branch.platform.performance.mapper;

import com.bank.branch.platform.performance.entity.PerfMetricRef;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * 指标引用关系表 Mapper.
 */
@Mapper
public interface PerfMetricRefMapper {

    /**
     * 批量新增引用关系.
     *
     * @param list 引用关系列表
     * @return 受影响行数
     */
    int insertBatch(@Param("list") List<PerfMetricRef> list);

    /**
     * 删除某个上层指标全部引用关系.
     *
     * @param metricCode 上层指标编码
     * @return 受影响行数
     */
    int deleteByMetricCode(@Param("metricCode") String metricCode);

    /**
     * 查询某个上层指标的引用列表.
     *
     * @param metricCode 上层指标编码
     * @return 引用关系列表
     */
    List<PerfMetricRef> selectByMetricCode(@Param("metricCode") String metricCode);

    /**
     * 反查被哪些指标引用.
     *
     * @param refMetricCode 下层指标编码
     * @return 引用关系列表
     */
    List<PerfMetricRef> selectByRefMetricCode(@Param("refMetricCode") String refMetricCode);

    /**
     * 一次性加载全部引用关系，供图算法构图.
     *
     * @return 全量引用关系列表
     */
    List<PerfMetricRef> selectAll();
}
