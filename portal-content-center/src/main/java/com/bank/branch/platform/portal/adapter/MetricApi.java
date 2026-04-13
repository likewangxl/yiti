package com.bank.branch.platform.portal.adapter;

import com.bank.branch.platform.portal.adapter.dto.MetricCardDTO;
import com.bank.branch.platform.portal.adapter.dto.MetricTrendPoint;

import java.util.List;

/**
 * 绩效指标查询接口 —— 供工作台聚合指标卡片与趋势图。
 *
 * <p>当前为纯接口定义，<strong>无 Spring Bean 实现</strong>；
 * 消费方应使用 {@code @Autowired(required = false)} 注入，
 * 在 Bean 为 null 时降级处理。</p>
 *
 * @deprecated V1 临时占位接口，待 performance-engine-center 模块创建后迁移
 */
@Deprecated
public interface MetricApi {

    /**
     * 查询员工所有指标卡片
     *
     * @param empId 员工工号
     * @return 指标卡片列表
     */
    List<MetricCardDTO> getUserMetricCards(String empId);

    /**
     * 查询员工单个指标卡片
     *
     * @param empId      员工工号
     * @param metricCode 指标编码
     * @return 指标卡片
     */
    MetricCardDTO getMetricCard(String empId, String metricCode);

    /**
     * 查询员工指标趋势数据
     *
     * @param empId      员工工号
     * @param metricCode 指标编码
     * @param period     统计周期（DAY / WEEK / MONTH / QUARTER / YEAR）
     * @return 趋势数据点列表
     */
    List<MetricTrendPoint> getMetricTrend(String empId, String metricCode, String period);
}
