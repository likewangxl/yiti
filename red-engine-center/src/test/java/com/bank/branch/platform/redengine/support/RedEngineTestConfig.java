package com.bank.branch.platform.redengine.support;

import com.bank.branch.platform.auth.api.CurrentUserApi;
import com.bank.branch.platform.auth.api.UserApi;
import com.bank.branch.platform.governance.api.DictApi;
import com.bank.branch.platform.governance.api.FileApi;
import com.bank.branch.platform.governance.api.JobApi;
import org.mockito.Mockito;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;

/**
 * 红色引擎集成测试的跨模块 API 隔离桩。
 *
 * <p>测试启动类只扫描 red-engine-center，认证与治理模块的真实 facade 不会进入上下文；
 * 这里仅补齐构造器依赖，避免 Mapper IT 为了测试一个 BaseMapper 而加载真实外部模块。
 * 具体 API 行为由对应单元测试通过 Mockito 单独覆盖。</p>
 */
@TestConfiguration
public class RedEngineTestConfig {

    /** 提供当前用户上下文桩，供控制器与服务构造器注入。 */
    @Bean
    @Primary
    public CurrentUserApi currentUserApi() {
        return Mockito.mock(CurrentUserApi.class);
    }

    /** 提供用户查询 API 桩，避免测试上下文依赖 auth facade。 */
    @Bean
    @Primary
    public UserApi userApi() {
        return Mockito.mock(UserApi.class);
    }

    /** 提供字典 API 桩，避免测试上下文依赖 governance facade。 */
    @Bean
    @Primary
    public DictApi dictApi() {
        return Mockito.mock(DictApi.class);
    }

    /** 提供文件 API 桩；Mapper IT 不执行对象存储操作。 */
    @Bean
    @Primary
    public FileApi fileApi() {
        return Mockito.mock(FileApi.class);
    }

    /** 提供 Quartz 注册 API 桩，避免测试上下文连接治理中心调度服务。 */
    @Bean
    @Primary
    public JobApi jobApi() {
        return Mockito.mock(JobApi.class);
    }
}
