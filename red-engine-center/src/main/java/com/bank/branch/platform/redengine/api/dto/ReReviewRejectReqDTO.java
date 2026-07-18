package com.bank.branch.platform.redengine.api.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

/**
 * 两级审核-审核驳回请求 DTO。
 * <p>对应源 redengine {@code ReviewController.rejectSubmit} 内联的 {@code Map<String,Object> body}
 * (取 {@code reason}|{@code feedback} 键)，拍平为强类型请求体。</p>
 */
@Data
@Schema(description = "两级审核-审核驳回请求")
public class ReReviewRejectReqDTO {

    /** 驳回原因/审核意见 */
    @Schema(description = "驳回原因/审核意见")
    private String feedback;
}
