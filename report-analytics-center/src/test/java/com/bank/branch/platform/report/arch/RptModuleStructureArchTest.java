package com.bank.branch.platform.report.arch;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 模块结构守护测试（Task M0.1.1）.
 *
 * <p>验证 report-analytics-center 模块的核心声明（pom.xml + 关键依赖）已就位。
 * 测试运行时 cwd 是模块目录（{@code report-analytics-center/}），因此
 * {@code new File("pom.xml")} 能正确定位子模块 pom.
 */
class RptModuleStructureArchTest {

    private static final Path API_DIR = Paths.get(
            "src/main/java/com/bank/branch/platform/report/api");

    @Test
    void shouldHaveModulePomXml() {
        File pom = new File("pom.xml");
        assertThat(pom).exists().isFile();
    }

    @Test
    void shouldDeclareJsqlParserDependency() throws Exception {
        String pom = Files.readString(new File("pom.xml").toPath());
        assertThat(pom).contains("jsqlparser").contains("4.9");
    }

    @Test
    void shouldDeclareEasyExcelDependency() throws Exception {
        String pom = Files.readString(new File("pom.xml").toPath());
        assertThat(pom).contains("easyexcel");
    }

    /**
     * V1.0 红线守护：{@code api/} 包下不允许出现任何 {@code *Api.java}
     * （仅允许 {@code package-info.java} 占位）.
     *
     * <p>本规则对应 架构规约 声称的"只读支撑域不暴露 *Api 接口"硬约束。
     * 检查方式参考 {@link RptNoV11UOEArchTest}：文件扫描，不走 ArchUnit 反射，
     * 避免 ClassFileImporter 在空包/纯 package-info 包上的边界行为差异。
     *
     * <p>包不存在 / 仅含 package-info.java 时 vacuously pass；
     * V2+ 一旦新增 *Api.java 该测试立即报警。
     */
    @Test
    @DisplayName("V1.0 红线：api/ 包不应出现 *Api.java")
    void apiPackage_shouldNotContainApiInterfaces() throws IOException {
        if (!Files.exists(API_DIR)) {
            // api/ 目录不存在也合法（V1.0 历史阶段）
            return;
        }
        try (Stream<Path> files = Files.walk(API_DIR)) {
            List<String> apiFiles = files
                    .filter(p -> p.getFileName().toString().endsWith("Api.java"))
                    .map(p -> p.getFileName().toString())
                    .toList();
            assertThat(apiFiles)
                    .as("V1.0 红线：report-analytics-center 不暴露 *Api 接口；命中文件：%s", apiFiles)
                    .isEmpty();
        }
    }
}
