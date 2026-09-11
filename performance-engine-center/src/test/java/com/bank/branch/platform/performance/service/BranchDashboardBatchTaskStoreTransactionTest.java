package com.bank.branch.platform.performance.service;

import com.bank.branch.platform.performance.api.dto.BranchDashboardBatchDTO;
import com.bank.branch.platform.performance.api.dto.BranchDashboardBatchRowDTO;
import com.bank.branch.platform.performance.entity.PerfMetricDef;
import com.bank.branch.platform.performance.entity.PerfRunTask;
import com.bank.branch.platform.performance.mapper.OrgIndexResultMapper;
import com.bank.branch.platform.performance.mapper.PerfRunTaskMapper;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.aop.support.AopUtils;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.EnableTransactionManagement;
import org.springframework.transaction.support.AbstractPlatformTransactionManager;
import org.springframework.transaction.support.DefaultTransactionStatus;
import org.springframework.transaction.TransactionDefinition;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

/**
 * 验证批次写入实际经过 Spring 事务代理：中间 slot 写失败必须整体回滚，
 * 初始 RUNNING 与 FAILED 追溯状态必须通过独立事务提交。
 */
class BranchDashboardBatchTaskStoreTransactionTest {

    @Test
    void commitSuccess_rollsBackAllWritesWhenTheNthSlotFails() {
        PerfRunTaskMapper taskMapper = mock(PerfRunTaskMapper.class);
        OrgIndexResultMapper resultMapper = mock(OrgIndexResultMapper.class);
        RecordingTransactionManager transactionManager = new RecordingTransactionManager();
        AtomicInteger slotWrites = new AtomicInteger();
        doAnswer(invocation -> {
            if (slotWrites.incrementAndGet() == 2) {
                throw new IllegalStateException("slot-2-write-failed");
            }
            return null;
        }).when(resultMapper).insertSlotValue(anyString(), any(LocalDate.class), anyString(), anyInt(), any());

        newRunner(taskMapper, resultMapper, transactionManager).run(context -> {
            BranchDashboardBatchTaskStore store = context.getBean(BranchDashboardBatchTaskStore.class);
            assertThat(AopUtils.isAopProxy(store)).isTrue();

            assertThatThrownBy(() -> store.commitSuccess(
                    "B1", snapshot(), definitions(), outputCodes(), "V1", "{}", "{}"))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("slot-2-write-failed");
            verify(taskMapper, never()).updateResultPreviewJson(anyString(), anyString());
            verify(taskMapper, never()).updateStatusWithParams(anyString(), anyString(), any(), anyString());
        });

        assertThat(transactionManager.beginCount.get()).isEqualTo(1);
        assertThat(transactionManager.commitCount.get()).isZero();
        assertThat(transactionManager.rollbackCount.get()).isEqualTo(1);
    }

    @Test
    void startAndMarkFailed_commitThroughSeparateTransactions() {
        PerfRunTaskMapper taskMapper = mock(PerfRunTaskMapper.class);
        OrgIndexResultMapper resultMapper = mock(OrgIndexResultMapper.class);
        RecordingTransactionManager transactionManager = new RecordingTransactionManager();

        newRunner(taskMapper, resultMapper, transactionManager).run(context -> {
            BranchDashboardBatchTaskStore store = context.getBean(BranchDashboardBatchTaskStore.class);
            PerfRunTask task = new PerfRunTask();
            task.setId("B1");
            task.setStatus("RUNNING");
            store.start(task);
            store.markFailed("B1", "internal detail", "{\"status\":\"FAILED\"}",
                    LocalDate.of(2026, 8, 30), "V1");
        });

        assertThat(transactionManager.beginCount.get()).isEqualTo(2);
        assertThat(transactionManager.commitCount.get()).isEqualTo(2);
        assertThat(transactionManager.rollbackCount.get()).isZero();
        verify(taskMapper).insert(any(PerfRunTask.class));
        verify(taskMapper).updateBatchContext("B1", LocalDate.of(2026, 8, 30), "V1");
        verify(taskMapper).updateStatusWithParams("B1", "FAILED", "internal detail",
                "{\"status\":\"FAILED\"}");
    }

    private ApplicationContextRunner newRunner(PerfRunTaskMapper taskMapper,
                                               OrgIndexResultMapper resultMapper,
                                               RecordingTransactionManager transactionManager) {
        return new ApplicationContextRunner()
                .withBean(PerfRunTaskMapper.class, () -> taskMapper)
                .withBean(OrgIndexResultMapper.class, () -> resultMapper)
                .withBean(ObjectMapper.class, ObjectMapper::new)
                .withBean(PlatformTransactionManager.class, () -> transactionManager)
                .withUserConfiguration(TransactionTestConfiguration.class);
    }

    private BranchDashboardBatchDTO snapshot() {
        return BranchDashboardBatchDTO.builder()
                .dataDate(LocalDate.of(2026, 8, 30))
                .rows(List.of(row("O1"), row("O2")))
                .build();
    }

    private BranchDashboardBatchRowDTO row(String orgCode) {
        Map<String, BigDecimal> values = new LinkedHashMap<>();
        for (String code : outputCodes()) {
            values.put(code, BigDecimal.ONE);
        }
        return BranchDashboardBatchRowDTO.builder()
                .orgCode(orgCode)
                .dataDate(LocalDate.of(2026, 8, 30))
                .metricValues(values)
                .build();
    }

    private Map<String, PerfMetricDef> definitions() {
        Map<String, PerfMetricDef> definitions = new LinkedHashMap<>();
        int slot = 1;
        for (String code : outputCodes()) {
            PerfMetricDef definition = new PerfMetricDef();
            definition.setMetricCode(code);
            definition.setValSlot(slot++);
            definitions.put(code, definition);
        }
        return definitions;
    }

    private List<String> outputCodes() {
        return List.of("CUSTOMER", "ATTENTION", "TARGET", "ORG_RATE", "GROUP_RATE");
    }

    @TestConfiguration(proxyBeanMethods = false)
    @EnableTransactionManagement
    static class TransactionTestConfiguration {

        @Bean
        BranchDashboardBatchTaskStore branchDashboardBatchTaskStore(
                PerfRunTaskMapper taskMapper, OrgIndexResultMapper resultMapper,
                ObjectMapper objectMapper) {
            return new BranchDashboardBatchTaskStore(taskMapper, resultMapper, objectMapper);
        }
    }

    private static final class RecordingTransactionManager extends AbstractPlatformTransactionManager {
        private final AtomicInteger beginCount = new AtomicInteger();
        private final AtomicInteger commitCount = new AtomicInteger();
        private final AtomicInteger rollbackCount = new AtomicInteger();

        @Override
        protected Object doGetTransaction() {
            return new Object();
        }

        @Override
        protected void doBegin(Object transaction, TransactionDefinition definition) {
            beginCount.incrementAndGet();
        }

        @Override
        protected void doCommit(DefaultTransactionStatus status) {
            commitCount.incrementAndGet();
        }

        @Override
        protected void doRollback(DefaultTransactionStatus status) {
            rollbackCount.incrementAndGet();
        }
    }
}
