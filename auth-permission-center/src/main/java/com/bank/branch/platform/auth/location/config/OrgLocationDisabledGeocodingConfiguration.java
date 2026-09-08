package com.bank.branch.platform.auth.location.config;

import com.bank.branch.platform.auth.location.geocode.OrgLocationGeocoder;
import com.bank.branch.platform.auth.location.geocode.UnavailableOrgLocationGeocoder;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** 默认关闭地理编码外呼。 */
@Configuration(proxyBeanMethods = false)
@ConditionalOnProperty(prefix = "auth.org-location", name = "geocoding-enabled",
        havingValue = "false", matchIfMissing = true)
public class OrgLocationDisabledGeocodingConfiguration {

    @Bean
    public OrgLocationGeocoder unavailableOrgLocationGeocoder() {
        return new UnavailableOrgLocationGeocoder("地址解析未启用");
    }
}
