package com.bank.branch.platform.report.support;

import com.bank.branch.platform.report.enums.RptErrorCode;
import com.bank.branch.platform.report.exception.RptException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * PERF_METRIC_DEF 槽位查询 DAO（走只读数据源，宽表数据源保存时做指标校验与槽位翻译）.
 *
 * <p>不走 performance 模块 Api：大屏数据访问按 D1 决策统一走只读源 + 白名单直查。
 */
@Slf4j
@Component
public class ScreenMetricSlotDao {

    private final DataSource readOnlyDataSource;

    public ScreenMetricSlotDao(@Qualifier("rptReadOnlyDataSource") DataSource readOnlyDataSource) {
        this.readOnlyDataSource = readOnlyDataSource;
    }

    /** 指标定义快照 */
    public record MetricSlot(String metricCode, String metricName, Integer valSlot, String baseDim) {
    }

    /**
     * 指标目录行。只含绑定所需的定义字段，不包含 sql_text / expr_text 等计算逻辑。
     */
    public record MetricDefinition(String metricCode, String metricName, String baseDim, Integer valSlot,
                                   String unit, String metricCategory, String summaryRule, Integer metricLevel,
                                   String calcFreq, String metricDesc, String status) {
    }

    /** 按编码批量查有效指标定义（deleted=0） */
    public List<MetricSlot> selectByCodes(List<String> codes) {
        if (codes == null || codes.isEmpty()) {
            return List.of();
        }
        String in = String.join(",", Collections.nCopies(codes.size(), "?"));
        String sql = "SELECT metric_code, metric_name, val_slot, base_dim FROM PERF_METRIC_DEF"
                + " WHERE deleted = 0 AND metric_code IN (" + in + ")";
        try (Connection conn = readOnlyDataSource.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setQueryTimeout(5);
            for (int i = 0; i < codes.size(); i++) {
                stmt.setString(i + 1, codes.get(i));
            }
            try (ResultSet rs = stmt.executeQuery()) {
                List<MetricSlot> out = new ArrayList<>();
                while (rs.next()) {
                    out.add(new MetricSlot(rs.getString("metric_code"), rs.getString("metric_name"),
                            (Integer) rs.getObject("val_slot"), rs.getString("base_dim")));
                }
                return out;
            }
        } catch (SQLException e) {
            log.warn("[ScreenMetricSlotDao] 指标定义查询失败 cause={}", e.getMessage());
            throw new RptException(RptErrorCode.SCREEN_DATA_QUERY_FAILED, e);
        }
    }

    /**
     * 列出未删除的 ACTIVE 指标定义，供大屏绑定目录使用。
     * 关键字和维度过滤在内存中完成，避免把用户输入拼进 SQL。
     */
    public List<MetricDefinition> listActiveDefinitions() {
        String sql = "SELECT metric_code, metric_name, base_dim, val_slot, unit, metric_category, "
                + "summary_rule, metric_level, calc_freq, metric_desc, status "
                + "FROM PERF_METRIC_DEF WHERE deleted = 0 AND status = 'ACTIVE' "
                + "ORDER BY base_dim, metric_code";
        try (Connection conn = readOnlyDataSource.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setQueryTimeout(5);
            stmt.setMaxRows(5000);
            try (ResultSet rs = stmt.executeQuery()) {
                List<MetricDefinition> out = new ArrayList<>();
                while (rs.next()) {
                    Object slot = rs.getObject("val_slot");
                    Object level = rs.getObject("metric_level");
                    out.add(new MetricDefinition(
                            rs.getString("metric_code"),
                            rs.getString("metric_name"),
                            rs.getString("base_dim"),
                            slot == null ? null : ((Number) slot).intValue(),
                            rs.getString("unit"),
                            rs.getString("metric_category"),
                            rs.getString("summary_rule"),
                            level == null ? null : ((Number) level).intValue(),
                            rs.getString("calc_freq"),
                            rs.getString("metric_desc"),
                            rs.getString("status")));
                }
                return out;
            }
        } catch (SQLException e) {
            log.warn("[ScreenMetricSlotDao] 指标目录查询失败 cause={}", e.getMessage());
            throw new RptException(RptErrorCode.SCREEN_DATA_QUERY_FAILED, e);
        }
    }
}
