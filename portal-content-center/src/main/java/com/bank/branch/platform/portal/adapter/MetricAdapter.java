package com.bank.branch.platform.portal.adapter;

import com.bank.branch.platform.portal.api.dto.PortalMetricCard;
import com.bank.branch.platform.portal.convert.MetricCardProjection;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

/**
 * MetricApi 适配器，封装跨模块降级逻辑
 *
 * <p>V1 阶段 MetricApi bean 不存在，自动返回空列表；
 * V2+ 阶段 performance-engine-center 提供实现后，自动注入并正常调用。</p>
 *
 * <p>异常场景同样降级为空列表，保证工作台不因绩效服务故障而整体不可用。</p>
 */
@Slf4j
@Service
public class MetricAdapter {

    @Autowired(required = false)
    @Setter // for testing
    private MetricApi metricApi;

    /**
     * 获取员工绩效指标卡片列表
     *
     * @param empId 员工编号
     * @return 指标卡片列表，MetricApi 不可用时返回空列表
     */
    public List<PortalMetricCard> fetch(String empId) {
        if (metricApi == null) {
            log.debug("MetricApi bean is null, returning empty list (V1 fallback)");
            return Collections.emptyList();
        }
        try {
            return metricApi.getUserMetricCards(empId).stream()
                    .map(MetricCardProjection::toPortal)
                    .collect(Collectors.toList());
        } catch (Exception ex) {
            log.warn("MetricApi.getUserMetricCards failed for empId={}", empId, ex);
            return Collections.emptyList();
        }
    }
}
