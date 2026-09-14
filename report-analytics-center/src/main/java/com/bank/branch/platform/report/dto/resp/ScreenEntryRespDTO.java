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

    /** 展示数据模式；固定注册项按 TEST/DEMO/LIVE 分别声明，LIVE 表示接口取数而非生产环境承诺。 */
    private String dataMode;
}
