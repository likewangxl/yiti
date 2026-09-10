package com.bank.branch.platform.report.dto.req;

/** 数据源可用性状态白名单。 */
public enum SourceAvailabilityStatus {
    AVAILABLE,
    NO_SOURCE,
    NO_ROWS,
    NO_VALUES,
    PARTIAL,
    HISTORICAL
}
