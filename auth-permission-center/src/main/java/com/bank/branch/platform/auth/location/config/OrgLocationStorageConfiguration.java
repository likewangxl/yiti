package com.bank.branch.platform.auth.location.config;

import com.bank.branch.platform.auth.location.persistence.MybatisOrgLocationStore;
import com.bank.branch.platform.auth.location.persistence.OrgLocationMapper;
import com.bank.branch.platform.auth.location.persistence.OrgLocationStore;
import org.apache.ibatis.annotations.Mapper;
import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** 位置存储开启时才扫描 PT_ORG_LOCATION Mapper。 */
@Configuration(proxyBeanMethods = false)
@ConditionalOnProperty(prefix = "auth.org-location", name = "storage-enabled", havingValue = "true")
@MapperScan(basePackages = "com.bank.branch.platform.auth.location.persistence",
        annotationClass = Mapper.class)
public class OrgLocationStorageConfiguration {

    @Bean
    public OrgLocationStore mybatisOrgLocationStore(OrgLocationMapper mapper) {
        return new MybatisOrgLocationStore(mapper);
    }
}
