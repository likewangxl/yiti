package com.bank.branch.platform.redengine.api.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.math.BigDecimal;

/**
 * 两级审核-审核通过请求 DTO。
 * <p>对应源 redengine {@code ReviewController.approveSubmit} 内联的 {@code Map<String,Object> body}
 * (取 {@code score}/{@code remark}|{@code feedback} 两键)，拍平为强类型请求体。{@code score} 可为空或
 * &le;0，此时 {@link com.bank.branch.platform.redengine.service.ReReviewService#approve} 跳过评分落库，
 * 仅推进 RE_SUBMIT 状态；不做 {@code @NotNull} 校验以保留"仅改状态不评分"的合法用法。</p>
 */
@Data
@Schema(description = "两级审核-审核通过请求")
public class ReReviewApproveReqDTO {

    /** 本次评分（可为空或&le;0，表示本次审核不落评分明细） */
    @Schema(description = "本次评分(可为空表示不评分)")
    private BigDecimal score;

    /** 审核意见 */
    @Schema(description = "审核意见")
    private String feedback;
}
