package com.bank.branch.platform.performance.job;

import com.bank.branch.platform.performance.entity.StatShowArchiveStatus;
import com.bank.branch.platform.performance.mapper.StatShowArchiveMapper;
import com.bank.branch.platform.performance.mapper.StatShowArchiveStatusMapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

/**
 * 统计展示表 T-1 归档任务。
 *
 * <p>tmp 表是唯一归档源，且只保存 T-1。普通运行日按 STATIS_DT 删除后重插目标旬表；
 * 1/11/21 旬边界先清空对应历史表，再插入当日 T-1。1 号另外将 tmp 中的上月月末
 * T-1 幂等写入主表。成功归档目标记录写入状态表，Quartz 当日重复触发时跳过已完成目标。
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
    private final StatShowArchiveStatusMapper statusMapper;

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
        List<ArchiveTarget> targets = buildTargets(histSuffix, boundary, firstDay);
        List<String> targetTables = targets.stream().map(ArchiveTarget::targetTable).toList();
        Set<String> completedTargets = findCompletedTargets(dataDate, targetTables);
        Map<String, Long> tmpCounts = readPendingTmpCounts(targets, completedTargets, dt);
        LocalDateTime createdTime = LocalDateTime.now(ZoneId.of("Asia/Shanghai"));
        int inserted = 0;

        for (ArchiveTarget target : targets) {
            if (completedTargets.contains(target.targetTable())) {
                continue;
            }
            long tmpCount = tmpCounts.getOrDefault(target.sourceTable(), 0L);
            if (tmpCount <= 0L) {
                continue;
            }

            if (target.truncate()) {
                mapper.truncateTable(target.targetTable());
            } else {
                mapper.deleteByTableDate(target.targetTable(), dt);
            }
            inserted += mapper.insertFromTmpByDate(target.targetTable(), target.sourceTable(), dt);

            StatShowArchiveStatus status = new StatShowArchiveStatus();
            status.setId(UUID.randomUUID().toString().replace("-", ""));
            status.setDataDate(dataDate);
            status.setSourceTable(target.sourceTable());
            status.setTargetTable(target.targetTable());
            status.setTmpCount(tmpCount);
            status.setArchiveStatus(StatShowArchiveStatus.SUCCESS);
            status.setCreatedTime(createdTime);
            if (statusMapper.insert(status) != 1) {
                throw new IllegalStateException("写入 PERF_STAT_SHOW_ARCHIVE_STATUS 失败");
            }
        }

        log.info("[StatShowArchiveJob] {} 执行完成，数据日={}，目标后缀={}，插入 {} 行",
                today, dataDate, histSuffix, inserted);
        return inserted;
    }

    private List<ArchiveTarget> buildTargets(String histSuffix, boolean boundary, boolean firstDay) {
        List<ArchiveTarget> targets = new ArrayList<>();
        for (String mainTable : MAIN_TABLES) {
            String tmpTable = mainTable + TMP_SUFFIX;
            targets.add(new ArchiveTarget(tmpTable, mainTable + histSuffix, boundary));
            if (firstDay) {
                targets.add(new ArchiveTarget(tmpTable, mainTable, false));
            }
        }
        return targets;
    }

    private Set<String> findCompletedTargets(LocalDate dataDate, List<String> targetTables) {
        List<StatShowArchiveStatus> statuses = statusMapper.selectList(
                Wrappers.<StatShowArchiveStatus>lambdaQuery()
                        .eq(StatShowArchiveStatus::getDataDate, dataDate)
                        .eq(StatShowArchiveStatus::getArchiveStatus, StatShowArchiveStatus.SUCCESS)
                        .in(StatShowArchiveStatus::getTargetTable, targetTables));
        Set<String> completedTargets = new HashSet<>();
        if (statuses != null) {
            for (StatShowArchiveStatus status : statuses) {
                if (status != null && status.getTargetTable() != null) {
                    completedTargets.add(status.getTargetTable());
                }
            }
        }
        return completedTargets;
    }

    private Map<String, Long> readPendingTmpCounts(List<ArchiveTarget> targets,
                                                    Set<String> completedTargets,
                                                    String dt) {
        Map<String, Long> tmpCounts = new HashMap<>();
        for (ArchiveTarget target : targets) {
            if (completedTargets.contains(target.targetTable())) {
                continue;
            }
            tmpCounts.computeIfAbsent(target.sourceTable(),
                    sourceTable -> mapper.countByTableDate(sourceTable, dt));
        }
        return tmpCounts;
    }

    private record ArchiveTarget(String sourceTable, String targetTable, boolean truncate) {
    }
}
