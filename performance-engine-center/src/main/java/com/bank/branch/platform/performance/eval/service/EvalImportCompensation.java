package com.bank.branch.platform.performance.eval.service;

import com.bank.branch.platform.performance.eval.entity.EvalAssignBatch;
import com.bank.branch.platform.performance.eval.mapper.EvalAssignBatchMapper;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 评价导入超时补偿器（2026-06-26）。
 *
 * <p>覆盖「JVM 重启 / 实例宕机时正在跑的异步导入丢失」：扫描 STATUS=3(IMPORTING) 且
 * CREATE_TIME 早于阈值（{@code perf.eval.import.importing-timeout-min}，默认 10 分钟）的批次，
 * 置 STATUS=4(导入失败) + ERROR_SUMMARY="导入中断或超时，请重传"。</p>
 *
 * <p>触发时机：{@link ApplicationRunner} 启动扫一次 + {@code @Scheduled} 每 5 分钟扫一次
 * （复用 {@code PerformanceSchedulingConfig} 的 {@code @EnableScheduling}）。</p>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class EvalImportCompensation implements ApplicationRunner {

    /** IMPORTING 批次超时阈值（分钟），超过即判定为中断/超时。 */
    @Value("${perf.eval.import.importing-timeout-min:10}")
    int importingTimeoutMin = 10;

    private final EvalAssignBatchMapper batchMapper;

    /**
     * 启动时扫一次：兜底「上次进程崩溃时遗留的 IMPORTING 批次」。
     *
     * @param args 启动参数（未使用）
     */
    @Override
    public void run(ApplicationArguments args) {
        int flipped = compensateStaleImporting();
        if (flipped > 0) {
            log.warn("[EvalImportCompensation] 启动补偿：{} 个超时 IMPORTING 批次置失败", flipped);
        }
    }

    /**
     * 定时补偿：每 5 分钟扫一次（首次延迟 5 分钟，避开与启动扫描重复）。
     */
    @Scheduled(fixedDelay = 300_000L, initialDelay = 300_000L)
    public void scheduledCompensate() {
        compensateStaleImporting();
    }

    /**
     * 扫描超时 IMPORTING 批次并置失败。
     *
     * <p>先按 STATUS=3 查出处理中批次，再在内存按 CREATE_TIME 早于 (now - 阈值) 判定超时：
     * DB 查询负责状态过滤，内存判定负责时间阈值（阈值可配且便于单测）。</p>
     *
     * @return 本次置失败的批次数
     */
    public int compensateStaleImporting() {
        LocalDateTime cutoff = LocalDateTime.now().minusMinutes(importingTimeoutMin);
        List<EvalAssignBatch> importing = batchMapper.selectList(
                new LambdaQueryWrapper<EvalAssignBatch>().eq(EvalAssignBatch::getStatus, 3));
        if (importing == null || importing.isEmpty()) {
            return 0;
        }
        int flipped = 0;
        for (EvalAssignBatch b : importing) {
            // 仅处理早于阈值的：避免误伤正在正常处理（未超时）的导入
            if (b.getCreateTime() != null && b.getCreateTime().isBefore(cutoff)) {
                b.setStatus(4);
                b.setErrorSummary("导入中断或超时，请重传");
                batchMapper.updateById(b);
                flipped++;
                log.warn("[EvalImportCompensation] batchId={} 导入超时（createTime={} < cutoff={}），置失败",
                        b.getBatchId(), b.getCreateTime(), cutoff);
            }
        }
        return flipped;
    }
}
