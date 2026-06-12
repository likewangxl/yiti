package com.bank.branch.platform.config;

import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletResponse;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * SpaEntryController 单测：路径式 /login 重定向到 hash 登录页。
 */
class SpaEntryControllerTest {

    @Test
    void login_redirectsToHashLogin() throws Exception {
        MockHttpServletResponse resp = new MockHttpServletResponse();

        new SpaEntryController().login(resp);

        assertThat(resp.getStatus()).isEqualTo(302);
        assertThat(resp.getRedirectedUrl()).isEqualTo("/#/login");
    }
}
