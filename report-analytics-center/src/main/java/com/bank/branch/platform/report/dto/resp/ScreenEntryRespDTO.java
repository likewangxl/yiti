package com.bank.branch.platform.report.dto.resp;

import lombok.Data;

/** 当前用户可进入的大屏运行时目录项。 */
@Data
public class ScreenEntryRespDTO {

    private String screenCode;

    private String screenName;

    private String viewLevel;

    private String bizLine;
}
