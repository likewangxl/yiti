package com.bank.branch.platform.performance.support;

import com.bank.branch.platform.performance.entity.PerfRunTask;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.UUID;

/**
 * P1-E 运行任务子域测试数据构造器.
 *
 * <p>统一构造带 {@code TEST_RT_} 前缀的 PerfRunTask 实体，避免污染生产数据；
 * 单线程 IT 的事务会回滚，这里的前缀主要保证并发 / 手工排查时可识别。
 *
 * <p>由于 PerfRunTaskMapper 只读（V1.0 无 insert 方法），测试数据通过 JdbcTemplate
 * 在 IT 中直接 {@code INSERT INTO perf_run_task ...} 准备。本 Builder 只负责
 * 构造 Entity 对象以便 IT 组织测试数据。
 */
public final class RunTaskTestDataBuilder implements TestDataBuilder {

    /** 统一测试编号前缀（task_key 使用）. */
    public static final String KEY_PREFIX = "TEST_RT_";

    private RunTaskTestDataBuilder() {
    }

    /**
     * 构造一个默认 RUNNING 任务（task_type=METRIC_RUN, data_date=今天）.
     *
     * @param keySuffix task_key 后缀
     * @param startedBy 发起人（数据范围过滤基准字段）
     * @return 任务实体
     */
    public static PerfRunTask task(String keySuffix, String startedBy) {
        return task(keySuffix, "METRIC_RUN", LocalDate.now(), "RUNNING", startedBy);
    }

    /**
     * 构造自定义任务.
     *
     * @param keySuffix task_key 后缀
     * @param taskType  任务类型
     * @param dataDate  数据日期
     * @param status    状态
     * @param startedBy 发起人
     * @return 任务实体
     */
    public static PerfRunTask task(String keySuffix, String taskType, LocalDate dataDate,
                                   String status, String startedBy) {
        PerfRunTask t = new PerfRunTask();
        t.setId(randomId());
        t.setTaskKey(KEY_PREFIX + keySuffix);
        t.setTaskType(taskType);
        t.setDataDate(dataDate);
        t.setDataVersion("V_TEST_" + keySuffix);
        t.setStatus(status);
        t.setStartedBy(startedBy);
        t.setStartTime(LocalDateTime.now());
        // end_time / error_msg / params_json / result_preview_json 保持 null；created_time 由 DB 默认值填充
        return t;
    }

    private static String randomId() {
        return UUID.randomUUID().toString().replace("-", "");
    }
}
