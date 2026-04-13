package com.bank.branch.platform.portal.typehandler;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.ibatis.type.BaseTypeHandler;
import org.apache.ibatis.type.JdbcType;
import org.apache.ibatis.type.MappedJdbcTypes;
import org.apache.ibatis.type.MappedTypes;

import java.sql.CallableStatement;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Collections;
import java.util.List;

/**
 * MyBatis TypeHandler: List<String> ↔ JSON 数组字符串
 *
 * 用于 product_info.responsible_emp_ids 和 addrbook_employee.responsible_product_ids 字段
 *
 * 边界处理：
 * - serialize(null) → null
 * - serialize(empty) → "[]"
 * - deserialize(null/empty/whitespace) → emptyList
 */
@MappedTypes(List.class)
@MappedJdbcTypes(JdbcType.LONGVARCHAR)
public class JsonStringListTypeHandler extends BaseTypeHandler<List<String>> {

    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final TypeReference<List<String>> LIST_REF = new TypeReference<>() {};

    public String serialize(List<String> list) {
        if (list == null) return null;
        try {
            return MAPPER.writeValueAsString(list);
        } catch (Exception e) {
            throw new IllegalStateException("JSON serialize failed: " + list, e);
        }
    }

    public List<String> deserialize(String json) {
        if (json == null || json.trim().isEmpty()) return Collections.emptyList();
        try {
            return MAPPER.readValue(json, LIST_REF);
        } catch (Exception e) {
            throw new IllegalStateException("JSON deserialize failed: " + json, e);
        }
    }

    @Override
    public void setNonNullParameter(PreparedStatement ps, int i, List<String> parameter, JdbcType jdbcType)
            throws SQLException {
        ps.setString(i, serialize(parameter));
    }

    @Override
    public List<String> getNullableResult(ResultSet rs, String columnName) throws SQLException {
        return deserialize(rs.getString(columnName));
    }

    @Override
    public List<String> getNullableResult(ResultSet rs, int columnIndex) throws SQLException {
        return deserialize(rs.getString(columnIndex));
    }

    @Override
    public List<String> getNullableResult(CallableStatement cs, int columnIndex) throws SQLException {
        return deserialize(cs.getString(columnIndex));
    }
}
