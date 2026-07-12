package com.bank.branch.platform.report.dto.req;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import lombok.Data;
import java.util.List;

/** 画布保存请求(styleJson + 组件树 + 乐观锁版本,单事务). */
@Data
public class ScreenCanvasSaveReqDTO {
    @NotNull
    private Long screenId;
    @Valid
    private CanvasStyleDTO canvasStyle;
    @Valid
    private List<CanvasComponentDTO> components;
    /** 客户端持有的期望版本(乐观锁);冲突返回 RPT-43012 */
    @NotNull
    private Integer expectedVersion;
}
