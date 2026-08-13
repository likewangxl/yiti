package com.bank.branch.platform.report.support;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 已发布大屏包的局部严格身份校验。
 *
 * <p>此校验只用于不可变发布包/归档，不修改 Spring 全局 Jackson 行为。它开启重复字段检测，并要求
 * 每个 ChartWidget 的 JSON integral {@code blockId} 与 {@code bindSnapshots} 键双向一一对应，
 * 每个快照 {@code bind.dsId} 也必须是 integral JSON 数字。任何历史包不满足这些条件都不能被
 * 当前可变 block 行“修复”。</p>
 */
public final class PublishedScreenPackageValidator {

    private static final ObjectMapper STRICT_PACKAGE_MAPPER = new ObjectMapper()
            .enable(JsonParser.Feature.STRICT_DUPLICATE_DETECTION);

    private PublishedScreenPackageValidator() {
    }

    /** 以局部重复键检测读取发布 JSON。 */
    public static JsonNode read(String packageJson) throws IOException {
        JsonNode root = STRICT_PACKAGE_MAPPER.readTree(packageJson == null ? "{}" : packageJson);
        if (root == null || !root.isObject()) {
            throw new IllegalArgumentException("发布包根节点必须为对象");
        }
        return root;
    }

    /**
     * 校验发布包的不可变身份图，并返回按 blockId 排列的快照节点。缺少 components 等价于空组件树，
     * 但只要快照非空便会因双向集合不等而拒绝。
     */
    public static Map<Long, JsonNode> requireTrustedBindings(JsonNode root) {
        requireSchemaVersion(root.path("schemaVersion"));
        requireMapSchemaVersions(root.path("components"));

        Map<Long, Integer> chartCounts = new LinkedHashMap<>();
        collectChartBlockCounts(root.path("components"), chartCounts);
        if (chartCounts.values().stream().anyMatch(count -> count != 1)) {
            throw new IllegalArgumentException("发布包 ChartWidget blockId 重复");
        }

        JsonNode snapshots = root.path("bindSnapshots");
        if (!snapshots.isObject()) {
            throw new IllegalArgumentException("发布包缺少 bindSnapshots");
        }
        Map<Long, JsonNode> byBlockId = new LinkedHashMap<>();
        Iterator<Map.Entry<String, JsonNode>> entries = snapshots.fields();
        while (entries.hasNext()) {
            Map.Entry<String, JsonNode> entry = entries.next();
            long blockId = parseBlockIdKey(entry.getKey());
            JsonNode snapshot = entry.getValue();
            JsonNode dsId = snapshot.path("bind").path("dsId");
            if (!snapshot.isObject() || chartCounts.getOrDefault(blockId, 0) != 1
                    || byBlockId.putIfAbsent(blockId, snapshot) != null || !isPositiveIntegral(dsId)) {
                throw new IllegalArgumentException("发布包 bindSnapshots 身份不可信");
            }
        }
        if (!byBlockId.keySet().equals(chartCounts.keySet())) {
            throw new IllegalArgumentException("发布包组件与 bindSnapshots 不一致");
        }
        return byBlockId;
    }

    private static void collectChartBlockCounts(JsonNode components, Map<Long, Integer> counts) {
        if (components == null || components.isMissingNode() || components.isNull()) {
            return;
        }
        if (!components.isArray()) {
            throw new IllegalArgumentException("发布包 components 必须为数组");
        }
        for (JsonNode component : components) {
            if ("ChartWidget".equals(component.path("component").asText())) {
                JsonNode blockId = component.path("blockId");
                if (!isPositiveIntegral(blockId)) {
                    throw new IllegalArgumentException("发布包 blockId 必须为正整数");
                }
                counts.merge(blockId.longValue(), 1, Integer::sum);
            }
            collectChartBlockCounts(component.path("children"), counts);
        }
    }

    private static long parseBlockIdKey(String key) {
        if (key == null || !key.matches("[1-9]\\d*")) {
            throw new IllegalArgumentException("发布包快照键必须为正整数");
        }
        try {
            return Long.parseLong(key);
        } catch (NumberFormatException ex) {
            throw new IllegalArgumentException("发布包快照键超出 Long 范围", ex);
        }
    }

    private static boolean isPositiveIntegral(JsonNode node) {
        return node != null && node.isIntegralNumber() && node.canConvertToLong() && node.longValue() > 0;
    }

    private static void requireSchemaVersion(JsonNode version) {
        if (version == null || version.isMissingNode() || version.isNull()) {
            return;
        }
        if (!version.isIntegralNumber() || !version.canConvertToInt()
                || (version.intValue() != 1 && version.intValue() != 2)) {
            throw new IllegalArgumentException("发布包 schemaVersion 必须为整数 1 或 2");
        }
    }

    private static void requireMapSchemaVersions(JsonNode components) {
        if (components == null || components.isMissingNode() || components.isNull()) {
            return;
        }
        if (!components.isArray()) {
            throw new IllegalArgumentException("发布包 components 必须为数组");
        }
        for (JsonNode component : components) {
            if ("MapCenter".equals(component.path("component").asText())) {
                requireSchemaVersion(component.path("propValue").path("schemaVersion"));
            }
            requireMapSchemaVersions(component.path("children"));
        }
    }
}
