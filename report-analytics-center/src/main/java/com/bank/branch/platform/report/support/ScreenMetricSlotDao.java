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
}
