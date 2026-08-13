package com.bank.branch.platform.report.support;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonToken;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.JsonMappingException;
import com.fasterxml.jackson.databind.deser.std.StdDeserializer;

import java.io.IOException;

/**
 * 仅用于安全敏感 HTTP 身份字段的严格整数反序列化。
 *
 * <p>Jackson 默认会把字符串和浮点值宽松转换为 Java {@link Integer}；运行时协议版本属于
 * 身份边界，必须要求 JSON 原生的 {@code VALUE_NUMBER_INT} token，不能让 {@code "1"} 或
 * {@code 1.0} 进入服务层后再被误判为合法版本。</p>
 */
public class StrictJsonIntegerDeserializer extends StdDeserializer<Integer> {

    public StrictJsonIntegerDeserializer() {
        super(Integer.class);
    }

    @Override
    public Integer deserialize(JsonParser parser, DeserializationContext context) throws IOException {
        if (!parser.hasToken(JsonToken.VALUE_NUMBER_INT)) {
            throw JsonMappingException.from(parser, "必须使用 JSON 整数值");
        }
        try {
            return parser.getIntValue();
        } catch (RuntimeException ex) {
            throw JsonMappingException.from(parser, "JSON 整数超出 Integer 范围", ex);
        }
    }
}
