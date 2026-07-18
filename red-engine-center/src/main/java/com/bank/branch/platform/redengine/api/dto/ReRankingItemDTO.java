package com.bank.branch.platform.redengine.api.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.math.BigDecimal;

/**
 * 驾驶舱-组织排名条目 DTO。
 * <p>对应源 redengine {@code BizCockpitServiceImpl.getRanking} 返回的
 * {@code List<Map<String,Object>>}（rank/orgId/finalScore/period 四键），拍平为强类型 DTO。
 * 排名按 RE_SCORE.final_score 降序，rank 为排序后的序号(从1开始)，非数据库字段。</p>
 */
@Data
@Schema(description = "驾驶舱-组织排名条目")
public class ReRankingItemDTO {

    /** 排名(从1开始，按 finalScore 降序) */
    @Schema(description = "排名")
    private int rank;

    /** 党组织ID */
    @Schema(description = "党组织ID")
    private Long orgId;

    /** 最终得分 */
    @Schema(description = "最终得分")
    private BigDecimal finalScore;

    /** 考核期间(YYYY-MM) */
    @Schema(description = "考核期间")
    private String period;
}
