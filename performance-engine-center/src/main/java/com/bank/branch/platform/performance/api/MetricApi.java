package com.bank.branch.platform.performance.api;

import com.bank.branch.platform.performance.api.dto.MetricCardDTO;
import com.bank.branch.platform.performance.api.dto.MetricDefDTO;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * 指标查询对外 API (跨模块指标相关查询的唯一入口).
 *
 * <p>V1.x 实现状态:
 * <ul>
 *   <li>✅ V1.0 实现: getMetricDef / getMetricDefs / listMetrics (配置查询)</li>
 *   <li>✅ V1.1 P2.6 已交付: getUserMetricCards / getEmpMetricValues / getOrgMetricValues / getCustMetricValues
 *       （详见 MetricApiImpl L42 / L279 / L285 / L291，原 V1.0 UOE 占位已清零）</li>
 * </ul>
 *
 * <p>消费方:
 * <ul>
 *   <li>portal-content-center: 工作台指标卡片</li>
 *   <li>report-analytics-center: 报表指标值查询</li>
 * </ul>
 */
public interface MetricApi {

    /**
     * 获取员工工作台的指标卡片聚合数据.
     * <p>V1.0 抛 UnsupportedOperationException; V1.1 实现.
     *
     * @param empId 员工工号
     * @return 指标卡片列表, 按 sortNo 排序
     */
    List<MetricCardDTO> getUserMetricCards(String empId);

    /**
     * 查询单个指标定义.
     *
     * @param metricCode 指标编码
     * @return 指标定义, 不存在时返回 Optional.empty()
     */
    Optional<MetricDefDTO> getMetricDef(String metricCode);

    /**
     * 批量查询指标定义.
     *
     * @param metricCodes 指标编码列表, 最多 100 个
     * @return 指标定义列表, 未找到的 code 不会出现在返回中
     */
    List<MetricDefDTO> getMetricDefs(List<String> metricCodes);

    /**
     * 按维度和层级查询指标定义.
     * <p>仅返回 status=PUBLISHED 的指标.
     *
     * @param baseDim     基础维度 EMP/ORG/CUST, 不可为 null
     * @param metricLevel 指标层级 1/2/3, null 表示不过滤
     * @return 指标定义列表
     */
    List<MetricDefDTO> listMetrics(String baseDim, Integer metricLevel);

    /**
     * 查询员工指标实际值.
     * <p>V1.0 抛 UnsupportedOperationException; V1.1 实现.
     *
     * @param empId        员工工号
     * @param dataDate     数据日期, null 表示读取 sys_control 的 latest_data_date
     * @param metricCodes  指标编码列表, 最多 100 个
     * @return Map of 指标编码 → 指标值
     */
    Map<String, BigDecimal> getEmpMetricValues(String empId, LocalDate dataDate, List<String> metricCodes);

    /**
     * 查询机构指标实际值.
     * <p>V1.0 抛 UnsupportedOperationException; V1.1 实现.
     */
    Map<String, BigDecimal> getOrgMetricValues(String orgCode, LocalDate dataDate, List<String> metricCodes);

    /**
     * 取某维度 SYS_CONTROL 的最新有效数据日期（current_version 对应的 latest_data_date）.
     * <p>供报表/仪表盘"读取最新版本汇总数据"使用，未配置时返回 null.
     *
     * @param scopeDim 维度 EMP / ORG / CUST
     * @return 最新数据日期，无配置返回 null
     */
    LocalDate getLatestDataDate(String scopeDim);

    /**
     * 查询客户指标实际值.
     * <p>V1.0 抛 UnsupportedOperationException; V1.1 实现.
     */
    Map<String, BigDecimal> getCustMetricValues(String custId, LocalDate dataDate, List<String> metricCodes);
}
