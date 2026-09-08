package com.bank.branch.platform.auth.location;

import com.bank.branch.platform.auth.location.config.OrgLocationDisabledGeocodingConfiguration;
import com.bank.branch.platform.auth.location.config.OrgLocationDisabledStorageConfiguration;
import com.bank.branch.platform.auth.location.config.OrgLocationGeocodingConfiguration;
import com.bank.branch.platform.auth.location.config.OrgLocationStorageConfiguration;
import com.bank.branch.platform.auth.location.geocode.OrgLocationGeocoder;
import com.bank.branch.platform.auth.location.geocode.UnavailableOrgLocationGeocoder;
import com.bank.branch.platform.auth.location.persistence.OrgLocationMapper;
import com.bank.branch.platform.auth.location.persistence.OrgLocationStore;
import com.bank.branch.platform.auth.location.persistence.UnavailableOrgLocationStore;
import com.bank.branch.platform.auth.location.security.OrgLocationCandidateTokenService;
import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Bean;
import org.springframework.core.env.MapPropertySource;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.beans.factory.config.BeanDefinition;
import org.springframework.beans.factory.config.ConfigurableListableBeanFactory;
import org.mybatis.spring.mapper.MapperFactoryBean;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.LinkedHashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
/**
 * 位置能力条件装配的最小 Spring 容器测试。
 *
 * <p>只注册位置配置并通过 BeanDefinitionRegistry 检查开启存储后的 Mapper，
 * 不加载真实应用配置、不连接数据库、不发起地理编码外呼。</p>
 */
class OrgLocationConfigurationTest {

    @Test
    void defaultsProvidePropertiesTokenServiceAndFailClosedFallbacksWithoutMapper() {
        try (AnnotationConfigApplicationContext context = locationContext(Map.of())) {
            assertThat(context.getBean(OrgLocationProperties.class)).isNotNull();
            assertThat(context.getBean(OrgLocationCandidateTokenService.class)).isNotNull();

            assertThat(context.getBean(OrgLocationStore.class))
                    .isInstanceOf(UnavailableOrgLocationStore.class);
            assertThat(context.getBean(OrgLocationStore.class).isAvailable()).isFalse();
            assertThat(context.getBean(OrgLocationGeocoder.class))
                    .isInstanceOf(UnavailableOrgLocationGeocoder.class);
            assertThat(context.getBean(OrgLocationGeocoder.class).isAvailable()).isFalse();

            assertThat(context.getBeansOfType(OrgLocationMapper.class)).isEmpty();
            assertThat(context.getBeanNamesForType(OrgLocationStore.class))
                    .containsExactly("unavailableOrgLocationStore");
        }
    }

    @Test
    void storageEnabledRegistersOnlyAnnotatedMapperAndMybatisStore() {
        try (AnnotationConfigApplicationContext context = locationContext(Map.of(
                "auth.org-location.storage-enabled", "true"))) {
            assertThat(context.getBean(OrgLocationProperties.class).isStorageEnabled()).isTrue();
            assertThat(context.getBeanFactory()
                    .getBeanNamesForType(OrgLocationMapper.class, true, false))
                    .containsExactly("orgLocationMapper");
            assertThat(context.getBeanFactory().getBeanDefinition("orgLocationMapper")
                    .getBeanClassName()).isEqualTo(MapperFactoryBean.class.getName());
            assertThat(context.getBeanFactory().getBeanDefinition("orgLocationMapper")
                    .getPropertyValues().get("mapperInterface")).isEqualTo(OrgLocationMapper.class);
            assertThat(context.getBeanFactory()
                    .getBeanNamesForType(OrgLocationStore.class, true, false))
                    .containsExactly("mybatisOrgLocationStore");
            assertThat(context.getBeanFactory().containsBeanDefinition("orgLocationStore"))
                    .isFalse();
        }
    }

    @Test
    void candidateTokenServiceBothConstructorsCanBeCreatedAsSpringBeans() {
        try (AnnotationConfigApplicationContext context = new AnnotationConfigApplicationContext()) {
            context.register(TokenConstructorConfiguration.class);
            context.refresh();

            assertThat(context.getBean("tokenServiceWithProperties"))
                    .isInstanceOf(OrgLocationCandidateTokenService.class);
            assertThat(context.getBean("tokenServiceWithPropertiesAndClock"))
                    .isInstanceOf(OrgLocationCandidateTokenService.class);
        }
    }

    private static AnnotationConfigApplicationContext locationContext(Map<String, String> properties) {
        AnnotationConfigApplicationContext context = new AnnotationConfigApplicationContext();
        Map<String, Object> values = new LinkedHashMap<>(properties);
        context.getEnvironment().getPropertySources()
                .addFirst(new MapPropertySource("org-location-test", values));
        if ("true".equals(properties.get("auth.org-location.storage-enabled"))) {
            // The registry assertions deliberately leave MapperFactoryBean and the store lazy;
            // otherwise a mock SqlSessionFactory would still make MyBatis create a proxy.
            context.addBeanFactoryPostProcessor(
                    OrgLocationConfigurationTest::deferStorageBeans);
        }
        context.register(LocationPropertiesConfiguration.class,
                OrgLocationCandidateTokenService.class,
                OrgLocationDisabledStorageConfiguration.class,
                OrgLocationStorageConfiguration.class,
                OrgLocationDisabledGeocodingConfiguration.class,
                OrgLocationGeocodingConfiguration.class);
        context.refresh();
        return context;
    }

    private static void deferStorageBeans(ConfigurableListableBeanFactory beanFactory) {
        for (String beanName : beanFactory.getBeanDefinitionNames()) {
            BeanDefinition definition = beanFactory.getBeanDefinition(beanName);
            if ("orgLocationMapper".equals(beanName)
                    || "mybatisOrgLocationStore".equals(beanName)
                    || MapperFactoryBean.class.getName().equals(definition.getBeanClassName())) {
                definition.setLazyInit(true);
            }
        }
    }

    @Configuration(proxyBeanMethods = false)
    @EnableConfigurationProperties(OrgLocationProperties.class)
    static class LocationPropertiesConfiguration {
    }

    @Configuration(proxyBeanMethods = false)
    static class TokenConstructorConfiguration {

        @Bean
        OrgLocationProperties orgLocationProperties() {
            return new OrgLocationProperties();
        }

        @Bean
        Clock locationClock() {
            return Clock.fixed(Instant.parse("2026-09-08T00:00:00Z"), ZoneOffset.UTC);
        }

        @Bean("tokenServiceWithProperties")
        OrgLocationCandidateTokenService tokenServiceWithProperties(
                OrgLocationProperties properties) {
            return new OrgLocationCandidateTokenService(properties);
        }

        @Bean("tokenServiceWithPropertiesAndClock")
        OrgLocationCandidateTokenService tokenServiceWithPropertiesAndClock(
                OrgLocationProperties properties, Clock clock) {
            return new OrgLocationCandidateTokenService(properties, clock);
        }
    }
}
