package com.bank.branch.platform.report.service.screen;

import com.bank.branch.platform.report.entity.RptScreen;
import com.bank.branch.platform.report.entity.RptScreenBlock;
import com.bank.branch.platform.report.entity.RptScreenPublishLog;
import com.bank.branch.platform.report.support.PublishedScreenPackageValidator;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

/**
 * 请求内的大屏数据源引用索引。
 *
 * <p>数据源列表需要同时返回草稿和已发布引用屏编码。若逐个数据源扫描屏、区块和归档，
 * 远程读取会把相同 JSON 重复解析很多次。本索引把三类输入一次性解析为
 * {@code datasourceId -> screenCode}，不持有跨请求状态，也不做权限判断。</p>
 */
public final class ScreenDatasourceReferenceIndex {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    private ScreenDatasourceReferenceIndex() {
    }

    /**
     * 构建一次请求使用的引用快照。
     *
     * <p>当前发布包和每个归档包都经过同一个严格发布包校验器。无法证明包的绑定身份时，
     * 对该屏的任意非空数据源 ID 采用 fail-close 引用；合法的空组件/空快照包则没有引用。
     * {@code archiveReadFailed} 表示归档整体读取失败，此时所有已知屏都按保守引用处理。</p>
     */
    public static Snapshot build(List<RptScreen> screens,
                                 List<RptScreenBlock> blocks,
                                 List<RptScreenPublishLog> archives,
                                 boolean archiveReadFailed) {
        Map<Long, Set<String>> screenCodesById = indexScreenCodes(screens);
        Map<Long, Set<String>> draftByDatasource = new HashMap<>();
        Map<Long, Set<String>> publishedByDatasource = new HashMap<>();
        Set<String> conservativePublishedCodes = new HashSet<>();

        indexDraftReferences(blocks, screenCodesById, draftByDatasource);
        indexCurrentPublishedReferences(screens, screenCodesById, publishedByDatasource,
                conservativePublishedCodes);
        if (archiveReadFailed) {
            conservativePublishedCodes.addAll(allScreenCodes(screenCodesById));
        } else {
            indexArchiveReferences(archives, screenCodesById, publishedByDatasource,
                    conservativePublishedCodes);
        }

        return new Snapshot(freeze(draftByDatasource), freeze(publishedByDatasource),
                sortedUnmodifiable(conservativePublishedCodes));
    }

    private static Map<Long, Set<String>> indexScreenCodes(List<RptScreen> screens) {
        Map<Long, Set<String>> result = new HashMap<>();
        if (screens == null) {
            return result;
        }
        for (RptScreen screen : screens) {
            if (screen == null || screen.getId() == null) {
                continue;
            }
            String code = normalizeCode(screen.getScreenCode());
            if (code != null) {
                result.computeIfAbsent(screen.getId(), ignored -> new HashSet<>()).add(code);
            }
        }
        return result;
    }

    private static void indexDraftReferences(List<RptScreenBlock> blocks,
                                             Map<Long, Set<String>> screenCodesById,
                                             Map<Long, Set<String>> draftByDatasource) {
        if (blocks == null || screenCodesById.isEmpty()) {
            return;
        }
        for (RptScreenBlock block : blocks) {
            if (block == null || block.getScreenId() == null) {
                continue;
            }
            Set<String> screenCodes = screenCodesById.get(block.getScreenId());
            if (screenCodes == null || screenCodes.isEmpty()) {
                continue;
            }
            Long datasourceId = readDraftDatasourceId(block.getBindJson());
            if (datasourceId != null) {
                addReferences(draftByDatasource, datasourceId, screenCodes);
            }
        }
    }

    private static void indexCurrentPublishedReferences(List<RptScreen> screens,
                                                        Map<Long, Set<String>> screenCodesById,
                                                        Map<Long, Set<String>> publishedByDatasource,
                                                        Set<String> conservativePublishedCodes) {
        if (screens == null || screenCodesById.isEmpty()) {
            return;
        }
        for (RptScreen screen : screens) {
            if (screen == null || screen.getId() == null) {
                continue;
            }
            Set<String> screenCodes = screenCodesById.get(screen.getId());
            if (screenCodes == null || screenCodes.isEmpty()) {
                continue;
            }
            PublishedPackage packageResult = parsePublishedPackage(screen.getCanvasPublishedJson());
            if (!packageResult.trusted()) {
                conservativePublishedCodes.addAll(screenCodes);
            } else {
                addReferences(publishedByDatasource, packageResult.datasourceIds(), screenCodes);
            }
        }
    }

    private static void indexArchiveReferences(List<RptScreenPublishLog> archives,
                                               Map<Long, Set<String>> screenCodesById,
                                               Map<Long, Set<String>> publishedByDatasource,
                                               Set<String> conservativePublishedCodes) {
        if (archives == null || archives.isEmpty() || screenCodesById.isEmpty()) {
            return;
        }
        for (RptScreenPublishLog archive : archives) {
            if (archive == null || archive.getScreenId() == null) {
                continue;
            }
            Set<String> screenCodes = screenCodesById.get(archive.getScreenId());
            // 归档必须属于本次屏列表；孤立归档不能凭自身内容制造屏编码引用。
            if (screenCodes == null || screenCodes.isEmpty()) {
                continue;
            }
            PublishedPackage packageResult = parsePublishedPackage(archive.getSnapshotJson());
            if (!packageResult.trusted()) {
                conservativePublishedCodes.addAll(screenCodes);
            } else {
                addReferences(publishedByDatasource, packageResult.datasourceIds(), screenCodes);
            }
        }
    }

    private static Long readDraftDatasourceId(String bindJson) {
        try {
            JsonNode root = OBJECT_MAPPER.readTree(bindJson == null ? "{}" : bindJson);
            JsonNode datasourceId = root == null ? null : root.path("dsId");
            // 草稿沿用服务现有 readDsId 的 JSON number 身份约束，同时拒绝小数截断。
            if (datasourceId == null || !datasourceId.isIntegralNumber()
                    || !datasourceId.canConvertToLong() || datasourceId.longValue() <= 0) {
                return null;
            }
            return datasourceId.longValue();
        } catch (Exception ignored) {
            return null;
        }
    }

    private static PublishedPackage parsePublishedPackage(String publishedJson) {
        try {
            JsonNode root = PublishedScreenPackageValidator.read(publishedJson);
            Map<Long, JsonNode> bindings = PublishedScreenPackageValidator.requireTrustedBindings(root);
            Set<Long> datasourceIds = new HashSet<>();
            for (JsonNode snapshot : bindings.values()) {
                JsonNode datasourceId = snapshot.path("bind").path("dsId");
                // requireTrustedBindings 已验证 integral、可转 Long 且为正数；这里只做索引投影。
                datasourceIds.add(datasourceId.longValue());
            }
            return new PublishedPackage(datasourceIds, true);
        } catch (Exception ignored) {
            return new PublishedPackage(Set.of(), false);
        }
    }

    private static void addReferences(Map<Long, Set<String>> byDatasource,
                                      Long datasourceId,
                                      Set<String> screenCodes) {
        byDatasource.computeIfAbsent(datasourceId, ignored -> new HashSet<>()).addAll(screenCodes);
    }

    private static void addReferences(Map<Long, Set<String>> byDatasource,
                                      Set<Long> datasourceIds,
                                      Set<String> screenCodes) {
        for (Long datasourceId : datasourceIds) {
            if (datasourceId != null) {
                addReferences(byDatasource, datasourceId, screenCodes);
            }
        }
    }

    private static Set<String> allScreenCodes(Map<Long, Set<String>> screenCodesById) {
        Set<String> result = new HashSet<>();
        for (Set<String> codes : screenCodesById.values()) {
            result.addAll(codes);
        }
        return result;
    }

    private static String normalizeCode(String code) {
        if (code == null) {
            return null;
        }
        String normalized = code.trim();
        return normalized.isEmpty() ? null : normalized;
    }

    private static Map<Long, List<String>> freeze(Map<Long, Set<String>> source) {
        Map<Long, List<String>> result = new HashMap<>();
        for (Map.Entry<Long, Set<String>> entry : source.entrySet()) {
            result.put(entry.getKey(), sortedUnmodifiable(entry.getValue()));
        }
        return Collections.unmodifiableMap(result);
    }

    private static List<String> sortedUnmodifiable(Set<String> values) {
        if (values == null || values.isEmpty()) {
            return List.of();
        }
        return Collections.unmodifiableList(new ArrayList<>(new TreeSet<>(values)));
    }

    private record PublishedPackage(Set<Long> datasourceIds, boolean trusted) {
    }

    /** 一次请求内只读的引用查询结果。 */
    public static final class Snapshot {

        private final Map<Long, List<String>> draftByDatasource;
        private final Map<Long, List<String>> publishedByDatasource;
        private final List<String> conservativePublishedCodes;

        private Snapshot(Map<Long, List<String>> draftByDatasource,
                         Map<Long, List<String>> publishedByDatasource,
                         List<String> conservativePublishedCodes) {
            this.draftByDatasource = draftByDatasource;
            this.publishedByDatasource = publishedByDatasource;
            this.conservativePublishedCodes = conservativePublishedCodes;
        }

        /** 返回按编码排序、去重后的草稿引用屏编码。 */
        public List<String> draftCodes(Long datasourceId) {
            if (datasourceId == null) {
                return List.of();
            }
            return draftByDatasource.getOrDefault(datasourceId, List.of());
        }

        /** 返回按编码排序、去重后的当前/归档发布引用屏编码。 */
        public List<String> publishedCodes(Long datasourceId) {
            if (datasourceId == null) {
                return List.of();
            }
            List<String> exact = publishedByDatasource.get(datasourceId);
            if (conservativePublishedCodes.isEmpty()) {
                return exact == null ? List.of() : exact;
            }
            TreeSet<String> merged = new TreeSet<>(conservativePublishedCodes);
            if (exact != null) {
                merged.addAll(exact);
            }
            return Collections.unmodifiableList(new ArrayList<>(merged));
        }
    }
}
