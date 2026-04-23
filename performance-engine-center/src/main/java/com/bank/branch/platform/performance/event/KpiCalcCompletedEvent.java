package com.bank.branch.platform.performance.event;

import lombok.Getter;

import java.time.LocalDate;

/**
 * KPI 计算完成事件（V1.2 Q1.1）.
 *
 * <p>触发场景：{@code KpiCalcService.calcKpi(schemeCode, cycleType, cycleDate)} 完成后。
 *
 * <p>消费方：report-analytics 刷新快照、portal 推送通知。
 *
 * @since V1.2 Q1.1
 */
@Getter
public class KpiCalcCompletedEvent extends PerfDomainEvent {

    /** KPI 方案编码. */
    private final String schemeCode;

    /** 周期类型 (DAY / WEEK / MONTH / QUARTER / YEAR). */
    private final String cycleType;

    /** 周期日期. */
    private final LocalDate cycleDate;

    /** 截止业务日期. */
    private final LocalDate asOfDate;

    /** 使用的版本号. */
    private final String version;

    /** 落地员工数 (kpi_result 行数). */
    private final int empCount;

    public KpiCalcCompletedEvent(String traceId,
                                 String schemeCode,
                                 String cycleType,
                                 LocalDate cycleDate,
                                 LocalDate asOfDate,
                                 String version,
                                 int empCount) {
        super(traceId);
        this.schemeCode = schemeCode;
        this.cycleType = cycleType;
        this.cycleDate = cycleDate;
        this.asOfDate = asOfDate;
        this.version = version;
        this.empCount = empCount;
    }

    @Override
    public String topic() {
        return "performance.kpi-calc.completed.v1";
    }
}
