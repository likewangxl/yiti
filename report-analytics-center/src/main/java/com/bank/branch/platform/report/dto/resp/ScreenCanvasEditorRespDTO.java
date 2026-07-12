package com.bank.branch.platform.report.dto.resp;

import com.bank.branch.platform.report.dto.req.ScreenBlockDTO;
import lombok.Data;
import java.util.List;

/** 画布编辑器加载响应:styleJson + draftJson + blocks 行 + 版本/发布态. */
@Data
public class ScreenCanvasEditorRespDTO {
    private Long screenId;
    private String screenCode;
    private String screenName;
    private String viewLevel;
    /** 画布全局样式 JSON 字符串(前端 JSON.parse) */
    private String canvasStyleJson;
    /** 编辑态组件树 JSON 字符串 */
    private String canvasDraftJson;
    /** 区块行(取数配置,前端按 blockId 关联 ChartWidget) */
    private List<ScreenBlockDTO> blocks;
    private Integer canvasVersion;
    private Integer publishStatus;
}
