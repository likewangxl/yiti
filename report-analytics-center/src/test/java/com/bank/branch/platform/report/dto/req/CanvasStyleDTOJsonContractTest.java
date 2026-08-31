package com.bank.branch.platform.report.dto.req;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * CanvasStyleDTO 与设计器 normalizeCanvasStyle 发送的 JSON 字段契约测试。
 *
 * <p>该测试使用纯 ObjectMapper，不启动 Spring 或真实数据库，确保画布保存请求的
 * 反序列化边界独立可验证；未知字段仍应被拒绝，避免用忽略字段掩盖契约漂移。</p>
 */
class CanvasStyleDTOJsonContractTest {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void acceptsBackgroundTypeGradientAndNestedGradientImageFields() throws Exception {
        String json = """
                {
                  "schemaVersion": 1,
                  "designWidth": 1920,
                  "designHeight": 1080,
                  "background": "#050e2b",
                  "backgroundType": "gradient",
                  "bgGradient": {"from": "#111111", "to": "#222222", "angle": 45.5},
                  "bgImage": "https://example.test/background.png",
                  "adaptor": "keepProportion",
                  "themeOverride": {"accent": "#00e5ff"}
                }
                """;

        CanvasStyleDTO actual = objectMapper.readValue(json, CanvasStyleDTO.class);

        assertThat(actual.getBackgroundType()).isEqualTo("gradient");
        assertThat(actual.getBgGradient()).isNotNull();
        assertThat(actual.getBgGradient().getFrom()).isEqualTo("#111111");
        assertThat(actual.getBgGradient().getTo()).isEqualTo("#222222");
        assertThat(actual.getBgGradient().getAngle()).isEqualByComparingTo(new BigDecimal("45.5"));
        assertThat(actual.getBgImage()).isEqualTo("https://example.test/background.png");
    }

    @Test
    void doesNotIgnoreUnknownCanvasStyleFields() {
        assertThatThrownBy(() -> objectMapper.readValue(
                "{\"backgroundType\":\"solid\",\"unexpected\":true}", CanvasStyleDTO.class))
                .isInstanceOf(com.fasterxml.jackson.databind.exc.UnrecognizedPropertyException.class);
    }
}
