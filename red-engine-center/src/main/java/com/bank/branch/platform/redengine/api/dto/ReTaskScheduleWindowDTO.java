package com.bank.branch.platform.redengine.api.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.time.LocalDate;

/**
 * 任务当前周期的自然日窗口。
 * <p>时间粒度刻意使用 {@link LocalDate}，避免把北京时间自然日边界与服务器时区耦合。</p>
 */
@Data
@Schema(description = "任务周期窗口")
public class ReTaskScheduleWindowDTO {

    /** 周期类型。 */
    @Schema(description = "周期类型")
    private ReTaskCycleType cycleType;

    /** 周期键，例如 2024-02 或 2024-Q2。 */
    @Schema(description = "周期键")
    private String periodKey;

    /** 窗口开始日期，包含当天。 */
    @Schema(description = "窗口开始日期")
    private LocalDate startDate;

    /** 窗口结束日期，包含当天。 */
    @Schema(description = "窗口结束日期")
    private LocalDate endDate;

    /**
     * 判断自然日是否落在当前窗口内。
     *
     * @param date 待判断日期
     * @return 在窗口内返回 true
     */
    public boolean contains(LocalDate date) {
        return date != null
                && startDate != null
                && endDate != null
                && !date.isBefore(startDate)
                && !date.isAfter(endDate);
    }
}
