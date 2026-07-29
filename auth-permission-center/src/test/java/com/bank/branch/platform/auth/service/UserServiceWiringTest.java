package com.bank.branch.platform.auth.service;

import com.bank.branch.platform.auth.config.AuthConfig;
import com.bank.branch.platform.auth.config.AuthUserProperties;
import com.bank.branch.platform.auth.mapper.UserMapper;
import com.bank.branch.platform.auth.mapper.UserOrgMapper;
import com.bank.branch.platform.auth.mapper.UserRoleMapper;
import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

class UserServiceWiringTest {

    @Test
    void userServiceShouldUseConfiguredPasswordEncoderBean() {
        try (AnnotationConfigApplicationContext context =
                     new AnnotationConfigApplicationContext()) {
            context.registerBean(UserMapper.class, () -> mock(UserMapper.class));
            context.registerBean(UserOrgMapper.class, () -> mock(UserOrgMapper.class));
            context.registerBean(UserRoleMapper.class, () -> mock(UserRoleMapper.class));
            context.registerBean(AuthUserProperties.class, AuthUserProperties::new);
            context.register(AuthConfig.class, UserService.class);

            context.refresh();

            assertThat(context.getBean(UserService.class)).isNotNull();
        }
    }
}
