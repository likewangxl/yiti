package com.bank.branch.platform.report.dto.req;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

/** 回滚请求:把某条归档发布回滚到 PUBLISHED_JSON. */
@Data
public class ScreenCanvasRollbackReqDTO {
    @NotNull
    private Long screenId;
    @NotNull
    private Long publishLogId;
}
