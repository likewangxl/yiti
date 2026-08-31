package com.bank.branch.platform.redengine.api.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.math.BigDecimal;

/** 连续两个自然季度命中的红黄牌支部。 */
@Data
@Schema(description = "红色引擎红黄牌预警条目")
public class ReQuarterWarningDTO {

    private Long branchId;
    private String branchName;
    /** red 或 yellow。 */
    private String level;
    private BigDecimal score;
    private BigDecimal previousScore;
    private int rank;
    private int previousRank;
    private String quarter;
    private String previousQuarter;

    /** 预警卡片使用的当前得分别名。 */
    public BigDecimal getFinalScore() {
        return score;
    }

    /** 预警卡片使用的当前得分别名。 */
    public void setFinalScore(BigDecimal finalScore) {
        this.score = finalScore;
    }

    /** 兼容既有预警卡片使用的组织 ID 字段命名。 */
    public Long getOrgId() {
        return branchId;
    }

    /** 兼容既有预警卡片使用的组织 ID 字段命名。 */
    public void setOrgId(Long orgId) {
        this.branchId = orgId;
    }

    /** 兼容既有预警卡片使用的期间字段命名。 */
    public String getPeriod() {
        return quarter;
    }

    /** 兼容既有预警卡片使用的期间字段命名。 */
    public void setPeriod(String period) {
        this.quarter = period;
    }
}
