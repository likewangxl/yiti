package com.bank.branch.platform.portal.adapter;

import com.bank.branch.platform.portal.adapter.dto.MetricCardDTO;

import java.util.List;

/**
 * 门户工作台指标查询防腐层接口（DIP 模式）。
 *
 * <p>由 bootstrap 模块的 {@code PerformanceMetricApiBridge} 桥接
 * {@code com.bank.branch.platform.performance.api.MetricApi} 实现。
 * portal 通用域设计原则不依赖 performance 核心域，因此采用 portal 定义抽象、
 * bootstrap 提供具体桥接的依赖反转。</p>
 *
 * <p>消费方应通过 {@code @Autowired(required = false)} 注入，
 * 当 bootstrap 桥接 Bean 缺失时（极端故障 / 模块裁剪场景）降级返回空。</p>
 */
public interface MetricApi {

    /**
     * 查询员工所有指标卡片
     *
     * @param empId 员工工号
     * @return 指标卡片列表
     */
    List<MetricCardDTO> getUserMetricCards(String empId);
}
