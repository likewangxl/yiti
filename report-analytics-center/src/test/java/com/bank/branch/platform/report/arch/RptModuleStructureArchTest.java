package com.bank.branch.platform.report.arch;

import org.junit.jupiter.api.Test;

import java.io.File;
import java.nio.file.Files;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 模块结构守护测试（Task M0.1.1）.
 *
 * <p>验证 report-analytics-center 模块的核心声明（pom.xml + 关键依赖）已就位。
 * 测试运行时 cwd 是模块目录（{@code report-analytics-center/}），因此
 * {@code new File("pom.xml")} 能正确定位子模块 pom.
 */
class RptModuleStructureArchTest {

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
}
