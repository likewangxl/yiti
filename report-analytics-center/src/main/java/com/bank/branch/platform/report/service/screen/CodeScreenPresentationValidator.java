package com.bank.branch.platform.report.service.screen;

import com.bank.branch.platform.report.entity.RptScreenBlock;
import com.bank.branch.platform.report.entity.RptScreenDatasource;
import com.bank.branch.platform.report.enums.RptErrorCode;
import com.bank.branch.platform.report.exception.RptException;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;

/**
 * 代码化经营大屏的纯 JSON 契约校验器。
 *
 * <p>代码化声明复用现有 canvas_style_json/canvas_draft_json/canvas_published_json。没有
 * {@code presentation} 的历史画布直接返回，因而不会把旧坐标画布带入新约束。</p>
 */
public final class CodeScreenPresentationValidator {

    public static final String CODE_TYPE = "CODE";
    public static final String BRANCH_OVERVIEW_TEMPLATE = "branch-overview-v1";
    public static final String RETAIL_OVERVIEW_TEMPLATE = "retail-overview-v1";

    private static final ObjectMapper MAPPER = new ObjectMapper()
            .enable(JsonParser.Feature.STRICT_DUPLICATE_DETECTION);
    private static final Set<String> BRANCH_SLOTS = Set.of(
            "deposit", "loan", "customers", "revenue", "rate", "trend", "composition",
            "ranking", "attention", "branches", "branchTrend", "citySummary",
            "depositIncrease", "depositAverage");
    private static final Set<String> RETAIL_SLOTS = Set.of(
            "retailAum", "retailDeposit", "retailDepositAverage", "retailRevenue", "retailLoan",
            "retailValueCustomers", "retailNplRate", "retailTrend", "retailSegments", "retailRanking",
            "retailAttention", "retailTargets", "branches");
    private static final Set<String> SLOTS = union(BRANCH_SLOTS, RETAIL_SLOTS);
    private static final Set<String> UNITS = Set.of(
            "YUAN", "TEN_THOUSAND", "HUNDRED_MILLION", "COUNT", "TEN_THOUSAND_COUNT",
            "PERCENT", "RATIO");
    private static final Set<String> PERIODS = Set.of("LATEST", "LAST_10D", "LAST_1M", "LAST_6M_EOM");
    private static final int DATA_NOTICE_MAX_LENGTH = 240;
    private static final int METRIC_LABEL_MAX_LENGTH = 40;
    private static final int SOURCE_AVAILABILITY_MESSAGE_MAX_LENGTH = 120;
    private static final Set<String> SOURCE_AVAILABILITY_STATUSES = Set.of(
            "AVAILABLE", "NO_SOURCE", "NO_ROWS", "NO_VALUES", "PARTIAL", "HISTORICAL");
    private static final Set<String> METRIC_LABEL_KEYS = Set.of(
            "deposit", "depositIncrease", "depositAverage", "loan", "customers", "revenue", "rate",
            "retailAum", "retailDeposit", "retailDepositAverage", "retailRevenue", "retailValueCustomers",
            "retailLoan", "retailNplRate");
    private static final Set<String> WIDE_TABLES = Set.of(
            "EMP_INDEX_RESULT", "ORG_INDEX_RESULT", "CUST_INDEX_RESULT");
    private static final Map<String, Set<String>> FIELD_KEYS = fieldKeys();
    private static final Map<String, Set<String>> REQUIRED_FIELDS = requiredFields();

    private CodeScreenPresentationValidator() {
    }

    /** 判断样式是否声明 CODE；历史样式解析失败时按非法配置处理。 */
    public static boolean isCodePresentation(String canvasStyleJson) {
        return isCodePresentation(readObject(canvasStyleJson, RptErrorCode.SCREEN_LAYOUT_INVALID));
    }

    /** 判断样式节点是否声明 CODE；调用方已完成 JSON 解析时使用。 */
    public static boolean isCodePresentation(JsonNode canvasStyle) {
        return presentationTemplate(canvasStyle) != null;
    }

    /** 返回 CODE 画布模板；历史样式无 presentation 时返回 null，未知声明按非法配置拒绝。 */
    public static String presentationTemplate(JsonNode canvasStyle) {
        validateSourcePresentationMetadata(canvasStyle);
        JsonNode presentation = canvasStyle == null ? null : canvasStyle.path("presentation");
        if (presentation == null || presentation.isMissingNode() || presentation.isNull()) {
            return null;
        }
        validatePresentation(presentation);
        return presentation.path("template").asText();
    }

    /**
     * 校验已解析画布样式中的运行时展示元数据。
     *
     * <p>这些字段复用既有 canvasStyle JSON，不改变数据库结构；它们仍须在服务端
     * 通过长度、纯文本和指标键白名单校验，不能仅依赖前端下拉框或 JsonNode 取值。</p>
     */
    public static void validateCanvasStyle(String canvasStyleJson) {
        validateSourcePresentationMetadata(readObject(canvasStyleJson, RptErrorCode.SCREEN_LAYOUT_INVALID));
    }

    /** 保存阶段校验样式与草稿组件形状；不要求新 ChartWidget 已经获得 blockId。 */
    public static void validateDraft(String canvasStyleJson, String draftJson) {
        JsonNode style = readObject(canvasStyleJson, RptErrorCode.SCREEN_LAYOUT_INVALID);
        String template = presentationTemplate(style);
        if (template == null) {
            return;
        }
        JsonNode draft = readObject(draftJson, RptErrorCode.SCREEN_LAYOUT_INVALID);
        validateComponents(draft.path("components"), false, Set.of(), template);
    }

    /**
     * 发布/草稿预览阶段校验组件与当前屏 block 行的真实绑定。
     * rows 只作为服务端已按 screenId 查出的候选行，方法仍会拒绝重复/缺失身份。
     */
    public static void validateDraft(String canvasStyleJson, String draftJson,
                                     Collection<RptScreenBlock> rows) {
        JsonNode style = readObject(canvasStyleJson, RptErrorCode.SCREEN_LAYOUT_INVALID);
        String template = presentationTemplate(style);
        if (template == null) {
            return;
        }
        JsonNode draft = readObject(draftJson, RptErrorCode.SCREEN_LAYOUT_INVALID);
        Map<Long, RptScreenBlock> byId = new LinkedHashMap<>();
        if (rows != null) {
            for (RptScreenBlock row : rows) {
                if (row == null || row.getId() == null || row.getId() <= 0
                        || byId.putIfAbsent(row.getId(), row) != null) {
                    throw invalid();
                }
            }
        }
        Set<Long> blockIds = validateComponents(draft.path("components"), true, byId.keySet(), template);
        for (Long blockId : blockIds) {
            RptScreenBlock row = byId.get(blockId);
            if (row == null) {
                throw invalid();
            }
            validateBindJson(row.getBindJson(), bindingKeyFor(draft.path("components"), blockId), template);
        }
    }

    /** 运行时/回滚阶段校验不可变 CODE 发布包及其 bindSnapshots。 */
    public static void validatePublishedPackage(String packageJson) {
        JsonNode root = readObject(packageJson, RptErrorCode.SCREEN_PUBLISHED_SNAPSHOT_UNTRUSTED);
        validatePublishedPackage(root);
    }

    /** 运行时/回滚阶段校验已解析的不可变 CODE 发布包。 */
    public static void validatePublishedPackage(JsonNode root) {
        if (root == null || !root.isObject()) {
            throw untrusted();
        }
        JsonNode style = root.path("canvasStyle");
        String template = presentationTemplate(style);
        if (template == null) {
            return;
        }
        JsonNode components = root.path("components");
        Set<Long> blockIds = validateComponents(components, true, Set.of(), template);
        JsonNode snapshots = root.path("bindSnapshots");
        if (!snapshots.isObject()) {
            throw untrusted();
        }
        Set<Long> snapshotIds = new LinkedHashSet<>();
        Iterator<Map.Entry<String, JsonNode>> fields = snapshots.fields();
        while (fields.hasNext()) {
            Map.Entry<String, JsonNode> entry = fields.next();
            long blockId = parsePositiveLong(entry.getKey());
            if (!snapshotIds.add(blockId) || !blockIds.contains(blockId)) {
                throw untrusted();
            }
            JsonNode snapshot = entry.getValue();
            if (!snapshot.isObject()) {
                throw untrusted();
            }
            JsonNode bind = snapshot.path("bind");
            String bindingKey = bindingKeyFor(components, blockId);
            validateBind(bind, bindingKey, template);
        }
        if (!snapshotIds.equals(blockIds)) {
            throw untrusted();
        }
    }

    /** 仅暴露给 service 的分支机构数据源检查入口。 */
    public static Set<String> slots() {
        return SLOTS;
    }

    /**
     * 校验一个已解析绑定；用于 publish 从 RPT_SCREEN_BLOCK 读取的真实 bindJson。
     */
    public static void validateBind(JsonNode bind, String bindingKey) {
        validateBind(bind, bindingKey, templateForBindingKey(bindingKey));
    }

    /** 按指定 CODE 模板校验绑定，防止 branch/retail 槽位互相混用。 */
    public static void validateBind(JsonNode bind, String bindingKey, String template) {
        Set<String> templateSlots = slotsForTemplate(template);
        if (!templateSlots.contains(bindingKey)) {
            throw invalid();
        }
        if (bind == null || !bind.isObject() || !isPositiveIntegral(bind.path("dsId"))) {
            throw invalid();
        }
        String period = bind.path("period").asText(null);
        if (period == null || period.isBlank()) {
            period = ("trend".equals(bindingKey) || "branchTrend".equals(bindingKey))
                    ? "LAST_6M_EOM" : "LATEST";
        }
        if (!PERIODS.contains(period)) {
            throw invalid();
        }
        JsonNode fields = bind.path("fields");
        JsonNode units = bind.path("units");
        if (!fields.isObject() || !units.isObject()) {
            throw invalid();
        }
        Set<String> allowed = fieldKeys(template, bindingKey);
        Set<String> required = requiredFields(template, bindingKey);
        Set<String> present = new HashSet<>();
        Iterator<Map.Entry<String, JsonNode>> fieldEntries = fields.fields();
        while (fieldEntries.hasNext()) {
            Map.Entry<String, JsonNode> entry = fieldEntries.next();
            if (!allowed.contains(entry.getKey()) || !entry.getValue().isTextual()
                    || entry.getValue().asText().isBlank()) {
                throw invalid();
            }
            present.add(entry.getKey());
        }
        if ("composition".equals(bindingKey)) {
            validateCompositionShape(present);
        } else if (!present.containsAll(required)) {
            throw invalid();
        }
        if ("trend".equals(bindingKey)
                && !present.contains("deposit") && !present.contains("loan")
                && !present.contains("depositIncrease")) {
            throw invalid();
        }
        if ("retailTrend".equals(bindingKey)
                && !present.contains("aum") && !present.contains("deposit")) {
            throw invalid();
        }
        if ("branchTrend".equals(bindingKey)
                && !present.contains("deposit") && !present.contains("loan")) {
            throw invalid();
        }
        if ("citySummary".equals(bindingKey)
                && !present.contains("cityCode") && !present.contains("orgCode")) {
            throw invalid();
        }
        if ("citySummary".equals(bindingKey)
                && present.stream().noneMatch(Set.of("deposit", "loan", "customers", "revenue", "rate")::contains)) {
            throw invalid();
        }
        Iterator<Map.Entry<String, JsonNode>> unitEntries = units.fields();
        while (unitEntries.hasNext()) {
            Map.Entry<String, JsonNode> entry = unitEntries.next();
            if (!present.contains(entry.getKey()) || !entry.getValue().isTextual()
                    || !UNITS.contains(entry.getValue().asText())) {
                throw invalid();
            }
            Set<String> unitKinds = unitKinds(bindingKey, entry.getKey());
            if (unitKinds.isEmpty() && "orgCode".equals(entry.getKey())) {
                throw invalid();
            }
            if (!unitKinds.isEmpty() && !unitKinds.contains(entry.getValue().asText())) {
                throw invalid();
            }
        }
        if ("composition".equals(bindingKey)) {
            validateCompositionUnitKinds(present, units);
        }
        // A numeric semantic with a declared unit kind cannot be published without its unit:
        // the runtime would otherwise have to guess the scale and render UNKNOWN_UNIT/null.
        for (String semantic : present) {
            Set<String> unitKinds = unitKinds(bindingKey, semantic);
            if (!unitKinds.isEmpty()) {
                JsonNode unit = units.get(semantic);
                if (unit == null || !unit.isTextual() || !unitKinds.contains(unit.asText())) {
                    throw invalid();
                }
            }
        }
    }

    /**
     * 校验绑定字段与数据源的服务端输出 schema 一致。
     *
     * <p>CODE 的字段映射来自管理端请求，不能只依赖前端下拉框。这里按现有
     * {@code ScreenQueryEngine} 的固定 SELECT 形状推导可用列，并结合保存的数据源
     * {@code fieldMeta.role} 校验 DIM/METRIC 角色。CUSTOM_SQL 没有固定列形状，只有
     * 已声明的 fieldMeta 能作为可证明的输出 schema。</p>
     */
    public static void validateBindAgainstDatasource(JsonNode bind, String bindingKey,
                                                      RptScreenDatasource datasource) {
        validateBindAgainstDatasource(bind, bindingKey, datasource, templateForBindingKey(bindingKey));
    }

    /** 按指定 CODE 模板校验绑定字段与数据源输出 schema 一致。 */
    public static void validateBindAgainstDatasource(JsonNode bind, String bindingKey,
                                                      RptScreenDatasource datasource,
                                                      String template) {
        validateBind(bind, bindingKey, template);
        if (datasource == null || datasource.getId() == null
                || !isPositiveIntegral(bind.path("dsId"))
                || bind.path("dsId").longValue() != datasource.getId()) {
            throw invalid();
        }
        // The new fixed two-column conversion is intentionally scoped to the
        // institution wide table. Legacy row bindings retain their existing
        // datasource support; this branch must not turn CUSTOM_SQL or another
        // source kind into a new composition execution path.
        if ("composition".equals(bindingKey) && isCompositionColumns(bind)
                && !isInstitutionWideTable(datasource)) {
            throw invalid();
        }
        Map<String, String> outputRoles = outputRoles(datasource);
        JsonNode fields = bind.path("fields");
        Iterator<Map.Entry<String, JsonNode>> entries = fields.fields();
        while (entries.hasNext()) {
            Map.Entry<String, JsonNode> entry = entries.next();
            String column = entry.getValue().asText().trim();
            String actualRole = outputRoles.get(column);
            if (actualRole == null) {
                throw invalid();
            }
            String expectedRole = unitKinds(bindingKey, entry.getKey()).isEmpty() ? "DIM" : "METRIC";
            if (!expectedRole.equals(actualRole)) {
                throw invalid();
            }
        }
        if ("retailRanking".equals(bindingKey) && isOrgSubjectAggregation(datasource)
                && !"org_code".equals(fields.path("orgCode").asText())) {
            // NAMED_GROUP runtime uses the engine's subject aggregate. org_name is only a
            // display dimension and cannot be accepted as the catalog identity.
            throw invalid();
        }
    }

    private static void validateBindJson(String bindJson, String bindingKey, String template) {
        JsonNode bind = readObject(bindJson, RptErrorCode.SCREEN_LAYOUT_INVALID);
        try {
            validateBind(bind, bindingKey, template);
        } catch (RptException ex) {
            throw ex;
        } catch (RuntimeException ex) {
            throw invalid();
        }
    }

    private static Set<Long> validateComponents(JsonNode components, boolean requireBlockId,
                                                Collection<Long> knownBlockIds, String template) {
        if (components == null || !components.isArray() || components.isEmpty()) {
            throw invalid();
        }
        Set<String> ids = new HashSet<>();
        Set<String> bindingKeys = new HashSet<>();
        Set<Long> blockIds = new LinkedHashSet<>();
        for (JsonNode component : components) {
            if (!component.isObject() || !"ChartWidget".equals(component.path("component").asText())) {
                throw invalid();
            }
            JsonNode children = component.path("children");
            if (!children.isMissingNode() && !children.isNull()
                    && (!children.isArray() || !children.isEmpty())) {
                throw invalid();
            }
            String id = textRequired(component.path("id"));
            if (!ids.add(id)) {
                throw invalid();
            }
            JsonNode propValue = component.path("propValue");
            if (!propValue.isObject()) {
                throw invalid();
            }
            String bindingKey = textRequired(propValue.path("bindingKey"));
            if (!slotsForTemplate(template).contains(bindingKey) || !bindingKeys.add(bindingKey)) {
                throw invalid();
            }
            JsonNode blockId = component.path("blockId");
            if (requireBlockId) {
                if (!isPositiveIntegral(blockId)) {
                    throw invalid();
                }
                long idValue = blockId.longValue();
                if (!blockIds.add(idValue)
                        || (knownBlockIds != null && !knownBlockIds.isEmpty() && !knownBlockIds.contains(idValue))) {
                    throw invalid();
                }
            } else if (!blockId.isMissingNode() && !blockId.isNull()
                    && !isPositiveIntegral(blockId)) {
                throw invalid();
            }
            JsonNode bindJson = component.path("bindJson");
            if (!bindJson.isTextual()) {
                throw invalid();
            }
            validateBindJson(bindJson.asText(), bindingKey, template);
        }
        return blockIds;
    }

    private static String bindingKeyFor(JsonNode components, long blockId) {
        if (components != null && components.isArray()) {
            for (JsonNode component : components) {
                if (component.path("blockId").isIntegralNumber()
                        && component.path("blockId").longValue() == blockId) {
                    return component.path("propValue").path("bindingKey").asText();
                }
            }
        }
        throw invalid();
    }

    private static void validatePresentation(JsonNode presentation) {
        if (presentation == null || !presentation.isObject()
                || !CODE_TYPE.equals(presentation.path("type").asText())
                || (!BRANCH_OVERVIEW_TEMPLATE.equals(presentation.path("template").asText())
                && !RETAIL_OVERVIEW_TEMPLATE.equals(presentation.path("template").asText()))) {
            throw invalid();
        }
    }

    private static void validateSourcePresentationMetadata(JsonNode canvasStyle) {
        if (canvasStyle == null || !canvasStyle.isObject()) {
            return;
        }
        JsonNode dataNotice = canvasStyle.get("dataNotice");
        if (dataNotice != null && !dataNotice.isNull()) {
            validatePlainText(dataNotice, DATA_NOTICE_MAX_LENGTH, true);
        }
        JsonNode metricLabels = canvasStyle.get("metricLabels");
        if (metricLabels != null && !metricLabels.isNull()) {
            if (!metricLabels.isObject()) {
                throw invalid();
            }
            Iterator<Map.Entry<String, JsonNode>> entries = metricLabels.fields();
            while (entries.hasNext()) {
                Map.Entry<String, JsonNode> entry = entries.next();
                if (!METRIC_LABEL_KEYS.contains(entry.getKey())) {
                    throw invalid();
                }
                validatePlainText(entry.getValue(), METRIC_LABEL_MAX_LENGTH, false);
            }
        }
        validateSourceAvailability(canvasStyle.get("sourceAvailability"));
    }

    private static void validateSourceAvailability(JsonNode availability) {
        if (availability == null || availability.isMissingNode()) {
            return;
        }
        if (!availability.isObject() || availability.size() > BRANCH_SLOTS.size()) {
            throw invalid();
        }
        Iterator<Map.Entry<String, JsonNode>> slots = availability.fields();
        while (slots.hasNext()) {
            Map.Entry<String, JsonNode> slot = slots.next();
            if (!BRANCH_SLOTS.contains(slot.getKey()) || !slot.getValue().isObject()) {
                throw invalid();
            }
            validateAvailabilityNode(slot.getValue(), FIELD_KEYS.get(slot.getKey()), true);
        }
    }

    private static void validateAvailabilityNode(JsonNode node, Set<String> allowedFields,
                                                  boolean allowNestedFields) {
        Set<String> allowed = new HashSet<>(Set.of("status", "message", "dataDate"));
        if (allowNestedFields) {
            allowed.add("fields");
        }
        Iterator<String> names = node.fieldNames();
        while (names.hasNext()) {
            if (!allowed.contains(names.next())) {
                throw invalid();
            }
        }
        JsonNode status = node.get("status");
        if (status == null || !status.isTextual()
                || !SOURCE_AVAILABILITY_STATUSES.contains(status.asText())) {
            throw invalid();
        }
        JsonNode message = node.get("message");
        if (node.has("message")) {
            validatePlainText(message, SOURCE_AVAILABILITY_MESSAGE_MAX_LENGTH, true);
        }
        if (node.has("dataDate")) {
            validateDataDate(node.get("dataDate"));
        }
        if (!allowNestedFields) {
            return;
        }
        JsonNode fields = node.get("fields");
        if (fields == null) {
            return;
        }
        if (fields.isNull()) {
            throw invalid();
        }
        if (!fields.isObject() || allowedFields == null || fields.size() > allowedFields.size()) {
            throw invalid();
        }
        Iterator<Map.Entry<String, JsonNode>> entries = fields.fields();
        while (entries.hasNext()) {
            Map.Entry<String, JsonNode> entry = entries.next();
            if (!allowedFields.contains(entry.getKey()) || !entry.getValue().isObject()) {
                throw invalid();
            }
            validateAvailabilityNode(entry.getValue(), Set.of(), false);
        }
    }

    private static void validateDataDate(JsonNode dataDate) {
        if (dataDate == null || dataDate.isNull()
                || !dataDate.isTextual() || !dataDate.asText().matches("\\d{4}-\\d{2}-\\d{2}")) {
            throw invalid();
        }
        try {
            LocalDate.parse(dataDate.asText(), DateTimeFormatter.ISO_LOCAL_DATE);
        } catch (DateTimeParseException ex) {
            throw invalid();
        }
    }

    private static void validatePlainText(JsonNode node, int maxLength, boolean allowBlank) {
        if (node == null || !node.isTextual()) {
            throw invalid();
        }
        String value = node.asText();
        if (!allowBlank && value.trim().isEmpty()) {
            throw invalid();
        }
        if (value.codePointCount(0, value.length()) > maxLength
                || value.chars().anyMatch(ch -> ch < 0x20 || ch == 0x7F)
                || value.indexOf('<') >= 0 || value.indexOf('>') >= 0) {
            throw invalid();
        }
    }

    private static Set<String> slotsForTemplate(String template) {
        if (BRANCH_OVERVIEW_TEMPLATE.equals(template)) {
            return BRANCH_SLOTS;
        }
        if (RETAIL_OVERVIEW_TEMPLATE.equals(template)) {
            return RETAIL_SLOTS;
        }
        throw invalid();
    }

    private static String templateForBindingKey(String bindingKey) {
        if (BRANCH_SLOTS.contains(bindingKey)) {
            return BRANCH_OVERVIEW_TEMPLATE;
        }
        if (RETAIL_SLOTS.contains(bindingKey)) {
            return RETAIL_OVERVIEW_TEMPLATE;
        }
        throw invalid();
    }

    private static JsonNode readObject(String json, RptErrorCode code) {
        try {
            JsonNode result = MAPPER.readTree(json == null || json.isBlank() ? "{}" : json);
            if (result == null || !result.isObject()) {
                throw new IllegalArgumentException("对象 JSON 必须为 object");
            }
            return result;
        } catch (Exception ex) {
            throw new RptException(code, ex);
        }
    }

    private static String textRequired(JsonNode node) {
        if (node == null || !node.isTextual() || node.asText().isBlank()) {
            throw invalid();
        }
        return node.asText();
    }

    /** 业务构成只能是旧的 name/value 行形状或固定的 corporate/retail 双列形状。 */
    private static void validateCompositionShape(Set<String> present) {
        boolean hasRowField = present.contains("name") || present.contains("value");
        boolean hasColumnField = present.contains("corporate") || present.contains("retail");
        if (hasRowField && hasColumnField) {
            throw invalid();
        }
        if (hasRowField && !present.containsAll(Set.of("name", "value"))) {
            throw invalid();
        }
        if (hasColumnField && !present.containsAll(Set.of("corporate", "retail"))) {
            throw invalid();
        }
        if (!hasRowField && !hasColumnField) {
            throw invalid();
        }
    }

    /** 双列两个指标必须使用同一类单位：金额或比例。 */
    private static void validateCompositionUnitKinds(Set<String> present, JsonNode units) {
        if (!present.contains("corporate") || !present.contains("retail")) {
            return;
        }
        String corporate = units.path("corporate").asText(null);
        String retail = units.path("retail").asText(null);
        String corporateKind = compositionUnitKind(corporate);
        String retailKind = compositionUnitKind(retail);
        if (corporateKind == null || retailKind == null || !corporateKind.equals(retailKind)) {
            throw invalid();
        }
    }

    private static String compositionUnitKind(String unit) {
        if (unit == null) {
            return null;
        }
        if (Set.of("YUAN", "TEN_THOUSAND", "HUNDRED_MILLION").contains(unit)) {
            return "amount";
        }
        if (Set.of("PERCENT", "RATIO").contains(unit)) {
            return "ratio";
        }
        return null;
    }

    private static boolean isCompositionColumns(JsonNode bind) {
        JsonNode fields = bind == null ? null : bind.path("fields");
        return fields != null && fields.isObject()
                && (fields.has("corporate") || fields.has("retail"));
    }

    private static boolean isInstitutionWideTable(RptScreenDatasource datasource) {
        if (!"WIDE_TABLE".equalsIgnoreCase(datasource.getSourceKind())) {
            return false;
        }
        JsonNode config = readConfigForSchema(datasource.getConfigJson());
        return "ORG_INDEX_RESULT".equalsIgnoreCase(config.path("table").asText());
    }

    private static boolean isOrgSubjectAggregation(RptScreenDatasource datasource) {
        if (datasource == null || !"WIDE_TABLE".equalsIgnoreCase(datasource.getSourceKind())) {
            return false;
        }
        JsonNode config = readConfigForSchema(datasource.getConfigJson());
        return "ORG_INDEX_RESULT".equals(config.path("table").asText())
                && "org_code".equals(config.path("subjectCol").asText())
                && config.path("aggregation").isObject()
                && "SUBJECT".equals(config.path("aggregation").path("groupBy").asText());
    }

    private static Set<String> union(Set<String> first, Set<String> second) {
        Set<String> result = new LinkedHashSet<>(first);
        result.addAll(second);
        return Set.copyOf(result);
    }

    private static long parsePositiveLong(String key) {
        if (key == null || key.isBlank() || !key.chars().allMatch(Character::isDigit)) {
            throw untrusted();
        }
        try {
            long value = Long.parseLong(key);
            if (value <= 0) {
                throw untrusted();
            }
            return value;
        } catch (NumberFormatException ex) {
            throw untrusted();
        }
    }

    private static boolean isPositiveIntegral(JsonNode node) {
        return node != null && node.isIntegralNumber() && node.canConvertToLong() && node.longValue() > 0;
    }

    private static RptException invalid() {
        return new RptException(RptErrorCode.SCREEN_LAYOUT_INVALID);
    }

    private static RptException untrusted() {
        return new RptException(RptErrorCode.SCREEN_PUBLISHED_SNAPSHOT_UNTRUSTED);
    }

    private static Map<String, Set<String>> fieldKeys() {
        Map<String, Set<String>> result = new HashMap<>();
        result.put("deposit", Set.of("value", "change", "date"));
        result.put("depositIncrease", Set.of("value", "change", "date"));
        result.put("depositAverage", Set.of("value", "change", "date"));
        result.put("loan", Set.of("value", "change", "date"));
        result.put("customers", Set.of("value", "change", "date"));
        result.put("revenue", Set.of("value", "change", "date"));
        result.put("rate", Set.of("value", "change", "date"));
        result.put("trend", Set.of("date", "deposit", "loan", "depositIncrease", "customers", "rate"));
        result.put("branchTrend", Set.of("date", "deposit", "loan", "customers", "rate"));
        result.put("composition", Set.of("name", "value", "corporate", "retail"));
        result.put("ranking", Set.of("orgCode", "name", "value", "increase", "average", "change"));
        result.put("attention", Set.of("orgCode", "label", "count"));
        result.put("branches", Set.of("orgCode", "orgName", "cityCode", "cityName", "ownerOperatingOrgCode",
                "parentOrgCode",
                "lng", "lat", "coordSys", "located", "deposit", "loan", "customers", "target", "rate"));
        result.put("citySummary", Set.of("cityCode", "orgCode", "cityName", "deposit", "loan", "customers", "revenue", "rate"));
        result.put("retailAum", Set.of("value", "change", "date"));
        result.put("retailDeposit", Set.of("value", "change", "date"));
        result.put("retailDepositAverage", Set.of("value", "change", "date"));
        result.put("retailRevenue", Set.of("value", "change", "date"));
        result.put("retailLoan", Set.of("value", "change", "date"));
        result.put("retailValueCustomers", Set.of("value", "change", "date"));
        result.put("retailNplRate", Set.of("value", "change", "date"));
        result.put("retailTrend", Set.of("date", "aum", "deposit", "depositAverage", "revenue",
                "loan", "valueCustomers", "nplRate"));
        result.put("retailSegments", Set.of("name", "customers", "aum"));
        result.put("retailRanking", Set.of("orgCode", "name", "aum", "increase", "rate", "nplRate"));
        result.put("retailAttention", Set.of("label", "count", "owner", "deadline"));
        result.put("retailTargets", Set.of("name", "actual", "target"));
        return Map.copyOf(result);
    }

    private static Map<String, Set<String>> requiredFields() {
        Map<String, Set<String>> result = new HashMap<>();
        result.put("deposit", Set.of("value"));
        result.put("depositIncrease", Set.of("value"));
        result.put("depositAverage", Set.of("value"));
        result.put("loan", Set.of("value"));
        result.put("customers", Set.of("value"));
        result.put("revenue", Set.of("value"));
        result.put("rate", Set.of("value"));
        result.put("trend", Set.of("date"));
        result.put("branchTrend", Set.of("date"));
        result.put("composition", Set.of("name", "value"));
        result.put("ranking", Set.of("orgCode", "name", "value"));
        result.put("attention", Set.of("label", "count"));
        result.put("branches", Set.of("orgCode"));
        // cityCode is preferred, but an ORG_INDEX_RESULT SUBJECT binding may provide the
        // authorized orgCode and let the runtime resolve city identity from profiles.
        result.put("citySummary", Set.of());
        result.put("retailAum", Set.of("value"));
        result.put("retailDeposit", Set.of("value"));
        result.put("retailDepositAverage", Set.of("value"));
        result.put("retailRevenue", Set.of("value"));
        result.put("retailLoan", Set.of("value"));
        result.put("retailValueCustomers", Set.of("value"));
        result.put("retailNplRate", Set.of("value"));
        result.put("retailTrend", Set.of("date"));
        result.put("retailSegments", Set.of("name", "customers", "aum"));
        result.put("retailRanking", Set.of("orgCode", "name", "aum"));
        result.put("retailAttention", Set.of("label", "count"));
        result.put("retailTargets", Set.of("name", "actual", "target"));
        return Map.copyOf(result);
    }

    private static Set<String> fieldKeys(String template, String bindingKey) {
        slotsForTemplate(template);
        Set<String> fields = FIELD_KEYS.get(bindingKey);
        if (fields == null) {
            throw invalid();
        }
        return fields;
    }

    private static Set<String> requiredFields(String template, String bindingKey) {
        slotsForTemplate(template);
        Set<String> fields = REQUIRED_FIELDS.get(bindingKey);
        if (fields == null) {
            throw invalid();
        }
        return fields;
    }

    /**
     * Derive the stable output columns emitted by ScreenQueryEngine for one saved datasource.
     * The method intentionally does not inspect a live database or issue SQL.
     */
    private static Map<String, String> outputRoles(RptScreenDatasource datasource) {
        String sourceKind = datasource.getSourceKind() == null
                ? "" : datasource.getSourceKind().trim().toUpperCase(Locale.ROOT);
        JsonNode config = readConfigForSchema(datasource.getConfigJson());
        Map<String, String> roles = new LinkedHashMap<>();
        switch (sourceKind) {
            case "WIDE_TABLE" -> addWideTableColumns(roles, config);
            case "KPI_RESULT" -> {
                roles.put("data_date", "DIM");
                roles.put("KPI总分", "METRIC");
            }
            case "KPI_DETAIL" -> addKpiDetailColumns(roles, config);
            case "CUSTOM_SQL" -> addFieldMetaColumns(roles, config.path("fieldMeta"), true);
            default -> throw invalid();
        }
        // fieldMeta is metadata over the engine's output, rather than a way to invent a
        // column. A contradictory role would make a valid mapping ambiguous, so fail closed.
        if (!"CUSTOM_SQL".equals(sourceKind)) {
            addFieldMetaColumns(roles, config.path("fieldMeta"), false);
        }
        return roles;
    }

    private static void addWideTableColumns(Map<String, String> roles, JsonNode config) {
        String table = config.path("table").asText().trim().toUpperCase(Locale.ROOT);
        if (!WIDE_TABLES.contains(table)) {
            throw invalid();
        }
        JsonNode aggregation = config.path("aggregation");
        String groupBy = aggregation.isObject() ? aggregation.path("groupBy").asText() : "";
        if (!aggregation.isObject() || "DATE".equals(groupBy)) {
            roles.put("data_date", "DIM");
        } else if ("SUBJECT".equals(groupBy)) {
            String subject = wideSubjectColumn(table);
            if (subject == null) {
                throw invalid();
            }
            roles.put(subject, "DIM");
            if ("ORG_INDEX_RESULT".equals(table)) {
                roles.put("org_name", "DIM");
            }
        } else if (!"NONE".equals(groupBy)) {
            throw invalid();
        }
        JsonNode metrics = config.path("metrics");
        if (!metrics.isArray() || metrics.isEmpty()) {
            throw invalid();
        }
        for (JsonNode metric : metrics) {
            String alias = metric.path("metricName").asText();
            if (alias == null || alias.isBlank()) {
                alias = metric.path("metricCode").asText();
            }
            if (alias == null || alias.isBlank()) {
                throw invalid();
            }
            roles.put(alias.trim(), "METRIC");
        }
    }

    private static void addKpiDetailColumns(Map<String, String> roles, JsonNode config) {
        String mode = config.path("mode").asText();
        if ("SNAPSHOT".equals(mode)) {
            roles.put("metric_code", "DIM");
            roles.put("细项名称", "DIM");
            roles.put("目标值", "METRIC");
            roles.put("实际值", "METRIC");
            roles.put("权重", "METRIC");
            roles.put("得分", "METRIC");
            roles.put("完成率", "METRIC");
            roles.put("缺口", "METRIC");
            return;
        }
        if ("TREND".equals(mode)) {
            roles.put("data_date", "DIM");
            JsonNode metrics = config.path("metrics");
            if (!metrics.isArray() || metrics.isEmpty()) {
                throw invalid();
            }
            for (JsonNode metric : metrics) {
                String alias = metric.path("metricName").asText();
                if (alias == null || alias.isBlank()) {
                    alias = metric.path("metricCode").asText();
                }
                if (alias == null || alias.isBlank()) {
                    throw invalid();
                }
                roles.put(alias.trim(), "METRIC");
            }
            return;
        }
        throw invalid();
    }

    private static void addFieldMetaColumns(Map<String, String> roles, JsonNode fieldMeta,
                                            boolean allowNewColumns) {
        if (fieldMeta == null || fieldMeta.isMissingNode() || fieldMeta.isNull()) {
            if (allowNewColumns) {
                throw invalid();
            }
            return;
        }
        if (!fieldMeta.isArray()) {
            throw invalid();
        }
        for (JsonNode item : fieldMeta) {
            String column = item.path("col").asText();
            String role = item.path("role").asText();
            if (column == null || column.isBlank() || (!"DIM".equals(role) && !"METRIC".equals(role))) {
                throw invalid();
            }
            column = column.trim();
            if (allowNewColumns) {
                String previous = roles.putIfAbsent(column, role);
                if (previous != null && !previous.equals(role)) {
                    throw invalid();
                }
            } else if (roles.containsKey(column) && !roles.get(column).equals(role)) {
                throw invalid();
            }
        }
    }

    private static String wideSubjectColumn(String table) {
        return switch (table) {
            case "EMP_INDEX_RESULT" -> "emp_id";
            case "ORG_INDEX_RESULT" -> "org_code";
            case "CUST_INDEX_RESULT" -> "cust_no";
            default -> null;
        };
    }

    private static JsonNode readConfigForSchema(String json) {
        try {
            JsonNode config = MAPPER.readTree(json == null || json.isBlank() ? "{}" : json);
            if (config == null || !config.isObject()) {
                throw new IllegalArgumentException("数据源配置必须为 object");
            }
            return config;
        } catch (Exception ex) {
            throw invalid();
        }
    }

    private static Set<String> unitKinds(String slot, String field) {
        Set<String> amount = Set.of("YUAN", "TEN_THOUSAND", "HUNDRED_MILLION");
        Set<String> count = Set.of("COUNT", "TEN_THOUSAND_COUNT");
        Set<String> ratio = Set.of("PERCENT", "RATIO");
        return switch (slot) {
            case "deposit", "depositIncrease", "depositAverage", "loan", "revenue" -> switch (field) {
                case "value" -> amount;
                case "change" -> ratio;
                default -> Set.of();
            };
            case "customers" -> switch (field) {
                case "value" -> count;
                case "change" -> ratio;
                default -> Set.of();
            };
            case "rate" -> switch (field) {
                case "value", "change" -> ratio;
                default -> Set.of();
            };
            case "trend" -> switch (field) {
                case "deposit", "loan", "depositIncrease" -> amount;
                case "customers" -> count;
                case "rate" -> ratio;
                default -> Set.of();
            };
            case "branchTrend" -> switch (field) {
                case "deposit", "loan" -> amount;
                case "customers" -> count;
                case "rate" -> ratio;
                default -> Set.of();
            };
            case "composition" -> switch (field) {
                case "value", "corporate", "retail" ->
                        Set.of("YUAN", "TEN_THOUSAND", "HUNDRED_MILLION", "PERCENT", "RATIO");
                default -> Set.of();
            };
            case "ranking" -> switch (field) {
                case "value", "increase", "average" -> amount;
                case "change" -> ratio;
                default -> Set.of();
            };
            case "attention" -> "count".equals(field) ? count : Set.of();
            case "branches" -> switch (field) {
                case "deposit", "loan", "target" -> amount;
                case "customers" -> count;
                case "rate" -> ratio;
                default -> Set.of();
            };
            case "citySummary" -> switch (field) {
                case "deposit", "loan", "revenue" -> amount;
                case "customers" -> count;
                case "rate" -> ratio;
                default -> Set.of();
            };
            case "retailAum", "retailDeposit", "retailDepositAverage", "retailRevenue", "retailLoan" -> switch (field) {
                case "value" -> amount;
                case "change" -> ratio;
                default -> Set.of();
            };
            case "retailValueCustomers" -> switch (field) {
                case "value" -> count;
                case "change" -> ratio;
                default -> Set.of();
            };
            case "retailNplRate" -> switch (field) {
                case "value", "change" -> ratio;
                default -> Set.of();
            };
            case "retailTrend" -> switch (field) {
                case "aum", "deposit", "depositAverage", "revenue", "loan" -> amount;
                case "valueCustomers" -> count;
                case "nplRate" -> ratio;
                default -> Set.of();
            };
            case "retailSegments" -> switch (field) {
                case "customers" -> count;
                case "aum" -> amount;
                default -> Set.of();
            };
            case "retailRanking" -> switch (field) {
                case "aum", "increase" -> amount;
                case "rate", "nplRate" -> ratio;
                default -> Set.of();
            };
            case "retailAttention" -> "count".equals(field) ? Set.of("COUNT") : Set.of();
            case "retailTargets" -> switch (field) {
                case "actual", "target" -> amount;
                default -> Set.of();
            };
            default -> Set.of();
        };
    }
}
