package com.bank.branch.platform.redengine.api.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

/**
 * 驾驶舱-总览统计 DTO。
 * <p>对应源 redengine {@code BizCockpitServiceImpl.getOverview} 返回的
 * {@code Map<String,Object>}（totalSubmits/approvedCount/pendingCount/rejectedCount 四键），
 * 拍平为强类型响应体（项目红线：禁止 Map/Object 通用返回）。四个计数均基于 RE_SUBMIT
 * （{@code @TableLogic} 自动排除软删记录），status 语义：0草稿 1已提交(pending) 2已通过(approved)
 * 3已驳回(rejected)。</p>
 */
@Data
@Schema(description = "驾驶舱-总览统计")
public class ReCockpitOverviewDTO {

    /** 上报总数（未软删的 RE_SUBMIT 全量） */
    @Schema(description = "上报总数")
    private long totalSubmits;

    /** 已通过数量(status=2) */
    @Schema(description = "已通过数量")
    private long approvedCount;

    /** 待审数量(status=1) */
    @Schema(description = "待审数量")
    private long pendingCount;

    /** 已驳回数量(status=3) */
    @Schema(description = "已驳回数量")
    private long rejectedCount;
}
