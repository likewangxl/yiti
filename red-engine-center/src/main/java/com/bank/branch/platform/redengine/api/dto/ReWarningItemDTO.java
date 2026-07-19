package com.bank.branch.platform.redengine.api.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.math.BigDecimal;

/**
 * 驾驶舱-红黄牌预警条目 DTO。
 * <p>对应源 redengine {@code BizCockpitServiceImpl.getRedWarning}/{@code getYellowWarning} 返回的
 * {@code List<Map<String,Object>>}（orgId/finalScore/period/level 四键），拍平为强类型 DTO，
 * 红黄两级预警共用同一结构，仅 {@link #level} 取值不同("red"/"yellow")。</p>
 * <p><b>阈值口径以 Java 代码为准</b>（源系统注释写 75，代码实际 80，两者不一致时按代码）：
 * final_score&lt;60 为红牌；60&le;final_score&lt;80 为黄牌。</p>
 */
@Data
@Schema(description = "驾驶舱-红黄牌预警条目")
public class ReWarningItemDTO {

    /** 党组织ID */
    @Schema(description = "党组织ID")
    private Long orgId;

    /** 最终得分 */
    @Schema(description = "最终得分")
    private BigDecimal finalScore;

    /** 考核期间(YYYY-MM) */
    @Schema(description = "考核期间")
    private String period;

    /** 预警级别："red" 或 "yellow" */
    @Schema(description = "预警级别(red/yellow)")
    private String level;
}
