package com.bank.branch.platform.report.dto.resp;

import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 大屏数据源响应.
 */
@Data
public class ScreenDatasourceRespDTO {

    private Long id;

    private String dsCode;

    private String dsName;

    /** TIMESERIES / SINGLE（前端组件联动过滤的依据） */
    private String dsType;

    private String sourceKind;

    private String bizLine;

    private String configJson;

    private String timeParamJson;

    private String status;

    private String remark;

    private LocalDateTime createdTime;

    /** 当前草稿 block 对此数据源的完整屏编码引用清单。 */
    private List<String> draftReferenceScreenCodes;

    /** 已发布快照对对此数据源的完整屏编码引用清单。 */
    private List<String> publishedReferenceScreenCodes;
}
