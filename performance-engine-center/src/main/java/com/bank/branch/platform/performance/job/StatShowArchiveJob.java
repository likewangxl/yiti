package com.bank.branch.platform.performance.job;

import com.bank.branch.platform.performance.job.StatShowArchiveDates.CleanupRange;
import com.bank.branch.platform.performance.job.StatShowArchiveDates.MonthRange;
import com.bank.branch.platform.performance.mapper.StatShowArchiveMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.util.Optional;

/**
 * 统计展示表「日增量归档 + 分批清理 + 主表瘦身」业务任务（由 Quartz 每天循环调用）.
 *
 * <p><strong>不加 @Transactional</strong>：每个按日 mapper 调用需独立提交（每次 ≤ 一天约 200 万行），
 * 避免整月/整旬巨型事务锁表。幂等：插入用 count 比对，删除删空即 no-op。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class StatShowArchiveJob {

    /** 待归档的两张主表（固定白名单，历史表 = 主表名 + _H1/_H2/_H3）. */
    static final String[] MAIN_TABLES = {
            "XAN_M98_CUST_STAT_SHOW3",
            "XAN_M98_EMP_STAT_SHOW3"
    };

    private final StatShowArchiveMapper mapper;

    /** Quartz 调度入口：以当前日期执行. */
    public int run() {
        return run(LocalDate.now());
    }

    /**
     * 按指定运行日执行（供单测注入确定日期）.
     *
     * @return 本次插入历史表的总行数（跨两表累加）
     */
    public int run(LocalDate today) {
        LocalDate yesterday = today.minusDays(1);
        int inserted = 0;
        for (String main : MAIN_TABLES) {
            inserted += dailyIncremental(main, yesterday);
            boundaryCleanup(main, today);
            pruneMain(main, today);
        }
        log.info("[StatShowArchiveJob] {} 执行完成，昨天={}，共插入 {} 行", today, yesterday, inserted);
        return inserted;
    }

    /** ①日增量补全：把「昨天所属旬的旬首~昨天」逐日搬进对应历史表（幂等）. */
    private int dailyIncremental(String main, LocalDate yesterday) {
        String histTable = main + StatShowArchiveDates.histSuffix(yesterday);
        LocalDate start = StatShowArchiveDates.sliceStart(yesterday);
        int total = 0;
        for (LocalDate d : StatShowArchiveDates.datesInclusive(start, yesterday)) {
            total += syncDate(main, histTable, StatShowArchiveDates.fmt(d));
        }
        return total;
    }

    /** 单日幂等同步：main 无数→跳过；hist 已等量→跳过；否则删该日再插该日. */
    private int syncDate(String main, String histTable, String dt) {
        long mainCnt = mapper.countByTableDate(main, dt);
        if (mainCnt == 0) {
            return 0; // 源表当日数据未就绪，等下一轮
        }
        long histCnt = mapper.countByTableDate(histTable, dt);
        if (histCnt == mainCnt) {
            return 0; // 已同步
        }
        mapper.deleteHistByDate(histTable, dt); // 幂等重置（无主键，防重复）
        int n = mapper.insertHistByDate(histTable, main, dt);
        log.info("[StatShowArchiveJob] {} <- {} 同步 {} 行 @ {}", histTable, main, n, dt);
        return n;
    }

    /** ②旬边界清理：today 为 1/11/21 时，按天分批删「上一代」旧旬. */
    private void boundaryCleanup(String main, LocalDate today) {
        Optional<CleanupRange> opt = StatShowArchiveDates.boundaryCleanup(today);
        if (opt.isEmpty()) {
            return;
        }
        CleanupRange cr = opt.get();
        String histTable = main + cr.histSuffix();
        int deleted = 0;
        for (LocalDate d : StatShowArchiveDates.datesInclusive(cr.start(), cr.end())) {
            deleted += mapper.deleteHistByDate(histTable, StatShowArchiveDates.fmt(d));
        }
        log.info("[StatShowArchiveJob] 边界清理 {} {}~{} 删 {} 行", histTable, cr.start(), cr.end(), deleted);
    }

    /** ③主表瘦身：仅 1 号，保留上月 MAX(STATIS_DT)，按天分批删其余. */
    private void pruneMain(String main, LocalDate today) {
        Optional<MonthRange> opt = StatShowArchiveDates.pruneMonth(today);
        if (opt.isEmpty()) {
            return;
        }
        MonthRange r = opt.get();
        String keepDt = mapper.selectMaxStatisDt(main, StatShowArchiveDates.fmt(r.start()), StatShowArchiveDates.fmt(r.end()));
        if (keepDt == null || keepDt.isBlank()) {
            log.warn("[StatShowArchiveJob] 主表 {} 瘦身跳过：{}~{} 无数据", main, r.start(), r.end());
            return;
        }
        int deleted = 0;
        for (LocalDate d : StatShowArchiveDates.datesInclusive(r.start(), r.end())) {
            String ds = StatShowArchiveDates.fmt(d);
            if (!ds.equals(keepDt)) {
                deleted += mapper.deleteMainByDate(main, ds);
            }
        }
        log.info("[StatShowArchiveJob] 主表 {} 瘦身 {}~{} 保留 {}，删 {} 行", main, r.start(), r.end(), keepDt, deleted);
    }
}
