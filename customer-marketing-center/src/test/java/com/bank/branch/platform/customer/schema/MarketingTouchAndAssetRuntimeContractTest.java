package com.bank.branch.platform.customer.schema;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

class MarketingTouchAndAssetRuntimeContractTest {

    private static final Path MAIN = Path.of("src/main");

    @Test
    void runtimeCodeMustOnlyUseMarketingTouchTables() throws IOException {
        String runtime;
        try (Stream<Path> paths = Files.walk(MAIN)) {
            runtime = paths.filter(Files::isRegularFile)
                    .filter(path -> path.toString().endsWith(".java") || path.toString().endsWith(".xml"))
                    .map(this::read)
                    .reduce("", (left, right) -> left + "\n" + right);
        }

        assertThat(runtime).contains("MARKETING_TOUCH_TASK");
        assertThat(runtime).contains("MARKETING_TOUCH_WORKLOG");
        assertThat(runtime).contains("MARKETING_TOUCH_WORKLOG_PICTURE");
        assertThat(runtime).contains("MARKETING_TOUCH_WORKLOG_PARTICIPANT");
        assertThat(runtime).doesNotContain("FROM TOUCH_TASK", "UPDATE TOUCH_TASK", "@TableName(\"TOUCH_TASK\")");
        assertThat(runtime).doesNotContain("xa_touch_custom_worklogs", "xa_touch_custom_worklogs_picture_record");
    }

    @Test
    void assetProjectRuntimeMustUseFormalMarketingTablesAndRoute() {
        String controller = read(MAIN.resolve(
                "java/com/bank/branch/platform/customer/controller/AssetProjectController.java"));
        String apply = read(MAIN.resolve(
                "java/com/bank/branch/platform/customer/entity/AssetProjectApply.java"));
        String urgent = read(MAIN.resolve(
                "java/com/bank/branch/platform/customer/entity/AssetProjectUrgentApply.java"));

        assertThat(controller).contains("/api/marketing/asset-projects");
        assertThat(apply).contains("@TableName(\"MARKETING_ASSET_PROJECT_APPLY\")");
        assertThat(urgent).contains("@TableName(\"MARKETING_ASSET_PROJECT_URGENT_APPLY\")");
    }

    private String read(Path path) {
        try {
            return Files.readString(path);
        } catch (IOException e) {
            throw new IllegalStateException("读取契约文件失败: " + path, e);
        }
    }
}
