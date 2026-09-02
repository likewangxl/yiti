package com.bank.branch.platform.config;

import org.junit.jupiter.api.Test;
import org.springframework.boot.env.YamlPropertySourceLoader;
import org.springframework.core.env.PropertySource;
import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.Resource;

import java.io.IOException;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 主配置文件的层级契约，防止新增顶层配置时误吞并相邻的 platform 配置。
 */
class ApplicationYamlStructureTest {

    private static final String APPLICATION_YML = "application.yml";

    @Test
    void platformAndMarketingPropertiesRemainInTheirOwnNamespaces() throws IOException {
        Resource resource = new ClassPathResource(APPLICATION_YML);
        assertThat(resource.exists()).isTrue();

        List<PropertySource<?>> propertySources =
                new YamlPropertySourceLoader().load(APPLICATION_YML, resource);
        assertThat(propertySources).hasSize(1);
        PropertySource<?> properties = propertySources.get(0);

        assertThat(properties.getProperty("platform.method-timing-error-ms")).isEqualTo(5000);
        assertThat(properties.getProperty("platform.soap.netty.port")).isEqualTo(30523);
        assertThat(properties.getProperty("marketing.lead-import.local-dir"))
                .isEqualTo("${MARKETING_LEAD_IMPORT_LOCAL_DIR:file-storage/marketing-lead-import}");
        assertThat(properties.getProperty("marketing.method-timing-error-ms")).isNull();
        assertThat(properties.getProperty("marketing.soap.netty.port")).isNull();
    }
}
