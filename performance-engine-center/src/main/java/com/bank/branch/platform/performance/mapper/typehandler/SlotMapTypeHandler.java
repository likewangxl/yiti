package com.bank.branch.platform.performance.mapper.typehandler;

import org.apache.ibatis.type.BaseTypeHandler;
import org.apache.ibatis.type.JdbcType;
import org.apache.ibatis.type.MappedTypes;

import java.math.BigDecimal;
import java.sql.CallableStatement;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.SQLException;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 宽表 200 slot 统一 TypeHandler（V1.1 Task P1.4）.
 *
 * <p>职责：将 {@link ResultSet} 中的 {@code val_1 .. val_200} 列一次性 pack 成
 * {@code Map<Integer, BigDecimal>}（稀疏存储：null 的槽不入 Map）。
 *
 * <h3>使用场景</h3>
 * <p>宽表（{@code emp/org/cust_index_result}）的主路径仍走 Mapper 的单 slot 读写：
 * <ul>
 *   <li>{@code insertSlotValue}：单列 UPSERT，性能友好</li>
 *   <li>{@code selectSlotValue}：按 slot 精确读取</li>
 * </ul>
 * <p>本 TypeHandler 仅服务于"一次读取整行 200 slot"的边缘场景，例如：
 * <ul>
 *   <li>调试 / 运维接口的"导出一行快照"</li>
 *   <li>V1.2 可能新增的 debug 查询</li>
 * </ul>
 *
 * <h3>写路径说明</h3>
 * <p>因为宽表写入走 {@code insertSlotValue} 单列 UPSERT 路径，本 handler 的
 * {@link #setNonNullParameter} 刻意为 noop：不对 {@link PreparedStatement} 产生
 * 任何 setXxx 副作用，避免误入写路径导致覆盖整行 200 列。
 *
 * <h3>注册方式</h3>
 * <p>本 handler 不在全局 {@code mybatis.type-handlers-package} 自动注册（避免影响
 * 已有 {@code Map} 字段的处理），调用方在对应 {@code resultMap} 中显式指定：
 * <pre>
 * &lt;result property="slots" typeHandler="com.bank.branch.platform.performance.
 *         mapper.typehandler.SlotMapTypeHandler" column="*" /&gt;
 * </pre>
 * 或在 Mapper 方法上使用 {@code @Results(@Result(...typeHandler=SlotMapTypeHandler.class))}.
 */
@MappedTypes(Map.class)
public class SlotMapTypeHandler extends BaseTypeHandler<Map<Integer, BigDecimal>> {

    /** val_ 列名前缀，与 DDL 保持一致（val_1 .. val_200）. */
    private static final String SLOT_COLUMN_PREFIX = "val_";

    /** 最大槽位（含），与宽表 DDL 列数一致. */
    static final int MAX_SLOT = 200;

    /**
     * 写路径 noop：宽表写入通过 Mapper 的 insertSlotValue 单列 UPSERT 完成，
     * 本 handler 故意不参与 PreparedStatement 参数设置，避免误覆盖整行。
     */
    @Override
    public void setNonNullParameter(PreparedStatement ps, int i,
                                    Map<Integer, BigDecimal> parameter,
                                    JdbcType jdbcType) throws SQLException {
        // noop by design
    }

    /**
     * 按列名读取：假定列名形如 "slots" 但实际 ResultSet 含 val_1 .. val_200 列。
     * <p>columnName 参数在此实现中仅用作来源标识，不作为实际查询列；实际 pack 依据
     * 固定的 val_1 .. val_200 列名遍历 ResultSet。
     */
    @Override
    public Map<Integer, BigDecimal> getNullableResult(ResultSet rs, String columnName) throws SQLException {
        return packAllSlots(rs);
    }

    /**
     * 按列索引读取：通过 {@link ResultSetMetaData} 发现所有形如 val_N 的列，
     * 仅聚合这些列的值（忽略非 val_ 列如 id/emp_id）。
     */
    @Override
    public Map<Integer, BigDecimal> getNullableResult(ResultSet rs, int columnIndex) throws SQLException {
        ResultSetMetaData md = rs.getMetaData();
        int cols = md.getColumnCount();
        Map<Integer, BigDecimal> slots = new LinkedHashMap<>();
        for (int idx = 1; idx <= cols; idx++) {
            String label = md.getColumnLabel(idx);
            Integer slot = parseSlot(label);
            if (slot == null) {
                continue;
            }
            BigDecimal v = rs.getBigDecimal(label);
            if (v != null) {
                slots.put(slot, v);
            }
        }
        return slots;
    }

    /**
     * CallableStatement 入口（存储过程场景）：
     * <p>V1.1 宽表读取无存储过程场景，本入口保留 Mybatis 契约所需的实现，
     * 但直接返回空 Map（调用方应改走 ResultSet 路径）。
     */
    @Override
    public Map<Integer, BigDecimal> getNullableResult(CallableStatement cs, int columnIndex) throws SQLException {
        return new LinkedHashMap<>();
    }

    /**
     * 遍历 val_1 .. val_200 固定列名，pack 成稀疏 Map.
     *
     * <p>注意：ResultSet 未包含某些 val_N 列时，部分驱动会抛 SQLException，
     * 此处统一吞掉异常并跳过（保证 handler 对"局部投影"场景也能工作），
     * 仅当驱动返回非 null 值才入 Map。
     */
    private Map<Integer, BigDecimal> packAllSlots(ResultSet rs) throws SQLException {
        Map<Integer, BigDecimal> slots = new LinkedHashMap<>();
        for (int slot = 1; slot <= MAX_SLOT; slot++) {
            String col = SLOT_COLUMN_PREFIX + slot;
            BigDecimal v;
            try {
                v = rs.getBigDecimal(col);
            } catch (SQLException e) {
                // 局部投影（结果集未包含此 val_N 列）时跳过；其他 SQLException 仍抛出
                if (isColumnNotFound(e)) {
                    continue;
                }
                throw e;
            }
            if (v != null) {
                slots.put(slot, v);
            }
        }
        return slots;
    }

    /** MySQL 驱动列未找到错误识别. */
    private boolean isColumnNotFound(SQLException e) {
        String msg = e.getMessage();
        return msg != null && (msg.contains("Column") && msg.contains("not found"));
    }

    /**
     * 解析 val_N 列名中的槽位号，N ∈ [1, 200]；其他列名返回 null.
     */
    private Integer parseSlot(String columnLabel) {
        if (columnLabel == null || !columnLabel.startsWith(SLOT_COLUMN_PREFIX)) {
            return null;
        }
        String tail = columnLabel.substring(SLOT_COLUMN_PREFIX.length());
        try {
            int slot = Integer.parseInt(tail);
            if (slot < 1 || slot > MAX_SLOT) {
                return null;
            }
            return slot;
        } catch (NumberFormatException e) {
            return null;
        }
    }
}
