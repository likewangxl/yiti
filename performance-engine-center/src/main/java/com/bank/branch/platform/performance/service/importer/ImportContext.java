package com.bank.branch.platform.performance.service.importer;

import java.time.LocalDate;

/**
 * 数据导入上下文（V1.12 微调引入）.
 *
 * <p>承载跨 strategy 共享的"整文件级"参数，避免接口签名爆炸。当前仅持
 * {@link #dataDate}，未来新增整文件级参数（如 cycleType / version 覆盖等）追加 record 字段即可。
 *
 * <p>语义：
 * <ul>
 *   <li>{@code dataDate} 仅 {@code METRIC_RESULT} 策略使用，作为整文件统一数据日期；
 *       其他 4 个策略（TARGET / ALLOC / BASE_DATA / METRIC_DEF）忽略此字段</li>
 *   <li>METRIC_RESULT 缺 {@code dataDate} 由 Controller / Service 层
 *       {@link com.bank.branch.platform.performance.exception.PerfException} fail-fast，
 *       不进入 strategy 行级最大努力分支</li>
 * </ul>
 *
 * <p>历史：V1.12 初版用 Excel Sheet 名携带 dataDate（每 Sheet 一个日期），
 * 2026-05-19 改为前端日期选择器经 HTTP 参数注入，Sheet 名变为纯展示用。
 */
public record ImportContext(LocalDate dataDate) {

    /** 空上下文（无任何参数），供不需要 context 的 4 个策略复用. */
    public static final ImportContext EMPTY = new ImportContext(null);
}
