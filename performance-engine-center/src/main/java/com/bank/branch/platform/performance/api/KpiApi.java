package com.bank.branch.platform.performance.api;

import com.bank.branch.platform.performance.api.dto.KpiResultDTO;
import com.bank.branch.platform.performance.api.dto.KpiSchemeDTO;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

/**
 * KPI 查询对外 API.
 *
 * <p>V1.x 实现状态:
 * <ul>
 *   <li>✅ V1.0 实现: getKpiScheme / getKpiSchemeById (方案配置查询)</li>
 *   <li>✅ V1.1 P2.6 已交付: getCurrentKpiTotal / getCurrentKpiResult / getKpiHistory
 *       （详见 KpiApiImpl L36-38 / L79 / L85 / L91，原 V1.0 UOE 占位已清零）</li>
 * </ul>
 *
 * <p>消费方: portal-content-center, report-analytics-center.
 *
 * <p>v1.2: schemeId 从 Long 改为 String (对齐生产 DDL varchar(32)).
 */
public interface KpiApi {

    /**
     * 获取员工当前周期的 KPI 总分.
     * <p>V1.0 抛 UnsupportedOperationException; V1.1 实现.
     */
    BigDecimal getCurrentKpiTotal(String empId, String cycleType);

    /**
     * 获取员工当前周期的 KPI 结果 (含明细).
     * <p>V1.0 抛 UnsupportedOperationException; V1.1 实现.
     */
    Optional<KpiResultDTO> getCurrentKpiResult(String empId, String cycleType);

    /**
     * 获取员工 KPI 历史结果.
     * <p>V1.0 抛 UnsupportedOperationException; V1.1 实现.
     */
    List<KpiResultDTO> getKpiHistory(String empId, String cycleType, LocalDate from, LocalDate to);

    /**
     * 获取 KPI 方案定义.
     *
     * <p><strong>本方法不走 Redis 缓存</strong>：write 方法 (update/publish/disable) 的 evict
     * 只认 id 作 key, 无法定位 code 缓存键; 高频访问建议改用 {@link #getKpiSchemeById(String)}
     * 并在调用方自行维护 code→id 映射.
     *
     * @param schemeCode 方案编码
     * @return 方案详情 (含 items), 不存在返回 Optional.empty()
     */
    Optional<KpiSchemeDTO> getKpiScheme(String schemeCode);

    /**
     * 按 ID 获取 KPI 方案.
     * <p>v1.2: schemeId 为 String.
     */
    Optional<KpiSchemeDTO> getKpiSchemeById(String schemeId);
}
