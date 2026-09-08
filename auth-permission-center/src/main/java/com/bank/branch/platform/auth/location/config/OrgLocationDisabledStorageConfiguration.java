package com.bank.branch.platform.auth.location.config;

import com.bank.branch.platform.auth.location.persistence.OrgLocationStore;
import com.bank.branch.platform.auth.location.persistence.UnavailableOrgLocationStore;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** 默认关闭位置存储；关闭时不注册任何位置 Mapper。 */
@Configuration(proxyBeanMethods = false)
@ConditionalOnProperty(prefix = "auth.org-location", name = "storage-enabled",
        havingValue = "false", matchIfMissing = true)
public class OrgLocationDisabledStorageConfiguration {

    @Bean
    public OrgLocationStore unavailableOrgLocationStore() {
        return new UnavailableOrgLocationStore("机构位置存储未启用");
    }
}
