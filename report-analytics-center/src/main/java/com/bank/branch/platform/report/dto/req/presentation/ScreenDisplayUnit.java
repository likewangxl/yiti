package com.bank.branch.platform.report.dto.req.presentation;

/** 原始/展示单位；AUTO 仅用于不强制缩放的展示配置。 */
public enum ScreenDisplayUnit {
    AUTO(null),
    YUAN("amount"),
    TEN_THOUSAND("amount"),
    HUNDRED_MILLION("amount"),
    COUNT("count"),
    TEN_THOUSAND_COUNT("count"),
    PERCENT("ratio"),
    RATIO("ratio");

    private final String kind;

    ScreenDisplayUnit(String kind) {
        this.kind = kind;
    }

    public String kind() {
        return kind;
    }
}
