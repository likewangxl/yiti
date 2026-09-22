package com.bank.branch.platform.report.service.screen;

import com.bank.branch.platform.common.web.exception.BizException;
import com.bank.branch.platform.report.entity.RptScreenDatasource;
import com.bank.branch.platform.report.enums.RptErrorCode;
import com.bank.branch.platform.report.service.screen.presentation.PublishedDatasourceDefinition;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ScreenPresentationSourceSnapshotTest {

    private final ObjectMapper mapper = new ObjectMapper();

    @Test
    void publishedDefinitionKeepsCanonicalQuerySemanticsAfterCurrentConfigChanges() {
        RptScreenDatasource current = datasource("{\"b\":2,\"a\":1}");
        ObjectNode snapshot = mapper.createObjectNode();
        PublishedDatasourceDefinition.write(snapshot, current, mapper);
        String hash = snapshot.path("sourceDefinition").path("definitionHash").asText();

        current.setConfigJson("{\"a\":99}");
        RptScreenDatasource effective = PublishedDatasourceDefinition.effective(snapshot, current, mapper, true);

        assertThat(effective.getConfigJson()).isEqualTo("{\"a\":1,\"b\":2}");
        assertThat(effective.getStatus()).isEqualTo("ACTIVE");
        assertThat(hash).hasSize(64);
    }

    @Test
    void tamperingDefinitionOrDatasourceIdentityIsRejected() {
        RptScreenDatasource current = datasource("{\"a\":1}");
        ObjectNode snapshot = mapper.createObjectNode();
        PublishedDatasourceDefinition.write(snapshot, current, mapper);
        ((ObjectNode) snapshot.path("sourceDefinition").path("config")).put("a", 2);

        assertThatThrownBy(() -> PublishedDatasourceDefinition.effective(snapshot, current, mapper, true))
                .isInstanceOf(BizException.class)
                .hasFieldOrPropertyWithValue("code", RptErrorCode.SCREEN_PUBLISHED_SNAPSHOT_UNTRUSTED.getCode());
    }

    @Test
    void sensitiveConnectionKeysAreNeverWrittenToPublishedPackage() {
        RptScreenDatasource current = datasource("{\"table\":\"ORG_INDEX_RESULT\",\"password\":\"redacted\"}");
        ObjectNode snapshot = mapper.createObjectNode();

        assertThatThrownBy(() -> PublishedDatasourceDefinition.write(snapshot, current, mapper))
                .isInstanceOf(BizException.class)
                .hasFieldOrPropertyWithValue("code", RptErrorCode.SCREEN_DS_CONFIG_INVALID.getCode());
        assertThat(snapshot.has("sourceDefinition")).isFalse();
    }

    @Test
    void newDisplayRequiresDefinitionWhileLegacyPackageKeepsCurrentDatasource() {
        RptScreenDatasource current = datasource("{\"a\":1}");
        ObjectNode snapshot = mapper.createObjectNode();

        assertThat(PublishedDatasourceDefinition.effective(snapshot, current, mapper, false)).isSameAs(current);
        assertThatThrownBy(() -> PublishedDatasourceDefinition.effective(snapshot, current, mapper, true))
                .hasFieldOrPropertyWithValue("code", RptErrorCode.SCREEN_PUBLISHED_SNAPSHOT_UNTRUSTED.getCode());
    }

    @Test
    void equivalentJsonObjectOrderProducesSameDefinitionHash() {
        ObjectNode first = mapper.createObjectNode();
        ObjectNode second = mapper.createObjectNode();
        PublishedDatasourceDefinition.write(first, datasource("{\"b\":2,\"a\":1}"), mapper);
        PublishedDatasourceDefinition.write(second, datasource("{\"a\":1,\"b\":2}"), mapper);

        assertThat(first.path("sourceDefinition").path("definitionHash").asText())
                .isEqualTo(second.path("sourceDefinition").path("definitionHash").asText());
    }

    private RptScreenDatasource datasource(String config) {
        RptScreenDatasource datasource = new RptScreenDatasource();
        datasource.setId(12L);
        datasource.setDsCode("DS_TEST");
        datasource.setDsName("测试来源");
        datasource.setSourceKind("WIDE_TABLE");
        datasource.setDsType("SINGLE");
        datasource.setBizLine("COMMON");
        datasource.setConfigJson(config);
        datasource.setTimeParamJson("[\"LATEST\"]");
        datasource.setStatus("ACTIVE");
        return datasource;
    }
}
