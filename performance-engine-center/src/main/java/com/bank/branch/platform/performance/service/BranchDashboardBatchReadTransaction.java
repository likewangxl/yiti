package com.bank.branch.platform.performance.service;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.function.Supplier;

/**
 * 为批次输入读取建立独立的只读事务边界。
 *
 * <p>批次服务通过 Bean 代理调用该方法，避免在同一类内调用 private 方法导致
 * {@code @Transactional} 失效。使用主数据源的 Mapper/API 会加入同一个事务快照。</p>
 */
@Service
public class BranchDashboardBatchReadTransaction {

    @Transactional(readOnly = true, propagation = Propagation.REQUIRES_NEW,
            isolation = Isolation.REPEATABLE_READ)
    public <T> T inSnapshot(Supplier<T> operation) {
        if (operation == null) {
            throw new IllegalArgumentException("批次读取操作不能为空");
        }
        return operation.get();
    }
}
