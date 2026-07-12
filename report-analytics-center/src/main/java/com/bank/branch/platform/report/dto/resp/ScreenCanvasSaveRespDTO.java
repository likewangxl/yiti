package com.bank.branch.platform.report.dto.resp;

import lombok.Data;

/** 画布保存响应:新版本号 + 回吐 resolved blockId 后的 draftJson(前端采纳新 id). */
@Data
public class ScreenCanvasSaveRespDTO {
    private Integer canvasVersion;
    private String canvasDraftJson;
}
