package com.bank.branch.platform.performance.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * performance-engine-center V1.1 执行引擎配置.
 *
 * <p>属性前缀：{@code perf.engine}，支持在 application.yml 中以如下形式覆盖默认值：
 * <pre>
 * perf:
 *   engine:
 *     groovy-enabled: true
 *     sql-timeout-seconds: 30
 *     cascade-max-depth: 5
 *     import-batch-size: 500
 * </pre>
 *
 * <p>本类在 V1.1 规划阶段作为执行引擎运行参数的统一入口，V1.1/V1.2 的：
 * <ul>
 *     <li>指标执行（SQL + Groovy）</li>
 *     <li>KPI 级联刷新</li>
 *     <li>数据导入</li>
 * </ul>
 * 将从本配置读取运行时参数，避免散落在各 Service 的魔数。
 */
@Component
@ConfigurationProperties(prefix = "perf.engine")
@Data
public class PerfEngineProperties {

    /** 是否允许 Groovy 表达式执行（V1.1 指标计算支持）；默认启用。 */
    private boolean groovyEnabled = true;

    /** 指标 SQL 执行超时（秒）；默认 30 秒，避免指标脚本因慢查询拖垮计算任务。 */
    private int sqlTimeoutSeconds = 30;

    /** 级联刷新深度上限；默认 5 层，防止指标依赖图递归爆炸。 */
    private int cascadeMaxDepth = 5;

    /** 数据导入批大小；默认 500 行/批，平衡内存占用与 JDBC 往返次数。 */
    private int importBatchSize = 500;
}
