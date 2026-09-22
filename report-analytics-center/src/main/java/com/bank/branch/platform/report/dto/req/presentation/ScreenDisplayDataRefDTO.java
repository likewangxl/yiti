package com.bank.branch.platform.report.dto.req.presentation;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import lombok.Data;

/** 指向当前屏已校验 block 的数据引用及只读业务身份快照。 */
@Data
public class ScreenDisplayDataRefDTO {
    @NotNull
    @Positive
    private Long blockId;
    @NotNull
    private ScreenDataRefRole role;
    @Size(max = 100)
    private String metricCode;
    @Size(max = 200)
    private String metricName;
    @NotNull
    private ScreenDisplayUnit unit;
    private ScreenSourceDimension dimension;
    @Size(max = 500)
    private String formula;
}
