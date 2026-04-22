package com.bank.branch.platform.performance.mapper.typehandler;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.sql.CallableStatement;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.Types;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * SlotMapTypeHandler 单元测试（Task P1.4 Red）.
 *
 * <p>TypeHandler 承担两件事：
 * <ul>
 *   <li>读：从 {@link ResultSet} 的 val_1..val_200 列一次性读到
 *       {@code Map<Integer, BigDecimal>}（稀疏存储，null 的槽不放）</li>
 *   <li>写：在 {@link PreparedStatement#setObject} 时不参与（宽表的写入走 Mapper 的
 *       insertSlotValue 单列路径，此 handler 仅服务"全量读"场景，因此 setParameter
 *       直接 noop 或设为 NULL 即可）</li>
 * </ul>
 *
 * <p>本测试覆盖：
 * <ul>
 *   <li>正常路径：val_1=1.00、val_50=50、val_200=200，其他 null → Map 只含 3 条</li>
 *   <li>边界：所有列都 null → Map 空</li>
 *   <li>按列名读取（{@code getNullableResult(rs, columnName)}） 与按列索引读取
 *       （{@code getNullableResult(rs, columnIndex)}） 均支持</li>
 *   <li>setParameter 为 noop：对 PreparedStatement 无任何副作用</li>
 * </ul>
 */
class SlotMapTypeHandlerTest {

    private final SlotMapTypeHandler handler = new SlotMapTypeHandler();

    @Test
    @DisplayName("getNullableResult(rs, columnName) 读取 200 列，仅非 null 槽进入 Map")
    void getNullableResult_byColumnName_packsSparseMap() throws Exception {
        ResultSet rs = mock(ResultSet.class);
        // 所有列默认返回 null
        when(rs.getBigDecimal(anyString())).thenReturn(null);
        // 仅 val_1 / val_50 / val_200 三列有值
        when(rs.getBigDecimal("val_1")).thenReturn(new BigDecimal("1.00"));
        when(rs.getBigDecimal("val_50")).thenReturn(new BigDecimal("50"));
        when(rs.getBigDecimal("val_200")).thenReturn(new BigDecimal("200"));

        Map<Integer, BigDecimal> slots = handler.getNullableResult(rs, "slots");

        assertThat(slots).hasSize(3);
        assertThat(slots.get(1)).isEqualByComparingTo("1.00");
        assertThat(slots.get(50)).isEqualByComparingTo("50");
        assertThat(slots.get(200)).isEqualByComparingTo("200");
        assertThat(slots.get(2)).isNull();
    }

    @Test
    @DisplayName("getNullableResult(rs, columnIndex) 读取基于 ResultSetMetaData 发现 val_ 列")
    void getNullableResult_byColumnIndex_discoversValColumns() throws Exception {
        // columnIndex 路径要求通过 metadata 遍历列名，选出所有 val_N 列聚合
        ResultSet rs = mock(ResultSet.class);
        ResultSetMetaData md = mock(ResultSetMetaData.class);
        when(rs.getMetaData()).thenReturn(md);
        when(md.getColumnCount()).thenReturn(4);
        when(md.getColumnLabel(1)).thenReturn("id");
        when(md.getColumnLabel(2)).thenReturn("emp_id");
        when(md.getColumnLabel(3)).thenReturn("val_7");
        when(md.getColumnLabel(4)).thenReturn("val_200");
        when(rs.getBigDecimal("val_7")).thenReturn(new BigDecimal("7.7"));
        when(rs.getBigDecimal("val_200")).thenReturn(new BigDecimal("200"));

        Map<Integer, BigDecimal> slots = handler.getNullableResult(rs, 1);

        assertThat(slots).hasSize(2);
        assertThat(slots.get(7)).isEqualByComparingTo("7.7");
        assertThat(slots.get(200)).isEqualByComparingTo("200");
    }

    @Test
    @DisplayName("所有 val_ 列均 null 时返回空 Map")
    void getNullableResult_allNull_returnsEmptyMap() throws Exception {
        ResultSet rs = mock(ResultSet.class);
        when(rs.getBigDecimal(anyString())).thenReturn(null);

        Map<Integer, BigDecimal> slots = handler.getNullableResult(rs, "slots");

        assertThat(slots).isEmpty();
    }

    @Test
    @DisplayName("setNonNullParameter 对 PreparedStatement 不产生任何操作（noop）")
    void setNonNullParameter_isNoop() throws Exception {
        PreparedStatement ps = mock(PreparedStatement.class);

        handler.setNonNullParameter(ps, 1, Map.of(1, new BigDecimal("1")), null);

        // 因为宽表写入走 Mapper 的 insertSlotValue 单列路径，此 handler 不应触发
        // PreparedStatement.setXxx 调用，避免误入写路径
        verifyNoInteractions(ps);
    }

    @Test
    @DisplayName("CallableStatement 路径支持按列索引读取（占位实现：返回空 Map）")
    void getNullableResult_fromCallableStatement_returnsEmpty() throws Exception {
        CallableStatement cs = mock(CallableStatement.class);

        Map<Integer, BigDecimal> slots = handler.getNullableResult(cs, 1);

        // V1.1 宽表读取无存储过程场景，CallableStatement 入口保留实现简单为空 Map
        assertThat(slots).isEmpty();
        verify(cs, org.mockito.Mockito.never()).getBigDecimal(anyInt());
    }

    @Test
    @DisplayName("JDBC Type 在 Mybatis 层读取时不需要，setParameter 的 JdbcType 参数值可为 null")
    void typeHandler_isRegistrable_andJdbcTypeParam_isAccepted() throws Exception {
        // 确保 handler 能处理 JdbcType.NULL、JdbcType.DECIMAL 等都不抛
        PreparedStatement ps = mock(PreparedStatement.class);
        handler.setParameter(ps, 1, Map.of(), org.apache.ibatis.type.JdbcType.DECIMAL);
        // 仍然 noop
        verify(ps, org.mockito.Mockito.never()).setObject(anyInt(), org.mockito.ArgumentMatchers.any(), anyInt());
        // 消除未使用变量告警
        assertThat(Types.DECIMAL).isNotZero();
    }
}
