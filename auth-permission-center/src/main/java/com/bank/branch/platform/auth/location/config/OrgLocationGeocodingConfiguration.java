package com.bank.branch.platform.auth.location.config;

import com.bank.branch.platform.auth.location.OrgLocationProperties;
import com.bank.branch.platform.auth.location.geocode.AmapOrgLocationGeocoder;
import com.bank.branch.platform.auth.location.geocode.OrgLocationGeocoder;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** 地址解析开启时才创建高德客户端；请求地址固定在服务端适配器内。 */
@Configuration(proxyBeanMethods = false)
@ConditionalOnProperty(prefix = "auth.org-location", name = "geocoding-enabled", havingValue = "true")
public class OrgLocationGeocodingConfiguration {

    @Bean
    public OrgLocationGeocoder amapOrgLocationGeocoder(OrgLocationProperties properties,
                                                       ObjectMapper objectMapper) {
        return new AmapOrgLocationGeocoder(properties, objectMapper);
    }
}
