package com.bank.branch.platform.soap.config;

import jakarta.annotation.PreDestroy;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

/**
 * 应用启动后向边车（sidecar）发起注册检查。
 *
 * <p>流程：先轮询边车 {@code /isready} 直到返回 {@code "0"}（边车就绪）——返回 {@code "1"} 或
 * 超时则按 {@code retry-interval-ms} 间隔重试；就绪后调 {@code /up} 发起注册，同样直到返回
 * {@code "0"} 才视为成功——{@code "1"} / 超时 / 未知均按间隔重试，成功后日志输出「微服务注册成功」。</p>
 *
 * <p>检查在独立守护线程异步执行，<b>不阻塞</b> Spring 启动完成回调与 Netty 端口监听。</p>
 */
@Slf4j
@Component
public class SidecarRegistrationChecker {

    private final SidecarProbe probe;
    private final long retryIntervalMs;

    public SidecarRegistrationChecker(
            SidecarProbe probe,
            @Value("${platform.sidecar.registration.retry-interval-ms:2000}") long retryIntervalMs) {
        this.probe = probe;
        this.retryIntervalMs = retryIntervalMs;
    }

    /** 注册检查结果（注册会一直重试至成功，故仅有成功一态）。 */
    public enum Result { SUCCESS }

    /** 应用就绪后异步发起边车注册检查（守护线程，不阻塞启动）。 */
    @EventListener(ApplicationReadyEvent.class)
    public void onApplicationReady() {
        Thread t = new Thread(this::register, "sidecar-registration-check");
        t.setDaemon(true);
        t.start();
    }

    /**
     * 边车注册检查（同步执行，便于单测）。
     *
     * <p>{@code /isready} 与 {@code /up} 均按间隔重试至返回 {@code "0"}，故正常返回即注册成功。</p>
     *
     * @return {@link Result#SUCCESS}（/up 返回 "0"，注册成功）
     */
    public Result register() {
        // 1. 轮询 /isready 直到边车就绪（返回 "0"）——返回 "1" 或超时(null) 均按间隔重试
        while (!"0".equals(probe.get("/isready"))) {
            log.info("[Sidecar] 边车未就绪（/isready≠0 或超时），{}ms 后重试", retryIntervalMs);
            sleep(retryIntervalMs);
        }
        // 2. 边车就绪后发起注册 /up
        while (!"0".equals(probe.get("/up"))) {
            log.info("[Sidecar] 微服务注册失败（/up≠0），{}ms 后重试", retryIntervalMs);
            sleep(retryIntervalMs);
        }
        log.info("微服务注册成功");
        return Result.SUCCESS;
    }

    /**
     * 应用关闭时<b>同步</b>调用边车 {@code /down} 注销本服务（优雅停机摘流量）。
     */
    @PreDestroy
    public void deregister() {
        String resp = probe.get("/down");
        log.info("[Sidecar] 应用关闭，已同步调用边车 /down 注销，响应={}", resp);
    }

    private void sleep(long ms) {
        if (ms <= 0) {
            return;
        }
        try {
            Thread.sleep(ms);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
