package com.bank.branch.platform.redengine.api.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.math.BigDecimal;

/** 首页支部季度排名条目。 */
@Data
@Schema(description = "红色引擎首页支部季度排名")
public class ReHomeBranchRankingDTO {

    @Schema(description = "党支部 ID")
    private Long branchId;

    @Schema(description = "党支部名称")
    private String branchName;

    @Schema(description = "当前自然季度得分")
    private BigDecimal score;

    /** 密集排名；同分支部使用相同名次，下一名按不同分值递增。 */
    @Schema(description = "密集排名")
    private int rank;

    @Schema(description = "自然季度，例如 2026-Q3")
    private String quarter;

    /** 与历史驾驶舱 DTO 兼容的得分别名。 */
    public BigDecimal getFinalScore() {
        return score;
    }

    /** 与历史驾驶舱 DTO 兼容的得分别名。 */
    public void setFinalScore(BigDecimal finalScore) {
        this.score = finalScore;
    }
}
