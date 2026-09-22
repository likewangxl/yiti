package com.bank.branch.platform.report.service.screen.presentation;

import com.bank.branch.platform.report.entity.RptScreenDatasource;
import com.bank.branch.platform.report.enums.RptErrorCode;
import com.bank.branch.platform.report.exception.RptException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HexFormat;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/** 服务端生成的不可变查询定义快照；不包含连接凭据或运行权限。 */
public final class PublishedDatasourceDefinition {

    public static final String NODE = "sourceDefinition";
    private static final Set<String> FIELDS = Set.of(
            "datasourceId", "sourceKind", "dsType", "bizLine", "config", "timeParams", "definitionHash");
    private static final Set<String> SENSITIVE_KEYS = Set.of(
            "password", "secret", "secretkey", "accesskey", "authorization", "token",
            "username", "url", "endpoint", "connectionstring");

    private PublishedDatasourceDefinition() {
    }

    public static boolean requiredFor(JsonNode root) {
        return root != null && root.path("canvasStyle").path("presentation")
                .path("displaySchemaVersion").asInt(0) == 1;
    }

    /** 从当前已校验数据源生成快照，并写入一个 bindSnapshot。 */
    public static void write(ObjectNode bindSnapshot, RptScreenDatasource datasource, ObjectMapper mapper) {
        if (bindSnapshot == null || datasource == null || datasource.getId() == null) {
            throw new RptException(RptErrorCode.SCREEN_DS_NOT_FOUND);
        }
        try {
            JsonNode config = parseObject(mapper, datasource.getConfigJson());
            JsonNode timeParams = parseOptionalJson(mapper, datasource.getTimeParamJson());
            rejectSensitive(config);
            rejectSensitive(timeParams);
            ObjectNode definition = mapper.createObjectNode();
            definition.put("datasourceId", datasource.getId());
            definition.put("sourceKind", required(datasource.getSourceKind()));
            definition.put("dsType", required(datasource.getDsType()));
            definition.put("bizLine", normalizeLine(datasource.getBizLine()));
            definition.set("config", canonical(config, mapper));
            if (timeParams != null) definition.set("timeParams", canonical(timeParams, mapper));
            definition.put("definitionHash", hash(definition, mapper));
            bindSnapshot.set(NODE, definition);
        } catch (RptException ex) {
            throw ex;
        } catch (Exception ex) {
            throw new RptException(RptErrorCode.SCREEN_DS_CONFIG_INVALID, ex);
        }
    }

    /** 快照存在时验证身份/哈希并生成仅供查询的有效数据源副本；旧包缺省时返回current。 */
    public static RptScreenDatasource effective(JsonNode bindSnapshot, RptScreenDatasource current,
                                                ObjectMapper mapper, boolean required) {
        JsonNode definition = bindSnapshot == null ? null : bindSnapshot.get(NODE);
        if (definition == null || definition.isNull() || definition.isMissingNode()) {
            if (required) throw new RptException(RptErrorCode.SCREEN_PUBLISHED_SNAPSHOT_UNTRUSTED);
            return current;
        }
        try {
            validateShape(definition);
            if (current == null || current.getId() == null
                    || definition.path("datasourceId").longValue() != current.getId()) {
                throw new RptException(RptErrorCode.SCREEN_PUBLISHED_SNAPSHOT_UNTRUSTED);
            }
            String expectedHash = definition.path("definitionHash").asText();
            ObjectNode unhashed = ((ObjectNode) definition).deepCopy();
            unhashed.remove("definitionHash");
            if (!hash(unhashed, mapper).equals(expectedHash)) {
                throw new RptException(RptErrorCode.SCREEN_PUBLISHED_SNAPSHOT_UNTRUSTED);
            }
            rejectSensitive(definition.path("config"));
            rejectSensitive(definition.path("timeParams"));
            RptScreenDatasource effective = new RptScreenDatasource();
            effective.setId(current.getId());
            effective.setDsCode(current.getDsCode());
            effective.setDsName(current.getDsName());
            effective.setSourceKind(definition.path("sourceKind").asText());
            effective.setDsType(definition.path("dsType").asText());
            effective.setBizLine(definition.path("bizLine").asText());
            effective.setConfigJson(definition.path("config").toString());
            effective.setTimeParamJson(definition.has("timeParams")
                    ? definition.path("timeParams").toString() : null);
            effective.setStatus(current.getStatus());
            return effective;
        } catch (RptException ex) {
            throw ex;
        } catch (Exception ex) {
            throw new RptException(RptErrorCode.SCREEN_PUBLISHED_SNAPSHOT_UNTRUSTED, ex);
        }
    }

    private static void validateShape(JsonNode definition) {
        if (!definition.isObject() || !definition.path("datasourceId").isIntegralNumber()
                || definition.path("datasourceId").longValue() <= 0
                || !definition.path("sourceKind").isTextual() || definition.path("sourceKind").asText().isBlank()
                || !definition.path("dsType").isTextual() || definition.path("dsType").asText().isBlank()
                || !definition.path("bizLine").isTextual() || !definition.path("config").isObject()
                || !definition.path("definitionHash").isTextual()) {
            throw new RptException(RptErrorCode.SCREEN_PUBLISHED_SNAPSHOT_UNTRUSTED);
        }
        definition.fieldNames().forEachRemaining(name -> {
            if (!FIELDS.contains(name)) throw new RptException(RptErrorCode.SCREEN_PUBLISHED_SNAPSHOT_UNTRUSTED);
        });
    }

    private static JsonNode parseObject(ObjectMapper mapper, String json) throws Exception {
        JsonNode value = mapper.readTree(json == null || json.isBlank() ? "{}" : json);
        if (value == null || !value.isObject()) throw new RptException(RptErrorCode.SCREEN_DS_CONFIG_INVALID);
        return value;
    }

    private static JsonNode parseOptionalJson(ObjectMapper mapper, String json) throws Exception {
        if (json == null || json.isBlank()) return null;
        JsonNode value = mapper.readTree(json);
        if (value == null || (!value.isArray() && !value.isObject())) {
            throw new RptException(RptErrorCode.SCREEN_DS_CONFIG_INVALID);
        }
        return value;
    }

    private static JsonNode canonical(JsonNode value, ObjectMapper mapper) {
        if (value == null || value.isNull() || value.isMissingNode()) return value;
        if (value.isArray()) {
            ArrayNode result = mapper.createArrayNode();
            value.forEach(item -> result.add(canonical(item, mapper)));
            return result;
        }
        if (value.isObject()) {
            ObjectNode result = mapper.createObjectNode();
            List<String> names = new ArrayList<>();
            value.fieldNames().forEachRemaining(names::add);
            names.sort(Comparator.naturalOrder());
            for (String name : names) result.set(name, canonical(value.get(name), mapper));
            return result;
        }
        return value.deepCopy();
    }

    private static void rejectSensitive(JsonNode value) {
        if (value == null || value.isNull() || value.isMissingNode()) return;
        if (value.isArray()) {
            value.forEach(PublishedDatasourceDefinition::rejectSensitive);
            return;
        }
        if (value.isObject()) {
            value.fields().forEachRemaining(entry -> {
                String key = entry.getKey().replaceAll("[^A-Za-z]", "").toLowerCase(Locale.ROOT);
                if (SENSITIVE_KEYS.contains(key)) {
                    throw new RptException(RptErrorCode.SCREEN_DS_CONFIG_INVALID);
                }
                rejectSensitive(entry.getValue());
            });
        }
    }

    private static String hash(ObjectNode definitionWithoutHash, ObjectMapper mapper) throws Exception {
        ObjectNode copy = definitionWithoutHash.deepCopy();
        copy.remove("definitionHash");
        byte[] bytes = mapper.writeValueAsString(canonical(copy, mapper)).getBytes(StandardCharsets.UTF_8);
        return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
    }

    private static String required(String value) {
        if (value == null || value.isBlank()) throw new RptException(RptErrorCode.SCREEN_DS_CONFIG_INVALID);
        return value.trim();
    }

    private static String normalizeLine(String value) {
        return value == null || value.isBlank() ? "COMMON" : value.trim().toUpperCase(Locale.ROOT);
    }
}
