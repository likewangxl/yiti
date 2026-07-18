package com.bank.branch.platform.report.dto.resp;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * 大屏统一取数响应（所有可视化组件同构消费的二维结构）.
 *
 * <p>2026-07-17 起扩展可空 columnsMeta（spec §3.2 字段元数据）：数据源 config_json 配置了
 * fieldMeta 时按列名匹配填充，供组件做别名/单位/小数位默认格式化；旧组件忽略即可，
 * columns/rows 契约不变。
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
     * 数据源未配置 fieldMeta 时保持 null，旧调用方零影响。
     */
    private List<ColumnMeta> columnsMeta;

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
    }
}
