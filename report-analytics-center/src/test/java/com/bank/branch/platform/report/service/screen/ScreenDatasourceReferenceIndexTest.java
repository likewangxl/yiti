package com.bank.branch.platform.report.service.screen;

import com.bank.branch.platform.report.entity.RptScreen;
import com.bank.branch.platform.report.entity.RptScreenBlock;
import com.bank.branch.platform.report.entity.RptScreenPublishLog;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * ScreenDatasourceReferenceIndex 纯内存单测。
 *
 * <p>这些用例刻意不启动 Spring，也不连接数据库；索引必须在一次请求中完成所有包解析，
 * 并保持 ScreenDatasourceServiceImpl 原有的 fail-close 引用语义。</p>
 */
class ScreenDatasourceReferenceIndexTest {

    @Test
    void build_indexesDraftAndPublishedReferencesAcrossDatasourcesOnce() {
        RptScreen alpha = screen(1L, " Z_ALPHA ", publishedPackage(11L, 10L, 12L, 20L));
        RptScreen beta = screen(2L, "BETA", publishedPackage(21L, 10L));

        RptScreenBlock alphaDs10 = block(1L, "{\"dsId\":10}");
        RptScreenBlock alphaDs20 = block(1L, "{\"dsId\":20}");
        RptScreenBlock betaDs10 = block(2L, "{\"dsId\":10}");
        RptScreenBlock unknownScreen = block(999L, "{\"dsId\":10}");
        RptScreenPublishLog alphaArchive = archive(1L, publishedPackage(31L, 30L));
        RptScreenPublishLog unknownArchive = archive(999L, publishedPackage(41L, 40L));

        ScreenDatasourceReferenceIndex.Snapshot snapshot = ScreenDatasourceReferenceIndex.build(
                List.of(alpha, beta),
                List.of(alphaDs10, alphaDs20, betaDs10, unknownScreen),
                List.of(alphaArchive, unknownArchive),
                false);

        assertThat(snapshot.draftCodes(10L)).containsExactly("BETA", "Z_ALPHA");
        assertThat(snapshot.draftCodes(20L)).containsExactly("Z_ALPHA");
        assertThat(snapshot.draftCodes(30L)).isEmpty();
        assertThat(snapshot.publishedCodes(10L)).containsExactly("BETA", "Z_ALPHA");
        assertThat(snapshot.publishedCodes(20L)).containsExactly("Z_ALPHA");
        assertThat(snapshot.publishedCodes(30L)).containsExactly("Z_ALPHA");
        assertThat(snapshot.publishedCodes(40L)).isEmpty();
    }

    @Test
    void build_marksMalformedCurrentOrArchivePackagesAsConservativeForAnyDatasource() {
        RptScreen malformedCurrent = screen(1L, " CURRENT ", "{broken");
        RptScreen validCurrent = screen(2L, "VALID", publishedPackage(21L, 7L));
        RptScreen validCurrentWithMalformedArchive = screen(3L, "ARCHIVE", emptyPublishedPackage());

        RptScreenPublishLog malformedArchive = archive(3L, "{\"components\":[{\"component\":\"ChartWidget\",\"blockId\":31}]}");
        RptScreenPublishLog orphanMalformedArchive = archive(999L, "{broken");

        ScreenDatasourceReferenceIndex.Snapshot snapshot = ScreenDatasourceReferenceIndex.build(
                List.of(malformedCurrent, validCurrent, validCurrentWithMalformedArchive),
                List.of(),
                List.of(malformedArchive, orphanMalformedArchive),
                false);

        assertThat(snapshot.publishedCodes(7L)).containsExactly("ARCHIVE", "CURRENT", "VALID");
        assertThat(snapshot.publishedCodes(9999L)).containsExactly("ARCHIVE", "CURRENT");
        assertThat(snapshot.publishedCodes(123456L)).containsExactly("ARCHIVE", "CURRENT");
    }

    @Test
    void build_archiveReadFailureConservativelyReferencesEveryKnownScreen() {
        RptScreen alpha = screen(1L, " z-alpha ", emptyPublishedPackage());
        RptScreen beta = screen(2L, "BETA", publishedPackage(21L, 7L));
        RptScreen blankCode = screen(3L, "   ", emptyPublishedPackage());

        ScreenDatasourceReferenceIndex.Snapshot snapshot = ScreenDatasourceReferenceIndex.build(
                List.of(alpha, beta, blankCode), List.of(), null, true);

        assertThat(snapshot.publishedCodes(7L)).containsExactly("BETA", "z-alpha");
        assertThat(snapshot.publishedCodes(999L)).containsExactly("BETA", "z-alpha");
    }

    @Test
    void build_acceptsOnlyStrictDraftJsonNumbersAndKeepsLegalEmptyPackagesEmpty() {
        RptScreen screen = screen(1L, "DRAFT", emptyPublishedPackage());
        List<RptScreenBlock> blocks = List.of(
                block(1L, "{\"dsId\":10}"),
                block(1L, "{\"dsId\":\"10\"}"),
                block(1L, "{\"dsId\":10.0}"),
                block(1L, "{\"dsId\":null}"),
                block(1L, "{broken"));

        ScreenDatasourceReferenceIndex.Snapshot snapshot = ScreenDatasourceReferenceIndex.build(
                List.of(screen), blocks, List.of(), false);

        assertThat(snapshot.draftCodes(10L)).containsExactly("DRAFT");
        assertThat(snapshot.draftCodes(11L)).isEmpty();
        assertThat(snapshot.publishedCodes(10L)).isEmpty();
        assertThat(snapshot.draftCodes(null)).isEmpty();
        assertThat(snapshot.publishedCodes(null)).isEmpty();
    }

    @Test
    void build_keepsNullOrBlankPublishedPackagesConservativeButLegalEmptyPackageUnreferenced() {
        RptScreen nullPackage = screen(1L, "NULL_PACKAGE", null);
        RptScreen blankPackage = screen(2L, " BLANK_PACKAGE ", "   ");
        RptScreen legalEmptyPackage = screen(3L, "EMPTY_PACKAGE", emptyPublishedPackage());

        ScreenDatasourceReferenceIndex.Snapshot snapshot = ScreenDatasourceReferenceIndex.build(
                List.of(nullPackage, blankPackage, legalEmptyPackage), List.of(), null, false);

        assertThat(snapshot.publishedCodes(999L))
                .containsExactly("BLANK_PACKAGE", "NULL_PACKAGE");
        assertThat(snapshot.publishedCodes(1L))
                .containsExactly("BLANK_PACKAGE", "NULL_PACKAGE");
    }

    @Test
    void build_deduplicatesTrimmedCodesAndNullInputs() {
        RptScreen first = screen(1L, " DUP ", publishedPackage(11L, 10L));
        RptScreen second = screen(2L, "DUP", publishedPackage(21L, 10L));
        RptScreen noIdentity = screen(null, null, null);

        ScreenDatasourceReferenceIndex.Snapshot snapshot = ScreenDatasourceReferenceIndex.build(
                List.of(first, second, noIdentity),
                List.of(block(1L, "{\"dsId\":10}"), block(2L, "{\"dsId\":10}")),
                null,
                false);

        assertThat(snapshot.draftCodes(10L)).containsExactly("DUP");
        assertThat(snapshot.publishedCodes(10L)).containsExactly("DUP");
        assertThat(snapshot.draftCodes(null)).isEmpty();
        assertThat(snapshot.publishedCodes(null)).isEmpty();

        ScreenDatasourceReferenceIndex.Snapshot empty = ScreenDatasourceReferenceIndex.build(null, null, null, false);
        assertThat(empty.draftCodes(10L)).isEmpty();
        assertThat(empty.publishedCodes(10L)).isEmpty();
    }

    private static RptScreen screen(Long id, String code, String publishedJson) {
        RptScreen screen = new RptScreen();
        screen.setId(id);
        screen.setScreenCode(code);
        screen.setCanvasPublishedJson(publishedJson);
        return screen;
    }

    private static RptScreenBlock block(Long screenId, String bindJson) {
        RptScreenBlock block = new RptScreenBlock();
        block.setScreenId(screenId);
        block.setBindJson(bindJson);
        return block;
    }

    private static RptScreenPublishLog archive(Long screenId, String snapshotJson) {
        RptScreenPublishLog archive = new RptScreenPublishLog();
        archive.setScreenId(screenId);
        archive.setSnapshotJson(snapshotJson);
        return archive;
    }

    private static String emptyPublishedPackage() {
        return "{\"components\":[],\"bindSnapshots\":{}}";
    }

    private static String publishedPackage(long firstBlockId, long firstDsId, long... additionalDsIds) {
        StringBuilder components = new StringBuilder("[");
        StringBuilder snapshots = new StringBuilder("{");
        int total = additionalDsIds.length + 1;
        for (int i = 0; i < total; i++) {
            long blockId = firstBlockId + i;
            long datasourceId = i == 0 ? firstDsId : additionalDsIds[i - 1];
            if (i > 0) {
                components.append(",");
                snapshots.append(",");
            }
            components.append("{\"component\":\"ChartWidget\",\"blockId\":").append(blockId).append("}");
            snapshots.append("\"").append(blockId).append("\":{\"bind\":{\"dsId\":")
                    .append(datasourceId).append("}}");
        }
        components.append("]");
        snapshots.append("}");
        return "{\"components\":" + components + ",\"bindSnapshots\":" + snapshots + "}";
    }
}
