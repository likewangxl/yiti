package com.bank.branch.platform.performance.arch;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 架构守护：V1.1 已交付阶段结束后，facade 层不得残留 message 含 "V1.1 delivered" 的
 * {@link UnsupportedOperationException} 占位（Task P8.2）.
 *
 * <p><b>背景</b>：V1.0 定型契约时，所有未实现的方法统一抛
 * {@code new UnsupportedOperationException("V1.1 delivered")} 占位。V1.1 交付完成
 * （指标计算 + KPI + 导入 + 外部上报 + 回算）后，仍未实现的方法应当全部改为
 * {@code "V1.2 delivered"}，避免消费方通过 message 串误判 "这个方法 V1.1 已经到货"。
 *
 * <p><b>检查方式</b>：文件扫描（不走反射 / ArchUnit），对 facade/*.java 源文件做字符串 grep。
 * 测试运行时 cwd 是模块目录（{@code performance-engine-center/}），因此
 * {@code Paths.get("src/main/java/...")} 能正确定位源码。
 *
 * <p><b>对照</b>：V1.2 对外 UOE 占位允许继续存在（如 {@code MetricApi.getUserMetricCards}
 * 依赖 V1.2 的 KPI 方案绑定 + 同环比），消息应使用 {@code "V1.2 delivered"}。
 */
class NoV11UOEArchTest {

    private static final Path FACADE_DIR = Paths.get(
            "src/main/java/com/bank/branch/platform/performance/facade");

    @Test
    void noV11DeliveredUoeMessage_inFacade() throws IOException {
        try (Stream<Path> files = Files.walk(FACADE_DIR)) {
            files.filter(p -> p.toString().endsWith(".java"))
                    .forEach(p -> {
                        try {
                            String content = Files.readString(p);
                            assertThat(content)
                                    .as("文件 %s 残留 V1.1 UOE 占位，V1.1 交付后应改为 V1.2 delivered", p)
                                    .doesNotContain("\"V1.1 delivered\"");
                        } catch (IOException e) {
                            throw new RuntimeException(e);
                        }
                    });
        }
    }
}
