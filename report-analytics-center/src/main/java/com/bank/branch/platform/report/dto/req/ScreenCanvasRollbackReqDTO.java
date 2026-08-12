package com.bank.branch.platform.report.dto.req;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

/** 回滚请求:把某条归档发布回滚到 PUBLISHED_JSON. */
@Data
public class ScreenCanvasRollbackReqDTO {
    @NotNull
    private Long screenId;
    @NotNull
    private Long publishLogId;
    /** 回滚同样必须基于读取时的画布版本做 CAS。 */
    @NotNull
    private Integer expectedVersion;

    /** 高危回滚必须填写原因，不能由服务端默认补值。 */
    @NotBlank
    private String reason;
}
