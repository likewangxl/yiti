package com.bank.branch.platform.report.dto.req.presentation;

/** 受控页面内动作；不接受任意 URL 或脚本。 */
public enum ScreenInteractionAction {
    NONE,
    OPEN_BUSINESS_LINE,
    OPEN_CITY,
    OPEN_INSTITUTION,
    OPEN_METRIC_DETAIL
}
