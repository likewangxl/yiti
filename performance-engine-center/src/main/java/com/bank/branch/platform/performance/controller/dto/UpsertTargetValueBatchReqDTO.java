package com.bank.branch.platform.performance.controller.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.util.List;

/**
 * 目标值批量 upsert 请求 DTO.
 *
 * <p>双重防线:
 * <ul>
 *   <li>Controller 层: {@link @NotEmpty} 拦截空列表, {@link @Size}(max=500) 拦截超大批次</li>
 *   <li>Service 层 (TargetValueService.upsertBatch): 再次校验 size &gt; 500 抛 PERF-40910</li>
 * </ul>
 * 即使 Controller 层 DTO 校验被绕过 (如直接调 Service), Service 层仍然守住 500 上限。
 */
@Data
@Schema(description = "目标值批量 upsert 请求")
public class UpsertTargetValueBatchReqDTO {

    /** 目标值列表 (不能为空, 单批上限 500). */
    @Schema(description = "目标值列表 (不能为空, 单批上限 500)", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotEmpty(message = "values 列表不能为空")
    @Size(max = 500, message = "批量目标值单次最多 500 条")
    @Valid
    private List<UpsertTargetValueReqDTO> values;
}
