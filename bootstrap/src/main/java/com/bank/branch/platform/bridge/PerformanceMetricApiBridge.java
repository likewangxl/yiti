package com.bank.branch.platform.bridge;

import com.bank.branch.platform.portal.adapter.MetricApi;
import com.bank.branch.platform.portal.adapter.dto.MetricCardDTO;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

/**
 * Portal MetricApi 防腐层 → Performance MetricApi 桥接实现（DIP）
 *
 * <p>portal-content-center 通用域设计原则：不依赖核心域 performance-engine-center，
 * 因此 portal 仅定义防腐层抽象接口 {@link com.bank.branch.platform.portal.adapter.MetricApi}
 * 与视图层 DTO {@link com.bank.branch.platform.portal.adapter.dto.MetricCardDTO}。</p>
 *
 * <p>bootstrap 模块同时依赖 portal 与 performance，由本桥接类完成：</p>
 * <ul>
 *   <li>注入 performance.api.MetricApi（V1.1 P2.6 已交付）</li>
 *   <li>调用 getUserMetricCards 取得 performance MetricCardDTO 列表</li>
 *   <li>逐项投影为 portal MetricCardDTO（trend 字段由 performance.mom 推导：&gt;0 UP / =0 FLAT / &lt;0 DOWN / null null）</li>
 *   <li>注册为 Spring @Service，让 portal MetricAdapter 通过 portal.adapter.MetricApi 类型注入到本实现</li>
 * </ul>
 *
 * <p>消除 V1 阶段 portal MetricAdapter 永远走降级返回空列表的 P1 关键债。</p>
 */
@Service
public class PerformanceMetricApiBridge implements MetricApi {

    private final com.bank.branch.platform.performance.api.MetricApi performanceMetricApi;

    public PerformanceMetricApiBridge(
            com.bank.branch.platform.performance.api.MetricApi performanceMetricApi) {
        this.performanceMetricApi = performanceMetricApi;
    }

    @Override
    public List<MetricCardDTO> getUserMetricCards(String empId) {
        List<com.bank.branch.platform.performance.api.dto.MetricCardDTO> source =
                performanceMetricApi.getUserMetricCards(empId);
        if (source == null) {
            return Collections.emptyList();
        }
        return source.stream()
                .filter(Objects::nonNull)
                .map(this::toPortalDto)
                .collect(Collectors.toList());
    }

    /**
     * performance MetricCardDTO → portal MetricCardDTO 投影
     *
     * <p>字段映射：
     * metricCode / metricName / currentValue / targetValue / achievementRate / unit 同名直传，
     * trend 由 performance.mom 推导，
     * changeRate 同步填 mom（保留 portal 视图层前向兼容），
     * dataTime 由 performance.dataDate.atStartOfDay() 而来。</p>
     */
    private MetricCardDTO toPortalDto(
            com.bank.branch.platform.performance.api.dto.MetricCardDTO src) {
        MetricCardDTO dto = new MetricCardDTO();
        dto.setMetricCode(src.getMetricCode());
        dto.setMetricName(src.getMetricName());
        dto.setCurrentValue(src.getCurrentValue());
        dto.setTargetValue(src.getTargetValue());
        dto.setAchievementRate(src.getAchievementRate());
        dto.setUnit(src.getUnit());
        dto.setTrend(deriveTrend(src.getMom()));
        dto.setChangeRate(src.getMom());
        dto.setDataTime(toDataTime(src.getDataDate()));
        return dto;
    }

    /**
     * mom → trend 字符串推导。
     * mom &gt; 0 → "UP" / mom == 0 → "FLAT" / mom &lt; 0 → "DOWN" / mom == null → null
     */
    private String deriveTrend(BigDecimal mom) {
        if (mom == null) {
            return null;
        }
        int sign = mom.signum();
        if (sign > 0) {
            return "UP";
        }
        if (sign < 0) {
            return "DOWN";
        }
        return "FLAT";
    }

    /**
     * LocalDate → LocalDateTime（取 atStartOfDay 等价 00:00:00）。
     */
    private LocalDateTime toDataTime(LocalDate date) {
        return date == null ? null : date.atStartOfDay();
    }
}
