package com.bank.branch.platform.report.dto.req;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

/** 发布请求(带乐观锁版本,防发布陈旧草稿). */
@Data
public class ScreenCanvasPublishReqDTO {
    @NotNull
    private Long screenId;
    /** 期望版本:与当前草稿版本不一致则拒绝(避免发布看到的不是最新草稿) */
    @NotNull
    private Integer expectedVersion;

    /** 高危发布必须由操作者明确说明原因，服务层也会重复校验。 */
    @NotBlank
    private String reason;
}
