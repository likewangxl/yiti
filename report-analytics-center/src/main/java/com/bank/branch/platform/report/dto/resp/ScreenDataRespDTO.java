package com.bank.branch.platform.report.dto.resp;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;
import java.util.Map;

/**
 * 大屏统一取数响应（所有可视化组件同构消费的二维结构）.
 *
 * <p>2026-07-17 起扩展可空 columnsMeta（spec §3.2 字段元数据）：数据源 config_json 配置了
 * fieldMeta 时按列名匹配填充，供组件做别名/单位/小数位及金额量级展示计算；旧组件忽略即可，
 * columns/rows 契约不变。运行时质量 quality 同样可空，旧组件忽略即可。
 */
@Data
@NoArgsConstructor
public class ScreenDataRespDTO {

    /** 列名（宽表指标列为指标名别名） */
    private List<String> columns;

    /** 数据行（值为 Number/String，日期已转字符串） */
    private List<List<Object>> rows;

    /**
     * 列元数据（可空，向后兼容新增字段）：仅包含 fieldMeta 配置过的列，顺序跟随 columns；
     * 数据源未配置 fieldMeta 时保持 null，旧调用方零影响。amountScale 用于前端按元基准计算展示值。
     */
    private List<ColumnMeta> columnsMeta;

    /**
     * 运行时数据质量（可空，向后兼容）。仅由需要完整批次判定的数据源填充，静态
     * sourceAvailability 不参与此对象的生成或覆盖。
     */
    private Quality quality;

    /** 既有二参构造器（保留，旧调用方零影响） */
    public ScreenDataRespDTO(List<String> columns, List<List<Object>> rows) {
        this.columns = columns;
        this.rows = rows;
    }

    public ScreenDataRespDTO(List<String> columns, List<List<Object>> rows, List<ColumnMeta> columnsMeta) {
        this.columns = columns;
        this.rows = rows;
        this.columnsMeta = columnsMeta;
    }

    /** 运行时完整批次和时效元数据；字段保持简单可序列化类型，便于旧客户端忽略。 */
    @Data
    @NoArgsConstructor
    public static class Quality {

        /** 批次身份：ORG:version:dataDate；无完整批次时为空。 */
        private String batchId;

        /** 实际选中的业务数据日期，ISO 格式；无完整批次时为空。 */
        private String dataDate;

        /** 当前 SYS_CONTROL 生效版本；无完整批次时可能为空。 */
        private String version;

        /** 批次来源分类，例如 TEST 或 PROD；取自不可变批次快照。 */
        private String dataClassification;

        /**
         * 主体值口径，仅由 TEST 不可变批次快照明确声明时输出；旧响应保持为空。
         */
        private String subjectValueMode;

        /** COMPLETE、STALE 或 NO_COMPLETE_BATCH。 */
        private String status;

        /** 服务端授权机构数。 */
        private Integer expectedSubjects;

        /** 选中日期内具备全部必需槽位的授权机构数。 */
        private Integer receivedSubjects;

        /** 配置的最大允许年龄（天）；未配置时为空。 */
        private Integer maxAgeDays;

        /** 选中日期距本次查询日期的天数；无完整批次时为空。 */
        private Integer ageDays;

        /** 面向调用方的质量说明。 */
        private String message;

        /** 批次计算完成时间，ISO-8601 且带 +08:00 偏移；通用 SQL 路径为空。 */
        private String calculatedAt;

        /** 各来源截至日期与采集时间，日期/时间均为 ISO-8601 字符串。 */
        private Map<String, String> sourceAsOf;

        /** 来源是否混期。 */
        private Boolean mixedPeriod;

        /** 机构覆盖缺口或其他质量原因；无缺口为空数组。 */
        private List<String> missing;

        /** 机构覆盖缺口明细；无缺口为空数组。 */
        private List<String> missingSubjects;

        /** 选中批次是否满足批次完整性。 */
        private Boolean selectedComplete;

        /** 选中日期之后发现的不完整候选日期。 */
        private List<String> newerIncomplete;

        /** 与性能批次 DTO 对齐的原始期望值数量；可空。 */
        private Integer expected;

        /** 与性能批次 DTO 对齐的原始收到值数量；可空。 */
        private Integer received;

        /** 历史金融行按日期的机构/指标覆盖，缺日不补零。 */
        private List<HistoryCoverage> historyCoverage;

        /** 既有调用方使用的精简构造器，扩展字段保持空值。 */
        public Quality(String batchId, String dataDate, String version, String status,
                       Integer expectedSubjects, Integer receivedSubjects, Integer maxAgeDays,
                       Integer ageDays, String message) {
            this.batchId = batchId;
            this.dataDate = dataDate;
            this.version = version;
            this.status = status;
            this.expectedSubjects = expectedSubjects;
            this.receivedSubjects = receivedSubjects;
            this.maxAgeDays = maxAgeDays;
            this.ageDays = ageDays;
            this.message = message;
        }
    }

    /** 历史日期覆盖明细，字段与 performance 批次契约保持 typed 对齐。 */
    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class HistoryCoverage {

        private String dataDate;
        private Integer expected;
        private Integer received;
        private Integer expectedSubjects;
        private Integer receivedSubjects;
        private Boolean complete;
        private List<String> missingSubjects;
        private List<String> missing;
    }

    /** 单列元数据（对应 config_json.fieldMeta 数组元素） */
    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ColumnMeta {

        /** 结果列名（与 columns 中的列名精确匹配） */
        private String col;

        /** 展示别名（可空，组件展示列名替换用） */
        private String alias;

        /** 角色：DIM（维度）| METRIC（度量） */
        private String role;

        /** 单位（可空，如"万元"） */
        private String unit;

        /** 小数位（可空，组件默认格式化用） */
        private Integer decimals;

        /** 金额量级预设（可空）：YUAN/TEN_THOUSAND_YUAN/HUNDRED_MILLION_YUAN；组件据此按元基准换算展示。 */
        private String amountScale;

        /** 兼容未带金额量级的既有构造调用。 */
        public ColumnMeta(String col, String alias, String role, String unit, Integer decimals) {
            this(col, alias, role, unit, decimals, null);
        }
    }
}
