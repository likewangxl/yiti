package com.bank.branch.platform.redengine.api.dto;

/**
 * 定时任务周期类型。
 * <p>周期窗口按北京时间自然日计算，起止日期均包含在窗口内。</p>
 */
public enum ReTaskCycleType {

    /** 每周初，周一开始。 */
    WEEK_START,
    /** 每周末，周日结束。 */
    WEEK_END,
    /** 每月初，当月第一天开始。 */
    MONTH_START,
    /** 每月末，当月最后一天结束。 */
    MONTH_END,
    /** 每季度初，季度第一天开始。 */
    QUARTER_START,
    /** 每季度末，季度最后一天结束。 */
    QUARTER_END,
    /** 非周期任务，仅供临时任务表达无周期。 */
    NONE;

    /**
     * 判断周期是否从周期起点向后计算。
     *
     * @return 周期起点类型返回 true，否则返回 false
     */
    public boolean isStartCycle() {
        return this == WEEK_START || this == MONTH_START || this == QUARTER_START;
    }

    /**
     * 判断周期是否从周期终点向前计算。
     *
     * @return 周期终点类型返回 true，否则返回 false
     */
    public boolean isEndCycle() {
        return this == WEEK_END || this == MONTH_END || this == QUARTER_END;
    }
}
