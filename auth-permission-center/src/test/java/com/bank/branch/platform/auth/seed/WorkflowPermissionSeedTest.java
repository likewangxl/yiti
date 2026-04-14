package com.bank.branch.platform.auth.seed;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * workflow 提交/撤回资源种子回灌回归测试。
 */
class WorkflowPermissionSeedTest {

    @Test
    void seedV1_shouldContainWorkflowSubmitAndCancelResources() throws IOException {
        String content = readRepoFile("docs", "schema", "seed-v1.sql");

        assertThat(content).contains("RES_WF_SUBMIT");
        assertThat(content).contains("/api/workflow/processes/submit");
        assertThat(content).contains("RES_WF_CANCEL");
        assertThat(content).contains("/api/workflow/processes/*/cancel");
        assertThat(content).contains("RR_RM_WF_SUBMIT");
        assertThat(content).contains("RR_RM_WF_CANCEL");
    }

    @Test
    void dataSql_shouldContainWorkflowSubmitAndCancelResources() throws IOException {
        String content = readRepoFile("data.sql");

        assertThat(content).contains("W_PROC_SUBMIT");
        assertThat(content).contains("/api/workflow/processes/submit");
        assertThat(content).contains("W_PROC_CANCEL");
        assertThat(content).contains("/api/workflow/processes/*/cancel");
        assertThat(content).contains("R_RM', 'W_PROC_SUBMIT'");
        assertThat(content).contains("R_RM', 'W_PROC_CANCEL'");
    }

    private String readRepoFile(String first, String... more) throws IOException {
        Path repoRoot = findRepoRoot(Path.of("").toAbsolutePath());
        return Files.readString(repoRoot.resolve(Path.of(first, more)), StandardCharsets.UTF_8);
    }

    private Path findRepoRoot(Path start) {
        Path current = start;
        while (current != null) {
            if (Files.exists(current.resolve("docs").resolve("schema").resolve("seed-v1.sql"))
                    && Files.exists(current.resolve("data.sql"))) {
                return current;
            }
            current = current.getParent();
        }
        throw new IllegalStateException("未找到仓库根目录");
    }
}
