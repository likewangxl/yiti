package com.bank.branch.platform.report.dto.req;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

/**
 * 放弃草稿请求。
 *
 * <p>放弃草稿不是回滚发布归档，不能复用带 publishLogId 的 DTO；必须以当前画布版本做 CAS，
 * 并填写高危配置审计原因。</p>
 */
@Data
public class ScreenCanvasDiscardReqDTO {

    @NotNull
    private Long screenId;

    @NotNull
    private Integer expectedVersion;

    @NotBlank
    private String reason;
}
