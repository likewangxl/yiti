/**
 * report-analytics-center 模块对外 Api 占位包.
 *
 * <p>V1.0 红线：本模块为只读支撑域，<strong>不暴露任何 *Api / *QueryApi 接口</strong>。
 * 跨模块只读聚合通过 4 个上游 *Api（auth/governance/customer/performance）+ 24 个 REST 接口完成。
 *
 * <p>V2+ 如需暴露 Api（例如订阅推送），在此包下新增 *Api 接口。
 *
 * @since 2026-04-25 (V1.0 P-1 占位补建)
 */
package com.bank.branch.platform.report.api;
