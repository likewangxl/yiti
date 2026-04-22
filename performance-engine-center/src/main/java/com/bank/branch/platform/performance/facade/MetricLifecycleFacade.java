package com.bank.branch.platform.performance.facade;

import com.bank.branch.platform.performance.entity.PerfMetricDef;
import com.bank.branch.platform.performance.enums.PerfErrorCode;
import com.bank.branch.platform.performance.exception.PerfException;
import com.bank.branch.platform.performance.service.MetricDefService;
import com.bank.branch.platform.performance.service.cmd.CreateMetricDefCmd;
import com.bank.branch.platform.performance.service.cmd.UpdateMetricDefCmd;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.data.redis.core.script.RedisScript;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.Collections;
import java.util.UUID;

/**
 * 指标生命周期门面，负责事务外的槽位分布式锁.
 */
@Service
@RequiredArgsConstructor
public class MetricLifecycleFacade {

    private static final String LOCK_KEY_PREFIX = "perf:slot-alloc:";
    private static final Duration LOCK_TTL = Duration.ofSeconds(30);
    private static final RedisScript<Long> COMPARE_AND_DEL = new DefaultRedisScript<>(
            "if redis.call('get', KEYS[1])==ARGV[1] then return redis.call('del', KEYS[1]) else return 0 end",
            Long.class);

    private final RedisTemplate<String, Object> redisTemplate;
    private final MetricDefService metricDefService;

    /**
     * 在基础维度级别申请锁后创建指标.
     *
     * @param cmd 创建命令
     * @return 新建指标
     */
    public PerfMetricDef createMetric(CreateMetricDefCmd cmd) {
        String lockKey = LOCK_KEY_PREFIX + cmd.getBaseDim();
        String token = UUID.randomUUID().toString();
        Boolean locked = redisTemplate.opsForValue().setIfAbsent(lockKey, token, LOCK_TTL);
        if (!Boolean.TRUE.equals(locked)) {
            throw new PerfException(PerfErrorCode.VALIDATION_FAILED, cmd.getBaseDim());
        }
        try {
            return metricDefService.create(cmd);
        } finally {
            redisTemplate.execute(COMPARE_AND_DEL, Collections.singletonList(lockKey), token);
        }
    }

    /**
     * Update metric without additional slot lock.
     *
     * @param cmd update command
     * @return updated metric
     */
    public PerfMetricDef updateMetric(UpdateMetricDefCmd cmd) {
        return metricDefService.update(cmd);
    }

    /**
     * Disable metric without additional slot lock.
     *
     * @param metricCode metric code
     * @param reason     disable reason
     * @param operator   operator
     */
    public void disableMetric(String metricCode, String reason, String operator) {
        metricDefService.disable(metricCode, reason, operator);
    }
}
