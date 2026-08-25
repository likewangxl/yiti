package com.bank.branch.platform.report.support;

import com.bank.branch.platform.report.dto.req.ScreenDataReqDTO;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonToken;
import com.fasterxml.jackson.core.StreamReadFeature;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.JsonMappingException;
import com.fasterxml.jackson.databind.deser.std.StdDeserializer;

import java.io.IOException;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/**
 * {@link ScreenDataReqDTO} 的运行时 HTTP 请求体解析器。
 *
 * <p>取数请求中的版本、区块和数据源 ID 都是安全身份字段。Jackson 普通 Bean 绑定会对重复 JSON
 * 键采用最后一次值，攻击者可以据此让审计或前置校验与最终服务入参不一致。此解析器只作用于该 DTO，
 * 在流式解析阶段启用重复检测并在根对象再次显式校验，避免改变其他 API 的 ObjectMapper 行为。</p>
 */
public class ScreenDataReqDTODeserializer extends StdDeserializer<ScreenDataReqDTO> {

    private static final TypeReference<Map<String, String>> STRING_MAP = new TypeReference<>() { };

    private static final StrictJsonIntegerDeserializer STRICT_INTEGER = new StrictJsonIntegerDeserializer();

    private static final StrictJsonLongDeserializer STRICT_LONG = new StrictJsonLongDeserializer();

    /** 无参构造器供 Jackson 注册此局部 DTO 解析器。 */
    public ScreenDataReqDTODeserializer() {
        super(ScreenDataReqDTO.class);
    }

    /**
     * 在请求体边界构造 DTO，任何顶层重复字段均拒绝，身份字段绝不存在 last-wins 路径。
     *
     * @param parser 当前 HTTP JSON 流
     * @param context Jackson 反序列化上下文
     * @return 已解析的运行时取数请求
     * @throws IOException JSON 结构、重复字段或字段类型非法时抛出，使 MVC 统一返回 VALID_005/400
     */
    @Override
    public ScreenDataReqDTO deserialize(JsonParser parser, DeserializationContext context) throws IOException {
        // 此 feature 仅作用于当前 HTTP 请求流；显式 Set 是兼容已读取 START_OBJECT 的第二层兜底。
        parser.enable(StreamReadFeature.STRICT_DUPLICATE_DETECTION.mappedFeature());
        if (parser.currentToken() == null) {
            parser.nextToken();
        }
        if (!parser.isExpectedStartObjectToken()) {
            throw JsonMappingException.from(parser, "请求体必须是 JSON 对象");
        }

        ScreenDataReqDTO request = new ScreenDataReqDTO();
        Set<String> seenFields = new HashSet<>();
        while (parser.nextToken() != JsonToken.END_OBJECT) {
            if (!parser.hasToken(JsonToken.FIELD_NAME)) {
                throw JsonMappingException.from(parser, "请求体字段格式错误");
            }
            String fieldName = parser.currentName();
            if (!seenFields.add(fieldName)) {
                throw JsonMappingException.from(parser, "请求体不允许重复字段: " + fieldName);
            }

            JsonToken valueToken = parser.nextToken();
            if (valueToken == null) {
                throw JsonMappingException.from(parser, "请求体字段缺少值: " + fieldName);
            }
            bindField(parser, context, request, fieldName, valueToken);
        }
        return request;
    }

    /** 将单个顶层字段按原 DTO 契约写入请求对象。 */
    private void bindField(JsonParser parser, DeserializationContext context, ScreenDataReqDTO request,
                           String fieldName, JsonToken valueToken) throws IOException {
        switch (fieldName) {
            case "schemaVersion" -> request.setSchemaVersion(
                    valueToken == JsonToken.VALUE_NULL ? null : STRICT_INTEGER.deserialize(parser, context));
            case "dsId" -> request.setDsId(
                    valueToken == JsonToken.VALUE_NULL ? null : STRICT_LONG.deserialize(parser, context));
            case "screenCode" -> request.setScreenCode(context.readValue(parser, String.class));
            case "blockId" -> request.setBlockId(
                    valueToken == JsonToken.VALUE_NULL ? null : STRICT_LONG.deserialize(parser, context));
            case "previewState" -> request.setPreviewState(context.readValue(parser, String.class));
            case "period" -> request.setPeriod(context.readValue(parser, String.class));
            case "dateFrom" -> request.setDateFrom(context.readValue(parser, String.class));
            case "dateTo" -> request.setDateTo(context.readValue(parser, String.class));
            case "contextParams" -> request.setContextParams(parser.getCodec().readValue(parser, STRING_MAP));
            // 保持 @JsonIgnore 原有语义：客户端可传，但不会注入服务端授权上下文或命名机构组标记。
            case "serverOrgCodes", "namedGroup" -> parser.skipChildren();
            default -> context.handleUnknownProperty(parser, this, request, fieldName);
        }
    }
}
