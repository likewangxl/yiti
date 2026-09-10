package com.bank.branch.platform.report.dto.resp;

import lombok.Data;

/** 当前用户可进入的大屏运行时目录项。 */
@Data
public class ScreenEntryRespDTO {

    private String screenCode;

    private String screenName;

    private String viewLevel;

    private String bizLine;

    /** 代码化展示模板；目录只注册受支持的固定模板。 */
    private String template;

    /** 展示数据模式；代码化目录当前只允许本地演示数据。 */
    private String dataMode;
}
