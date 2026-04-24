package com.bank.branch.platform.performance.controller.dto;

import com.fasterxml.jackson.annotation.JsonAlias;
import com.fasterxml.jackson.annotation.JsonIgnore;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/**
 * 指标试运行响应 DTO（03 §A.5）.
 *
 * <p>V1.3 R3.2 字段对齐 03 §A.5 规格：
 * <ul>
 *   <li>{@code samples} → {@code sampleRows}（命名对齐文档）</li>
 *   <li>新增 5 字段：{@code taskId}/{@code status}/{@code startedAt}/{@code endedAt}/{@code errorMsg}</li>
 * </ul>
 *
 * <p>破坏性变更兼容策略（过渡期保留，V1.4 删除）：
 * <ul>
 *   <li>{@link JsonAlias @JsonAlias({"samples"})}：旧前端在请求体使用旧字段名 {@code samples}
 *       时仍可反序列化到 {@link #sampleRows}</li>
 *   <li>{@link #getSamples()} 标 {@code @Deprecated} + {@code @JsonIgnore}：
 *       旧调用方代码 {@code dto.getSamples()} 仍可编译通过（返回 sampleRows 同一引用）；
 *       不参与 JSON 序列化以避免字段双写</li>
 * </ul>
 *
 * <p>字段语义：
 * <ul>
 *   <li>{@code taskId} —— 本次试运行任务 ID（V1.3 新增；Service 生成，可能为 UUID）</li>
 *   <li>{@code metricCode} —— 回显的指标编码</li>
 *   <li>{@code sampleSize} —— 实际返回样本条数</li>
 *   <li>{@code totalRows} —— SQL 返回的总行数（EXPR 为 1）</li>
 *   <li>{@code status} —— RUNNING/SUCCESS/FAILED（V1.3 新增；Service 同步执行时固定 SUCCESS）</li>
 *   <li>{@code startedAt} —— 执行开始时间（V1.3 新增）</li>
 *   <li>{@code endedAt} —— 执行结束时间（V1.3 新增）</li>
 *   <li>{@code errorMsg} —— 失败时的错误消息（V1.3 新增；正常场景为 null）</li>
 *   <li>{@code exprResult} —— EXPR 单值结果（SQL 场景为 null）</li>
 *   <li>{@code executionMillis} —— 执行耗时（毫秒）</li>
 *   <li>{@code sampleRows} —— 样本行（SQL 场景填充，EXPR 为空列表）</li>
 * </ul>
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MetricTrialRespDTO {

    /** 本次试运行任务 ID（V1.3 新增）. */
    private String taskId;

    /** 回显的指标编码. */
    private String metricCode;

    /** 实际返回样本条数. */
    private Integer sampleSize;

    /** 总行数（EXPR 为 1）. */
    private Integer totalRows;

    /** 执行状态：RUNNING/SUCCESS/FAILED（V1.3 新增，Service 同步执行固定 SUCCESS）. */
    private String status;

    /** 执行开始时间（V1.3 新增）. */
    private LocalDateTime startedAt;

    /** 执行结束时间（V1.3 新增）. */
    private LocalDateTime endedAt;

    /** 错误信息（V1.3 新增，失败时填入）. */
    private String errorMsg;

    /** EXPR 单值结果（SQL 场景为 null）. */
    private BigDecimal exprResult;

    /** 执行耗时（毫秒）. */
    private Long executionMillis;

    /**
     * 样本行（SQL 场景填充，EXPR 为空列表）.
     * <p>V1.3 R3.2：字段重命名为 sampleRows 对齐 03 §A.5；{@link JsonAlias} 让旧前端请求
     * 继续以 {@code samples} 字段反序列化到此字段，过渡一个版本。
     */
    @JsonAlias({"samples"})
    private List<Map<String, Object>> sampleRows;

    /**
     * 旧字段名访问器（V1.3 兼容过渡）.
     *
     * @return {@link #sampleRows} 同一引用
     * @deprecated V1.3 改名为 sampleRows，V1.4 删除。请调用 {@link #getSampleRows()}。
     */
    @Deprecated
    @JsonIgnore
    public List<Map<String, Object>> getSamples() {
        return sampleRows;
    }
}
