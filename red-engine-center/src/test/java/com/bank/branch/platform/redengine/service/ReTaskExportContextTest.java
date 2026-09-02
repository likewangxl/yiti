package com.bank.branch.platform.redengine.service;

import com.bank.branch.platform.auth.api.CurrentUserApi;
import com.bank.branch.platform.governance.api.DictApi;
import com.bank.branch.platform.governance.api.FileApi;
import com.bank.branch.platform.redengine.controller.ReTaskExportController;
import com.bank.branch.platform.redengine.mapper.RePartyOrgMapper;
import com.bank.branch.platform.redengine.mapper.ReSubmitFileMapper;
import com.bank.branch.platform.redengine.mapper.ReSubmitMapper;
import com.bank.branch.platform.redengine.mapper.ReTaskBranchAssignmentMapper;
import com.bank.branch.platform.redengine.mapper.ReTaskExportTaskMapper;
import com.bank.branch.platform.redengine.mapper.ReTaskInstanceMapper;
import com.bank.branch.platform.redengine.mapper.ReTaskMapper;
import com.bank.branch.platform.redengine.mapper.ReTaskReSubmitRelMapper;
import com.bank.branch.platform.redengine.mapper.ReTaskSubmissionFileMapper;
import com.bank.branch.platform.redengine.mapper.ReTaskSubmissionMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.aop.framework.AopProxyUtils;
import org.springframework.aop.support.AopUtils;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.EnableAspectJAutoProxy;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.EnableTransactionManagement;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

/**
 * 导出控制器的最小 Spring 装配回归测试。
 *
 * <p>该测试不启动数据库，只验证控制器、导出服务、异步执行器和事务 worker 的构造图；
 * 其中异步执行器必须有唯一的容器构造入口，worker 必须保留 Spring 事务代理。</p>
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = ReTaskExportContextTest.ExportContext.class)
class ReTaskExportContextTest {

    @org.springframework.beans.factory.annotation.Autowired
    private org.springframework.context.ApplicationContext context;

    @Test
    void exportControllerGraphIsSpringConstructibleAndWorkerIsTransactionalProxy() {
        assertThat(context.getBean(ReTaskExportController.class)).isNotNull();
        assertThat(context.getBean(ReTaskExportService.class)).isNotNull();
        assertThat(context.getBean(ReTaskExportAsyncExecutor.class)).isNotNull();

        Object worker = context.getBean(ReTaskExportWorker.class);
        assertThat(AopUtils.isAopProxy(worker)).isTrue();
        assertThat(AopProxyUtils.ultimateTargetClass(worker)).isEqualTo(ReTaskExportWorker.class);

        // 多构造器会让未识别 @Autowired 的运行环境退回“无默认构造器”路径。
        assertThat(ReTaskExportAsyncExecutor.class.getDeclaredConstructors()).hasSize(1);
    }

    @Configuration(proxyBeanMethods = false)
    @EnableTransactionManagement
    @EnableAspectJAutoProxy
    @Import({ReTaskExportController.class, ReTaskExportServiceImpl.class,
            ReTaskExportAsyncExecutor.class, ReTaskExportWorker.class})
    static class ExportContext {

        @Bean
        PlatformTransactionManager transactionManager() {
            return mock(PlatformTransactionManager.class);
        }

        @Bean
        CurrentUserApi currentUserApi() {
            return mock(CurrentUserApi.class);
        }

        @Bean
        ReTaskExportTaskMapper exportTaskMapper() {
            return mock(ReTaskExportTaskMapper.class);
        }

        @Bean
        ReTaskMapper taskMapper() {
            return mock(ReTaskMapper.class);
        }

        @Bean
        ReTaskInstanceMapper instanceMapper() {
            return mock(ReTaskInstanceMapper.class);
        }

        @Bean
        ReTaskBranchAssignmentMapper assignmentMapper() {
            return mock(ReTaskBranchAssignmentMapper.class);
        }

        @Bean
        ReTaskSubmissionMapper submissionMapper() {
            return mock(ReTaskSubmissionMapper.class);
        }

        @Bean
        ReTaskSubmissionFileMapper submissionFileMapper() {
            return mock(ReTaskSubmissionFileMapper.class);
        }

        @Bean
        ReTaskReSubmitRelMapper taskReSubmitRelMapper() {
            return mock(ReTaskReSubmitRelMapper.class);
        }

        @Bean
        ReSubmitMapper reSubmitMapper() {
            return mock(ReSubmitMapper.class);
        }

        @Bean
        ReSubmitFileMapper reSubmitFileMapper() {
            return mock(ReSubmitFileMapper.class);
        }

        @Bean
        RePartyOrgMapper partyOrgMapper() {
            return mock(RePartyOrgMapper.class);
        }

        @Bean
        ReTaskManagementService taskManagementService() {
            return mock(ReTaskManagementService.class);
        }

        @Bean
        DictApi dictApi() {
            return mock(DictApi.class);
        }

        @Bean
        FileApi fileApi() {
            return mock(FileApi.class);
        }
    }
}
