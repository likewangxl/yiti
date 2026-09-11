package com.bank.branch.platform.performance.mapper;

import com.bank.branch.platform.performance.controller.dto.MetricSummaryDTO;
import com.bank.branch.platform.performance.entity.PerfRunTask;
import com.bank.branch.platform.performance.service.dto.RunTaskQuery;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 绩效任务执行日志 Mapper.
 *
 * <p>V1.0 限定只读场景：前端/下游 Api 读取任务执行日志（列表 / 详情 / 计数）。
 * <p>V1.1 Task P2.4 起扩展写方法（{@link #insert}, {@link #updateStatus}），由计算引擎调用。
 *
 * <p><strong>安全 (SQL 注入) 注意</strong>：
 * <ul>
 *   <li>{@link #selectByCondition} 与 {@link #countByCondition} 的 {@code dataScopeFilter} 参数
 *       在 XML 中以 <code>${dataScopeFilter}</code> 方式直接拼接到 SQL（为 common-dev-guide §5 允许的
 *       合法例外：数据范围 SQL 片段注入）。</li>
 *   <li>调用方 <strong>必须</strong> 保证该参数由 common-security 的 {@code DataScopeApi} 可信生成，
 *       <strong>禁止</strong> 接受任何用户输入直接拼入，否则将形成 SQL 注入漏洞。</li>
 *   <li>所有其他参数一律使用 {@code #{}} 预编译占位符。</li>
 * </ul>
 * <p>insert / selectById 由 MyBatis-Plus BaseMapper 提供.
 */
@Mapper
public interface PerfRunTaskMapper extends BaseMapper<PerfRunTask> {

    /**
     * 按任务编号（task_key，作为 V1.0 的业务唯一标识）查询.
     *
     * <p>V1.0 语义：{@code task_key} 既承担"关键键"（如 metric_code），也承担"任务编号"。
     * V1.1 若 DDL 新增独立 {@code task_no} 字段，仅需改 XML 底层列名，接口保持不变.
     *
     * @param taskNo 任务编号（对齐 DDL {@code task_key} 列）
     * @return 任务日志，不存在返回 null
     */
    PerfRunTask selectByTaskNo(@Param("taskNo") String taskNo);

    /**
     * 条件分页查询（支持数据范围 SQL 片段注入）.
     *
     * <p>普通用户场景：{@code dataScopeFilter} 应由 DataScopeApi 传回 {@code " AND started_by = '#{empId}' "} 之类的片段；
     * 管理员场景：{@code dataScopeFilter} 传 {@code null} 表示全见。
     *
     * <p>V1.13 起过滤条件收敛到 {@link RunTaskQuery}（新增 triggerType/startedBy/dataDateFrom/dataDateTo），
     * 避免方法签名随过滤维度增长持续膨胀。
     *
     * @param q               查询过滤条件（nullable 字段表示不过滤）
     * @param dataScopeFilter 数据范围 SQL 片段（nullable；管理员全见时传 null）
     * @param offset          偏移量
     * @param limit           每页大小
     * @return 任务日志列表
     */
    List<PerfRunTask> selectByCondition(@Param("q") RunTaskQuery q,
                                        @Param("dataScopeFilter") String dataScopeFilter,
                                        @Param("offset") int offset,
                                        @Param("limit") int limit);

    /**
     * 条件计数（与 {@link #selectByCondition} 过滤条件保持一致，包含同样的数据范围片段）.
     *
     * @param q               查询过滤条件（nullable 字段表示不过滤）
     * @param dataScopeFilter 数据范围 SQL 片段（nullable）
     * @return 总数
     */
    long countByCondition(@Param("q") RunTaskQuery q,
                          @Param("dataScopeFilter") String dataScopeFilter);

    /**
     * 按类型 + 日期统计（V1.1 SysControl 切版前置校验使用：判断当日是否仍有 RUNNING 任务）.
     *
     * @param taskType 任务类型
     * @param dataDate 数据日期
     * @return 任务数
     */
    long countByTypeAndDate(@Param("taskType") String taskType,
                            @Param("dataDate") LocalDate dataDate);

    /**
     * 按 task_key(指标) 分组汇总（任务监控列表）.
     *
     * <p>LEFT JOIN perf_metric_def 取指标名并支持"编码或名称"关键字模糊；
     * {@code dataScopeFilter} 为可信数据范围片段（形如 " AND started_by = 'xxx' "），
     * 仅由 Service 层 resolveScopeFilter 生成，禁止用户输入拼入。
     *
     * @param taskType        任务类型（如 METRIC_RUN）
     * @param keyword         关键字（nullable；匹配 task_key 或 metric_name）
     * @param dataScopeFilter 数据范围 SQL 片段（nullable）
     * @param offset          偏移
     * @param limit           每页
     */
    List<MetricSummaryDTO> selectMetricSummary(@Param("taskType") String taskType,
                                               @Param("keyword") String keyword,
                                               @Param("dataScopeFilter") String dataScopeFilter,
                                               @Param("offset") int offset,
                                               @Param("limit") int limit);

    /** 与 {@link #selectMetricSummary} 同过滤条件的分组计数（DISTINCT task_key 数）. */
    long countMetricSummary(@Param("taskType") String taskType,
                            @Param("keyword") String keyword,
                            @Param("dataScopeFilter") String dataScopeFilter);

    /**
     * 更新任务状态（V1.1 Task P2.4 起由计算引擎调用）.
     *
     * <p>当 status=SUCCESS/FAILED 时同时更新 end_time=NOW() 与 error_msg（可空）。
     * errorMsg 为 null 时清空 error_msg 列。
     *
     * @param id       任务 ID
     * @param status   新状态（RUNNING/SUCCESS/FAILED/PARTIAL/CANCELLED）
     * @param errorMsg 错误信息（可选）
     * @return 受影响行数
     */
    int updateStatus(@Param("id") String id,
                     @Param("status") String status,
                     @Param("errorMsg") String errorMsg);

    /**
     * 更新任务的 result_preview_json 字段（V1.1 P8 Task C.1 新增）.
     *
     * <p>用途：HistoryRecalcService 在所有子任务完成后，把 childTaskIds 的 JSON 数组
     * 写回父 task，供下游从父 task 反查子任务清单（V1.1 简化方案；V1.2 引入 parent_id 后
     * 此方法可下线）。
     *
     * <p>注意：本方法不动 status / end_time / error_msg，仅覆盖 result_preview_json 一列。
     *
     * @param id                 任务 ID
     * @param resultPreviewJson  结果预览 JSON 字符串（通常为 childTaskIds 数组）
     * @return 受影响行数
     */
    int updateResultPreviewJson(@Param("id") String id,
                                @Param("resultPreviewJson") String resultPreviewJson);

    /**
     * V1.7：updateStatus + 同步写 params_json（多主体计算终态专用）.
     *
     * <p>与 {@link #updateStatus} 的区别：额外将主体统计信息写入 params_json 列，
     * 供运维和下游事件消费方（KpiCascadeListener）读取 subjectTotal/subjectFailed 等字段。
     *
     * @param id         任务 ID
     * @param status     新状态（SUCCESS / PARTIAL_FAILED / FAILED）
     * @param errorMsg   错误信息（可空）
     * @param paramsJson 主体统计 JSON（由 SubjectStats.toJson() 生成）
     * @return 受影响行数
     */
    int updateStatusWithParams(@Param("id") String id,
                               @Param("status") String status,
                               @Param("errorMsg") String errorMsg,
                               @Param("paramsJson") String paramsJson);

    /** 更新分行批次最终选定的业务日和数据版本，不修改全局 SYS_CONTROL。 */
    int updateBatchContext(@Param("id") String id,
                           @Param("dataDate") LocalDate dataDate,
                           @Param("dataVersion") String dataVersion);

    /**
     * 删除 cutoff 之前全部 {@code status = 'SUCCESS'} 的 run_task（V1.2 Task Q5.2）.
     *
     * <p>FAILED / RUNNING / PENDING / PARTIAL / CANCELLED 状态不删，保留给失败诊断与
     * 异常回溯。筛选字段为 {@code end_time}（SUCCESS 任务必然有终态 end_time，
     * RUNNING 任务的 end_time 为 NULL，自然不会命中）.
     *
     * @param cutoffTime 截止时刻，{@code end_time < cutoffTime} 的 SUCCESS 任务会被删
     * @return 删除的行数
     */
    int deleteSuccessTasksBefore(@Param("cutoffTime") LocalDateTime cutoffTime);
}
