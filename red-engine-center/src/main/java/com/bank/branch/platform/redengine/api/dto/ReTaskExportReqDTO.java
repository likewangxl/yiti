package com.bank.branch.platform.redengine.api.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.util.List;

/** 任务异步导出请求；四大维度任务必须由服务层校验明细项编码。 */
@Data
@Schema(description = "任务异步导出请求")
public class ReTaskExportReqDTO {

    /** 选中的 RE_ITEM_CODE 明细项；临时任务允许为空数组。 */
    @Schema(description = "导出明细项编码")
    private List<String> itemCodes;
}
