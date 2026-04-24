package com.bank.branch.platform.performance.arch;

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
 * 架构守护：V1.3 交付所有 UOE 占位后，facade 层测试资产不得再残留
 * {@code UnsupportedOperationException} 断言或 {@code "V1.2 delivered"} 字面量（Task R5.3）.
 *
 * <p><b>背景</b>：V1.1 P8 测试里曾有大量 {@code xxx_throwsUOE} 断言（固化 "V1.x delivered"
 * 占位行为），V1.3 Phase R2 已把 {@code triggerKpiCalc} / {@code batchQuerySnapshots} /
 * {@code getUserMetricCards} 等 5 个 UOE 方法真正实现并替换为行为断言（R2.1~R2.5）。
 *
 * <p>本架构测试守护：避免未来迭代误引入新的 UOE 占位测试，确保契约交付状态与测试代码同步。
 * 对 {@code src/test/java/.../facade/*.java} 扫描，不允许出现：
 * <ul>
 *   <li>{@code UnsupportedOperationException}（直接匹配）</li>
 *   <li>{@code "V1.2 delivered"}（message 字面量，带引号）</li>
 * </ul>
 *
 * <p>与 {@link NoV11UOEArchTest} 对称：后者守护源码 facade 层不得残留 {@code "V1.1 delivered"}，
 * 本测试守护测试层不得残留 V1.2 UOE 断言。
 */
class NoUoeInFacadeTestsArchTest {

    private static final Path FACADE_TEST_DIR = Paths.get(
            "src/test/java/com/bank/branch/platform/performance/facade");

    @Test
    void noUoeAssertions_inFacadeTests() throws IOException {
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
                .as("V1.3 R2 已交付所有 UOE，facade 测试层不得再残留 UnsupportedOperationException"
                        + " 或 \"V1.2 delivered\" 字面量；命中文件：%s", offenders)
                .isEmpty();
    }
}
