package com.bank.branch.platform.report.arch;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 架构守护：facade 层不得残留"占位 UOE"字面量（对照 perf NoV11UOEArchTest）.
 *
 * <p><b>背景</b>：report-analytics-center V1.0 定型契约时，未实现的 facade 方法
 * 曾使用 {@code new UnsupportedOperationException("V1.1 delivered")} 样板占位。
 * 随着 V1 分阶段交付，每个阶段结束后相应 UOE 字面量应当清除或向下一版本推进，
 * 避免消费方通过 message 误判"该方法已经到货"。
 *
 * <p><b>检查方式</b>：文件扫描（不走反射 / ArchUnit），对 facade/*.java 源文件做字符串 grep.
 * 测试运行时 cwd 是模块目录（{@code report-analytics-center/}），因此
 * {@code Paths.get("src/main/java/...")} 能正确定位源码。
 *
 * <p>M0 阶段 facade 子包尚未有任何 *.java 文件，walk 空目录即 vacuously pass；
 * M1+ 接入真 Facade 时生效.
 */
class RptNoV11UOEArchTest {

    private static final Path FACADE_DIR = Paths.get(
            "src/main/java/com/bank/branch/platform/report/facade");

    @Test
    void noV11DeliveredUoeMessage_inFacade() throws IOException {
        if (!Files.exists(FACADE_DIR)) {
            // M0 阶段 facade 目录尚未创建；vacuously pass
            return;
        }
        try (Stream<Path> files = Files.walk(FACADE_DIR)) {
            files.filter(p -> p.toString().endsWith(".java"))
                    .forEach(p -> {
                        try {
                            String content = Files.readString(p);
                            assertThat(content)
                                    .as("文件 %s 残留 V1.1 UOE 占位，应改为最新迭代的 delivered 标记", p)
                                    .doesNotContain("\"V1.1 delivered\"");
                        } catch (IOException e) {
                            throw new RuntimeException(e);
                        }
                    });
        }
    }
}
