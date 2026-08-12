package com.bank.branch.platform.report.support;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonToken;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.JsonMappingException;
import com.fasterxml.jackson.databind.deser.std.StdDeserializer;

import java.io.IOException;

/** 运行时 blockId/dsId 的本地严格 JSON long 反序列化器，拒绝字符串、浮点和越界数值。 */
public class StrictJsonLongDeserializer extends StdDeserializer<Long> {

    public StrictJsonLongDeserializer() {
        super(Long.class);
    }

    @Override
    public Long deserialize(JsonParser parser, DeserializationContext context) throws IOException {
        if (!parser.hasToken(JsonToken.VALUE_NUMBER_INT)) {
            throw JsonMappingException.from(parser, "必须使用 JSON 整数值");
        }
        try {
            return parser.getLongValue();
        } catch (RuntimeException ex) {
            throw JsonMappingException.from(parser, "JSON 整数超出 Long 范围", ex);
        }
    }
}
