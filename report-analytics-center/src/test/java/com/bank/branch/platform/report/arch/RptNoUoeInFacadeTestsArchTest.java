package com.bank.branch.platform.report.arch;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 架构守护：facade 层测试资产不得残留 UOE 断言或 delivered message 字面量（对照 perf R5.3 守护）.
 *
 * <p><b>背景</b>：V1 定型期 facade 测试里曾有大量 {@code xxx_throwsUOE} 断言
 * （固化"V1.x delivered"占位行为）。每个阶段实际交付方法时，对应的 UOE 断言必须同步
 * 替换为行为断言，避免契约交付状态与测试代码不同步.
 *
 * <p><b>检查方式</b>：对 {@code src/test/java/.../facade/*.java} 扫描，不允许出现：
 * <ul>
 *   <li>{@code UnsupportedOperationException}（直接匹配）</li>
 *   <li>{@code "V1.2 delivered"}（message 字面量，带引号）</li>
 * </ul>
 *
 * <p>与 {@link RptNoV11UOEArchTest} 对称：后者守护生产源码 facade/*.java 不得残留
 * {@code "V1.1 delivered"}，本测试守护测试层不得残留 UOE 断言.
 *
 * <p>M0 阶段 facade 测试子包尚未有任何 *.java 文件，walk 空目录即 vacuously pass；
 * M1+ 接入真 Facade 测试时生效.
 */
class RptNoUoeInFacadeTestsArchTest {

    private static final Path FACADE_TEST_DIR = Paths.get(
            "src/test/java/com/bank/branch/platform/report/facade");

    @Test
    void noUoeAssertions_inFacadeTests() throws IOException {
        if (!Files.exists(FACADE_TEST_DIR)) {
            // M0 阶段 facade 测试目录尚未创建；vacuously pass
            return;
        }
        List<String> offenders = new ArrayList<>();
        try (Stream<Path> files = Files.walk(FACADE_TEST_DIR)) {
            files.filter(p -> p.toString().endsWith(".java"))
                    .forEach(p -> {
                        try {
                            String content = Files.readString(p);
                            if (content.contains("UnsupportedOperationException")
                                    || content.contains("\"V1.2 delivered\"")) {
                                offenders.add(p.toString());
                            }
                        } catch (IOException e) {
                            throw new RuntimeException(e);
                        }
                    });
        }
        assertThat(offenders)
                .as("facade 测试层不得再残留 UnsupportedOperationException 或 \"V1.2 delivered\""
                        + " 字面量；命中文件：%s", offenders)
                .isEmpty();
    }
}
