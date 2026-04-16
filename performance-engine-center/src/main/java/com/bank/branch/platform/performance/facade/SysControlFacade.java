package com.bank.branch.platform.performance.facade;

import com.bank.branch.platform.performance.entity.SysControl;
import com.bank.branch.platform.performance.enums.PerfErrorCode;
import com.bank.branch.platform.performance.exception.PerfException;
import com.bank.branch.platform.performance.service.SysControlService;
import com.bank.branch.platform.performance.service.cmd.SwitchVersionCmd;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.data.redis.core.script.RedisScript;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.LocalDate;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

/**
 * SysControlFacade —— 对外封装版本控制 API.
 *
 * <p>核心职责: <b>在事务外</b> 申请/释放 Redis 分布式锁, 再调 Service 的 @Transactional 方法.
 * <p>锁 key: {@code perf:sys_control:switch:{scopeDim}}, TTL 30 秒, Lua 脚本 compare-and-del.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SysControlFacade {

    private final RedisTemplate<String, Object> redisTemplate;
    private final SysControlService sysControlService;

    /** 分布式锁 key 前缀. */
    private static final String LOCK_KEY_PREFIX = "perf:sys_control:switch:";

    /** 锁 TTL (30 秒, 覆盖 doSwitchVersion 最坏耗时). */
    private static final Duration LOCK_TTL = Duration.ofSeconds(30);

    /**
     * Lua 脚本: 仅当 token 匹配时删除锁 (防止误删别的节点的锁).
     * <p>返回 1 成功, 0 失败.
     */
    private static final RedisScript<Long> COMPARE_AND_DEL = new DefaultRedisScript<>(
            "if redis.call('get', KEYS[1])==ARGV[1] then return redis.call('del', KEYS[1]) else return 0 end",
            Long.class);

    /**
     * 切换版本 (分布式锁保护).
     *
     * <p>申请锁失败直接抛 PERF-40904, 不重试;
     * 申请锁成功后 finally 释放 (无论 service 是否抛异常).
     *
     * @param cmd 命令
     * @return 新入库的 SysControl
     * @throws PerfException PERF-40904 获锁失败或 DB UK 冲突
     */
    public SysControl switchVersion(SwitchVersionCmd cmd) {
        String lockKey = LOCK_KEY_PREFIX + cmd.getScopeDim();
        String token = UUID.randomUUID().toString();

        Boolean locked = redisTemplate.opsForValue().setIfAbsent(lockKey, token, LOCK_TTL);
        if (!Boolean.TRUE.equals(locked)) {
            log.warn("[SysControlFacade.switchVersion] 获锁失败, scopeDim={}", cmd.getScopeDim());
            throw new PerfException(PerfErrorCode.SYS_CONTROL_CONFLICT);
        }

        try {
            return sysControlService.doSwitchVersion(cmd);
        } finally {
            try {
                redisTemplate.execute(COMPARE_AND_DEL, Collections.singletonList(lockKey), token);
            } catch (Exception e) {
                // 释放失败只记日志, 不扩散异常 (锁 30s 后自动过期)
                log.warn("[SysControlFacade.switchVersion] 释放锁失败, 依赖 TTL 自动释放. lockKey={}, err={}",
                        lockKey, e.getMessage());
            }
        }
    }

    /**
     * 查询指定维度当前生效版本 (直接委托 Service).
     */
    public SysControl getCurrentVersion(String scopeDim) {
        return sysControlService.getCurrentVersion(scopeDim);
    }

    /**
     * 查询历史版本 (直接委托 Service).
     */
    public List<SysControl> listVersionHistory(String scopeDim, int limit) {
        return sysControlService.listVersionHistory(scopeDim, limit);
    }

    /**
     * 幂等初始化 (直接委托 Service, 不加分布式锁 —— 依赖 DB UK 兜底).
     */
    public SysControl initIfAbsent(String scopeDim, LocalDate dataDate, String version) {
        return sysControlService.initIfAbsent(scopeDim, dataDate, version);
    }
}
