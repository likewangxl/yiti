package com.bank.branch.platform.performance.mapper;

import com.bank.branch.platform.performance.entity.PerfRunTask;
import com.bank.branch.platform.performance.support.PerformanceMapperTestBase;
import com.bank.branch.platform.performance.support.RunTaskTestDataBuilder;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * PerfRunTaskMapper 集成测试（只读）.
 *
 * <p>Mapper 无 insert 方法，测试数据通过 {@link JdbcTemplate} 直接写入，
 * 继承 {@link PerformanceMapperTestBase} 的 {@code @Transactional + @Rollback} 保证自动回滚.
 *
 * <p>覆盖场景：
 * <ul>
 *   <li>基础路径：selectById / selectByTaskNo / selectByCondition 多种过滤 / 两个 count 方法</li>
 *   <li>数据范围片段注入：filter = null (管理员全见) vs filter 指向特定 started_by (普通用户仅见自己)</li>
 * </ul>
 */
class PerfRunTaskMapperIT extends PerformanceMapperTestBase {

    @Autowired
    private PerfRunTaskMapper mapper;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    /** 直接走 JDBC 插入（Mapper 只读，V1.0 无 insert 方法）. */
    private void insertRaw(PerfRunTask t) {
        jdbcTemplate.update(
                "INSERT INTO PERF_RUN_TASK (id, task_type, task_key, data_date, data_version, "
                        + "params_json, status, started_by, start_time, end_time, error_msg, result_preview_json) "
                        + "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)",
                t.getId(), t.getTaskType(), t.getTaskKey(), t.getDataDate(), t.getDataVersion(),
                t.getParamsJson(), t.getStatus(), t.getStartedBy(), t.getStartTime(),
                t.getEndTime(), t.getErrorMsg(), t.getResultPreviewJson()
        );
    }

    @Test
    @DisplayName("selectById 不存在时返回 null")
    void selectById_whenNotFound_returnsNull() {
        assertThat(mapper.selectById("NO_SUCH_ID_PERF_RT")).isNull();
    }

    @Test
    @DisplayName("selectById 存在时可查回任务")
    void selectById_whenExists_ok() {
        PerfRunTask t = RunTaskTestDataBuilder.task("SEL_ID", "USER_X");
        insertRaw(t);

        PerfRunTask loaded = mapper.selectById(t.getId());

        assertThat(loaded).isNotNull();
        assertThat(loaded.getTaskKey()).isEqualTo("TEST_RT_SEL_ID");
        assertThat(loaded.getStartedBy()).isEqualTo("USER_X");
        assertThat(loaded.getStatus()).isEqualTo("RUNNING");
    }

    @Test
    @DisplayName("selectByTaskNo 不存在时返回 null")
    void selectByTaskNo_whenNotFound_returnsNull() {
        assertThat(mapper.selectByTaskNo("TEST_RT_NO_SUCH_NO")).isNull();
    }

    @Test
    @DisplayName("selectByTaskNo 存在时可查回任务")
    void selectByTaskNo_whenExists_ok() {
        PerfRunTask t = RunTaskTestDataBuilder.task("BY_NO", "USER_Y");
        insertRaw(t);

        PerfRunTask loaded = mapper.selectByTaskNo("TEST_RT_BY_NO");

        assertThat(loaded).isNotNull();
        assertThat(loaded.getId()).isEqualTo(t.getId());
    }

    @Test
    @DisplayName("selectByCondition 无数据范围片段时不按 started_by 过滤（管理员场景）")
    void selectByCondition_noDataScopeFilter_returnsAll() {
        // 插入 3 条不同 started_by 的任务（dataVersion 唯一以便精确过滤）
        PerfRunTask a = RunTaskTestDataBuilder.task("ALL_A", "USER_AA");
        PerfRunTask b = RunTaskTestDataBuilder.task("ALL_B", "USER_BB");
        PerfRunTask c = RunTaskTestDataBuilder.task("ALL_C", "USER_CC");
        insertRaw(a);
        insertRaw(b);
        insertRaw(c);

        // filter = null 表示管理员全见；仅按 taskKey 精确过滤逐条读取并汇总 started_by 多样性
        // 用法说明：本用例主要验证 "filter 为 null 不产生按 started_by 的过滤"——通过分别读取 3 条并确认都可见
        PerfRunTask loadedA = mapper.selectByTaskNo("TEST_RT_ALL_A");
        PerfRunTask loadedB = mapper.selectByTaskNo("TEST_RT_ALL_B");
        PerfRunTask loadedC = mapper.selectByTaskNo("TEST_RT_ALL_C");
        assertThat(loadedA.getStartedBy()).isEqualTo("USER_AA");
        assertThat(loadedB.getStartedBy()).isEqualTo("USER_BB");
        assertThat(loadedC.getStartedBy()).isEqualTo("USER_CC");

        // 再验证 selectByCondition 在 filter=null 下会把 3 种 started_by 都包含进来
        List<PerfRunTask> list = mapper.selectByCondition(null, null, null, null, null, 0, 500);
        assertThat(list).extracting(PerfRunTask::getStartedBy)
                .contains("USER_AA", "USER_BB", "USER_CC");
    }

    @Test
    @DisplayName("selectByCondition + 数据范围片段: 普通用户仅见自己 started_by 的任务")
    void selectByCondition_withDataScopeFilter_onlyShowsOwnTasks() {
        // 插入 3 条不同 started_by 的任务
        PerfRunTask ta = RunTaskTestDataBuilder.task("DS_A", "USER_A");
        PerfRunTask tb = RunTaskTestDataBuilder.task("DS_B", "USER_B");
        PerfRunTask tc = RunTaskTestDataBuilder.task("DS_C", "USER_C");
        insertRaw(ta);
        insertRaw(tb);
        insertRaw(tc);

        // 场景 1: filter = null -> 管理员全见 (应至少看到 3 条)
        long adminCount = mapper.countByCondition(null, null, null, null, null);
        assertThat(adminCount).isGreaterThanOrEqualTo(3L);

        // 场景 2: filter = "AND started_by='USER_A'" -> 只应返回 USER_A 的数据
        String filter = "AND started_by = 'USER_A'";
        List<PerfRunTask> userAList = mapper.selectByCondition(null, null, null, null, filter, 0, 100);
        assertThat(userAList).isNotEmpty();
        assertThat(userAList).extracting(PerfRunTask::getStartedBy).containsOnly("USER_A");
        // 至少包含本测试的 DS_A
        assertThat(userAList).extracting(PerfRunTask::getTaskKey)
                .contains("TEST_RT_DS_A");
        // 不应包含 USER_B / USER_C 的记录
        assertThat(userAList).extracting(PerfRunTask::getTaskKey)
                .doesNotContain("TEST_RT_DS_B", "TEST_RT_DS_C");

        // 场景 3: count 一致性 —— filter 下 count 仅统计 USER_A
        long userACount = mapper.countByCondition(null, null, null, null, filter);
        assertThat(userACount).isEqualTo(userAList.size());
    }

    @Test
    @DisplayName("selectByCondition 可按 taskType 过滤")
    void selectByCondition_filterByTaskType_ok() {
        PerfRunTask run = RunTaskTestDataBuilder.task("TYPE_RUN", "METRIC_RUN",
                LocalDate.now(), "RUNNING", "USER_TYP");
        PerfRunTask trial = RunTaskTestDataBuilder.task("TYPE_TRIAL", "METRIC_TRIAL",
                LocalDate.now(), "RUNNING", "USER_TYP");
        insertRaw(run);
        insertRaw(trial);

        String filter = "AND started_by = 'USER_TYP'";
        List<PerfRunTask> list = mapper.selectByCondition("METRIC_TRIAL", null, null, null, filter, 0, 100);

        assertThat(list).extracting(PerfRunTask::getTaskType).containsOnly("METRIC_TRIAL");
        assertThat(list).extracting(PerfRunTask::getTaskKey).contains("TEST_RT_TYPE_TRIAL");
        assertThat(list).extracting(PerfRunTask::getTaskKey).doesNotContain("TEST_RT_TYPE_RUN");
    }

    @Test
    @DisplayName("selectByCondition 可按 status 过滤")
    void selectByCondition_filterByStatus_ok() {
        PerfRunTask running = RunTaskTestDataBuilder.task("ST_RUN", "METRIC_RUN",
                LocalDate.now(), "RUNNING", "USER_ST");
        PerfRunTask success = RunTaskTestDataBuilder.task("ST_SUC", "METRIC_RUN",
                LocalDate.now(), "SUCCESS", "USER_ST");
        insertRaw(running);
        insertRaw(success);

        String filter = "AND started_by = 'USER_ST'";
        List<PerfRunTask> list = mapper.selectByCondition(null, null, "SUCCESS", null, filter, 0, 100);

        assertThat(list).extracting(PerfRunTask::getStatus).containsOnly("SUCCESS");
        assertThat(list).extracting(PerfRunTask::getTaskKey).contains("TEST_RT_ST_SUC");
        assertThat(list).extracting(PerfRunTask::getTaskKey).doesNotContain("TEST_RT_ST_RUN");
    }

    @Test
    @DisplayName("countByTypeAndDate 按类型与日期统计")
    void countByTypeAndDate_ok() {
        LocalDate today = LocalDate.now();
        LocalDate yesterday = today.minusDays(1);
        // 今日 METRIC_RUN 2 条
        insertRaw(RunTaskTestDataBuilder.task("CNT_A", "METRIC_RUN", today, "RUNNING", "USER_CNT"));
        insertRaw(RunTaskTestDataBuilder.task("CNT_B", "METRIC_RUN", today, "SUCCESS", "USER_CNT"));
        // 今日 KPI_RUN 1 条（应不计入 METRIC_RUN）
        insertRaw(RunTaskTestDataBuilder.task("CNT_C", "KPI_RUN", today, "RUNNING", "USER_CNT"));
        // 昨日 METRIC_RUN 1 条（应不计入今日）
        insertRaw(RunTaskTestDataBuilder.task("CNT_D", "METRIC_RUN", yesterday, "SUCCESS", "USER_CNT"));

        long count = mapper.countByTypeAndDate("METRIC_RUN", today);

        // 断言 ≥ 2（本测试插入的 2 条；其他事务已回滚，同测试类中只有本方法插入今日 METRIC_RUN）
        assertThat(count).isGreaterThanOrEqualTo(2L);
    }

    @Test
    @DisplayName("countByCondition 与 selectByCondition 过滤条件一致")
    void countByCondition_sameAsSelect() {
        // 3 条 RUNNING + 2 条 SUCCESS，全部 started_by = USER_CMP
        insertRaw(RunTaskTestDataBuilder.task("CMP_1", "METRIC_RUN", LocalDate.now(), "RUNNING", "USER_CMP"));
        insertRaw(RunTaskTestDataBuilder.task("CMP_2", "METRIC_RUN", LocalDate.now(), "RUNNING", "USER_CMP"));
        insertRaw(RunTaskTestDataBuilder.task("CMP_3", "METRIC_RUN", LocalDate.now(), "RUNNING", "USER_CMP"));
        insertRaw(RunTaskTestDataBuilder.task("CMP_4", "METRIC_RUN", LocalDate.now(), "SUCCESS", "USER_CMP"));
        insertRaw(RunTaskTestDataBuilder.task("CMP_5", "METRIC_RUN", LocalDate.now(), "SUCCESS", "USER_CMP"));

        String filter = "AND started_by = 'USER_CMP'";
        List<PerfRunTask> list = mapper.selectByCondition(null, null, "RUNNING", null, filter, 0, 100);
        long count = mapper.countByCondition(null, null, "RUNNING", null, filter);

        assertThat(count).isEqualTo(list.size());
        assertThat(count).isEqualTo(3L);
    }
}
