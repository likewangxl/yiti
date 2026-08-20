package com.bank.branch.platform.performance.job;

import com.bank.branch.platform.performance.mapper.StatShowArchiveMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Objects;

/**
 * 统计展示表 T-1 归档任务。
 *
 * <p>tmp 表是唯一归档源，且只保存 T-1。普通运行日按 STATIS_DT 删除后重插目标旬表；
 * 1/11/21 旬边界先清空对应历史表，再插入当日 T-1。1 号另外将 tmp 中的上月月末
 * T-1 幂等写入主表。任务不做数据量或边界预查询，适配 Quartz 当日多次触发。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class StatShowArchiveJob {

    /** 固定处理顺序：客户表在前，员工表在后。 */
    static final String[] MAIN_TABLES = {
            "XAN_M98_CUST_STAT_SHOW3",
            "XAN_M98_EMP_STAT_SHOW3"
    };

    private static final String TMP_SUFFIX = "_TMP";

    private final StatShowArchiveMapper mapper;

    /** Quartz 调度入口：以当前日期执行。 */
    public int run() {
        return run(LocalDate.now(ZoneId.of("Asia/Shanghai")));
    }

    /**
     * 按指定运行日执行，供 Quartz 测试和补偿调用注入确定日期。
     *
     * @return 本次从 tmp 插入各目标表的总行数
     */
    public int run(LocalDate today) {
        Objects.requireNonNull(today, "today");
        LocalDate dataDate = today.minusDays(1);
        String dt = StatShowArchiveDates.fmt(dataDate);
        String histSuffix = StatShowArchiveDates.histSuffixForRunDate(today);
        boolean boundary = StatShowArchiveDates.isBoundaryRunDate(today);
        boolean firstDay = today.getDayOfMonth() == 1;
        int inserted = 0;

        for (String mainTable : MAIN_TABLES) {
            String tmpTable = mainTable + TMP_SUFFIX;
            String histTable = mainTable + histSuffix;

            if (boundary) {
                mapper.truncateTable(histTable);
            } else {
                mapper.deleteByTableDate(histTable, dt);
            }
            inserted += mapper.insertFromTmpByDate(histTable, tmpTable, dt);

            if (firstDay) {
                mapper.deleteByTableDate(mainTable, dt);
                inserted += mapper.insertFromTmpByDate(mainTable, tmpTable, dt);
            }
        }

        log.info("[StatShowArchiveJob] {} 执行完成，数据日={}，目标后缀={}，插入 {} 行",
                today, dataDate, histSuffix, inserted);
        return inserted;
    }
}
