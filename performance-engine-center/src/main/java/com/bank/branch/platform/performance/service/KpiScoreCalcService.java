package com.bank.branch.platform.performance.service;

import com.bank.branch.platform.performance.entity.PerfKpiItem;
import com.bank.branch.platform.performance.entity.PerfKpiScheme;
import com.bank.branch.platform.performance.entity.PerfKpiCalcLog;
import com.bank.branch.platform.performance.entity.PerfKpiScore;
import com.bank.branch.platform.performance.entity.PerfMetricCalcTask;
import com.bank.branch.platform.performance.entity.PerfMetricDef;
import com.bank.branch.platform.performance.entity.PerfTargetPlan;
import com.bank.branch.platform.performance.entity.PerfTargetValue;
import com.bank.branch.platform.performance.enums.PerfErrorCode;
import com.bank.branch.platform.performance.exception.PerfException;
import com.bank.branch.platform.performance.mapper.CustIndexResultMapper;
import com.bank.branch.platform.performance.mapper.EmpIndexResultMapper;
import com.bank.branch.platform.performance.mapper.OrgIndexResultMapper;
import com.bank.branch.platform.performance.mapper.PerfKpiItemMapper;
import com.bank.branch.platform.performance.mapper.PerfKpiSchemeMapper;
import com.bank.branch.platform.performance.mapper.PerfKpiScoreMapper;
import com.bank.branch.platform.performance.mapper.PerfMetricCalcTaskMapper;
import com.bank.branch.platform.performance.mapper.PerfTargetPlanMapper;
import com.bank.branch.platform.performance.mapper.PerfTargetValueMapper;
import com.bank.branch.platform.performance.mapper.SubjectSlotValueRow;
import com.bank.branch.platform.performance.mapper.KpiSubjectGroupRow;
import com.bank.branch.platform.performance.mapper.KpiScopeFilter;
import com.bank.branch.platform.common.security.context.DataScopeContext;
import com.bank.branch.platform.common.security.enums.BizType;
import com.bank.branch.platform.performance.controller.dto.KpiScoreGroupPageDTO;
import com.bank.branch.platform.performance.controller.dto.KpiScoreGroupRowDTO;
import com.bank.branch.platform.performance.controller.dto.KpiScoreMetricCellDTO;
import com.bank.branch.platform.performance.controller.dto.MetricOptionDTO;
import com.bank.branch.platform.performance.service.engine.SqlExecutor;
import com.bank.branch.platform.auth.api.OrgApi;
import com.bank.branch.platform.auth.api.UserApi;
import com.bank.branch.platform.auth.api.dto.OrgDTO;
import com.bank.branch.platform.auth.api.dto.UserDTO;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * KPI 分值计算服务（后台定时任务 + 前端重算共用入口）.
 *
 * <p>口径（按业务规格）：
 * <ol>
 *   <li>往 {@code PERF_METRIC_CALC_TASK} 登记任务开始（任务名 / KPI方案编号 / 数据日期 / 开始时间 / 状态=RUNNING）；</li>
 *   <li>前置检查：同数据日期下 1/2/3 级指标批量计算是否都有 SUCCESS 记录，缺任一即把任务置 FAILED 并记录原因；</li>
 *   <li>取 ACTIVE（已发布）KPI 方案（指定 schemeCode 时仅算该方案），逐方案逐指标逐对象计分：
 *       <ul>
 *         <li>实际值 = 指标维度结果宽表中该日期 + 该 slot 的对象值（同对象多版本取最新）；</li>
 *         <li>目标值 / 基础值 = KPI 方案关联目标方案（{@code perf_target_plan.kpi_scheme_id}）下，
 *             按对象 + 指标 + 周期键匹配的 {@code perf_target_value}，未匹配默认 0；</li>
 *         <li>得分 = 指标项配置的计分公式（{@code PERF_KPI_ITEM.formula}）代入 actual/target/base/weight 求值；</li>
 *         <li>结果 upsert 到 {@code PERF_KPI_SCORE}（唯一键命中则更新）；</li>
 *       </ul>
 *   </li>
 *   <li>全部完成 → 任务 SUCCESS + 结束时间；中途任一 KPI 计算抛异常 → 立即停止，任务 FAILED + 原因 + 结束时间。</li>
 * </ol>
 *
 * <p><strong>事务边界</strong>：与 {@link KpiCalcService} / {@link MetricBatchCalcService} 一致，
 * 本服务<em>不包</em> {@code @Transactional}——失败任务的 FAILED 痕迹需独立写入，避免大事务回滚抹掉。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class KpiScoreCalcService {

    private static final String TASK_TYPE = "KPI_SCORE_CALC";
    private static final String TASK_NAME = "KPI分值计算";
    /** KPI 计分 SQL 表达式单行执行超时（秒）. */
    private static final Duration SQL_EXPR_TIMEOUT = Duration.ofSeconds(30);
    /** 导出行数上限（防 OOM）. */
    public static final int EXPORT_ROWS_CAP = 50000;

    private final PerfMetricCalcTaskMapper taskMapper;
    private final PerfKpiSchemeMapper schemeMapper;
    private final PerfKpiItemMapper itemMapper;
    private final PerfTargetPlanMapper targetPlanMapper;
    private final PerfTargetValueMapper targetValueMapper;
    private final PerfKpiScoreMapper scoreMapper;
    private final MetricDefService metricDefService;
    private final KpiScoreFormulaService formulaService;
    /** 解析对象名称：员工姓名（auth 用户，subject_id=工号=PT_USER.username）/ 机构名称 + 机构号（auth 机构）. */
    private final UserApi userApi;
    private final OrgApi orgApi;
    private final EmpIndexResultMapper empIndexResultMapper;
    private final OrgIndexResultMapper orgIndexResultMapper;
    private final CustIndexResultMapper custIndexResultMapper;
    /** KPI 方案级计算记录：每方案处理完/异常结束落一条. */
    private final com.bank.branch.platform.performance.mapper.PerfKpiCalcLogMapper kpiCalcLogMapper;
    /** KPI 计分 SQL 表达式执行器（结果列 obj_id + kpi_value）. */
    private final SqlExecutor sqlExecutor;

    /**
     * 执行 KPI 分值计算.
     *
     * @param dataDate    数据日期（必填）
     * @param schemeCode  KPI 方案编码（可空，空=全部 ACTIVE 方案）
     * @param triggerType 触发方式 AUTO 自动 / MANUAL 手动（空按 MANUAL）
     * @param triggerBy   触发人工号（PT_USER.username，手动触发填，自动触发为空）
     * @return 任务流水 ID
     * @throws PerfException dataDate 为空 / 前置依赖未完成 / 中途计算失败（任务已置 FAILED）
     */
    public String calculate(LocalDate dataDate, String schemeCode, String triggerType, String triggerBy) {
        String normalizedTrigger = "AUTO".equalsIgnoreCase(triggerType) ? "AUTO" : "MANUAL";
        if (dataDate == null) {
            throw new PerfException(PerfErrorCode.VALIDATION_FAILED, "数据日期不能为空");
        }
        String normalizedScheme = StringUtils.hasText(schemeCode) ? schemeCode.trim() : null;

        String taskId = UUID.randomUUID().toString().replace("-", "");
        PerfMetricCalcTask task = new PerfMetricCalcTask();
        task.setId(taskId);
        task.setTaskName(TASK_NAME);
        task.setTaskType(TASK_TYPE);
        task.setKpiSchemeCode(normalizedScheme);
        task.setDataDate(dataDate);
        task.setStatus("RUNNING");
        task.setStartTime(LocalDateTime.now());
        task.setTotalCount(0);
        task.setSuccessCount(0);
        task.setFailCount(0);
        task.setSkipCount(0);
        taskMapper.insert(task);
        log.info("========== 【KPI分值计算】开始 taskId={}, dataDate={}, schemeCode={} ==========",
                taskId, dataDate, normalizedScheme);

        try {
            // 1. 前置依赖检查：1/2/3 级指标当日均已完成（有 SUCCESS 记录）
            for (int level = 1; level <= 3; level++) {
                boolean done = taskMapper.selectCount(new LambdaQueryWrapper<PerfMetricCalcTask>()
                        .eq(PerfMetricCalcTask::getDataDate, dataDate)
                        .eq(PerfMetricCalcTask::getMetricLevel, level)
                        .eq(PerfMetricCalcTask::getStatus, "SUCCESS")) > 0;
                if (!done) {
                    String reason = "前置依赖检查失败：" + level + "级指标在数据日期 " + dataDate
                            + " 尚未完成计算（无SUCCESS记录），KPI分值计算中止";
                    log.error("【KPI分值计算】{}", reason);
                    finishTask(task, "FAILED", reason);
                    throw new PerfException(PerfErrorCode.VALIDATION_FAILED, reason);
                }
            }
            log.info("【KPI分值计算】前置依赖检查通过（1/2/3级指标均已完成）");

            // 2. 取待计算 KPI 方案
            List<PerfKpiScheme> schemes;
            if (normalizedScheme != null) {
                PerfKpiScheme one = schemeMapper.selectBySchemeCode(normalizedScheme);
                if (one == null) {
                    String reason = "KPI方案不存在：" + normalizedScheme;
                    log.error("【KPI分值计算】{}", reason);
                    finishTask(task, "FAILED", reason);
                    throw new PerfException(PerfErrorCode.KPI_SCHEME_NOT_FOUND, normalizedScheme);
                }
                schemes = List.of(one);
            } else {
                schemes = schemeMapper.selectByCondition(null, "ACTIVE", null, null, 0, 100000);
            }
            log.info("【KPI分值计算】待计算方案数={}", schemes.size());

            // 3. 逐方案计算（方案级隔离：单方案失败记录错误后继续处理下一个方案，不中止整任务）
            int schemeSuccess = 0;
            int schemeFail = 0;
            int totalScored = 0;
            int totalSkipped = 0;
            for (PerfKpiScheme scheme : schemes) {
                // 每个方案落一条方案级记录（成功/失败各记一条到 PERF_KPI_CALC_LOG）
                LocalDateTime schemeStart = LocalDateTime.now();
                try {
                    SchemeStat stat = calcOneScheme(scheme, dataDate);
                    schemeSuccess++;
                    totalScored += stat.scored;
                    totalSkipped += stat.skipped;
                    insertSchemeLog(dataDate, scheme.getSchemeCode(), normalizedTrigger, triggerBy,
                            schemeStart, "SUCCESS", stat.scored, stat.skipped, null, taskId);
                } catch (Exception schemeEx) {
                    // 单方案计算失败：记录错误信息后继续处理下一个方案（不再 fail-fast 中止整任务）
                    schemeFail++;
                    log.error("【KPI分值计算】方案 {} 计算失败，已记录并继续下一个方案: {}",
                            scheme.getSchemeCode(), schemeEx.getMessage(), schemeEx);
                    insertSchemeLog(dataDate, scheme.getSchemeCode(), normalizedTrigger, triggerBy,
                            schemeStart, "FAILED", 0, 0, schemeEx.getMessage(), taskId);
                }
            }

            task.setTotalCount(schemes.size());
            task.setSuccessCount(schemeSuccess);
            task.setFailCount(schemeFail);
            task.setSkipCount(totalSkipped);
            // 全部方案失败才算任务 FAILED；否则任务完成（含部分失败），失败明细已逐方案落 PERF_KPI_CALC_LOG
            boolean allFailed = schemeFail > 0 && schemeSuccess == 0;
            String taskErr = allFailed
                    ? ("KPI分值计算失败：全部 " + schemeFail + " 个方案计算失败")
                    : null;
            finishTask(task, allFailed ? "FAILED" : "SUCCESS", taskErr);
            log.info("========== 【KPI分值计算】完成 taskId={}, 成功方案={}, 失败方案={}, 计分对象={}, 跳过项={} ==========",
                    taskId, schemeSuccess, schemeFail, totalScored, totalSkipped);
            return taskId;
        } catch (PerfException pe) {
            // 前置检查 / 方案不存在已在上面 finishTask；这里兜底（避免重复写时 status 已是 FAILED 也无妨）
            if (!"FAILED".equals(task.getStatus())) {
                finishTask(task, "FAILED", "KPI分值计算失败：" + pe.getMessage());
            }
            log.error("【KPI分值计算】任务失败 taskId={}: {}", taskId, pe.getMessage());
            throw pe;
        } catch (Exception e) {
            finishTask(task, "FAILED", "KPI分值计算失败：" + e.getMessage());
            log.error("【KPI分值计算】任务异常 taskId={}: {}", taskId, e.getMessage(), e);
            throw new PerfException(PerfErrorCode.CALC_JOB_FAILED, e, "KPI分值计算失败：" + e.getMessage());
        }
    }

    /**
     * 考核计算页面统计：从 {@code PERF_METRIC_CALC_TASK} 取 KPI 计算任务（task_type=KPI_SCORE_CALC）
     * 的「最后一次任务成功数/失败数/耗时」+「本月任务数」.
     *
     * @return 统计 DTO（无任务时各"最后一次"字段为 null）
     */
    public com.bank.branch.platform.performance.controller.dto.KpiScoreStatsDTO getStats() {
        com.bank.branch.platform.performance.controller.dto.KpiScoreStatsDTO dto =
                new com.bank.branch.platform.performance.controller.dto.KpiScoreStatsDTO();
        // 本月 KPI 计算任务数（按 start_time 落在本月）
        LocalDateTime monthStart = LocalDate.now().withDayOfMonth(1).atStartOfDay();
        Long monthCount = taskMapper.selectCount(new LambdaQueryWrapper<PerfMetricCalcTask>()
                .eq(PerfMetricCalcTask::getTaskType, TASK_TYPE)
                .ge(PerfMetricCalcTask::getStartTime, monthStart));
        dto.setMonthTaskCount(monthCount == null ? 0L : monthCount);
        // 最后一次 KPI 计算任务
        List<PerfMetricCalcTask> last = taskMapper.selectList(new LambdaQueryWrapper<PerfMetricCalcTask>()
                .eq(PerfMetricCalcTask::getTaskType, TASK_TYPE)
                .orderByDesc(PerfMetricCalcTask::getStartTime)
                .last("LIMIT 1"));
        if (last != null && !last.isEmpty()) {
            PerfMetricCalcTask t = last.get(0);
            dto.setLastSuccessCount(t.getSuccessCount());
            dto.setLastFailCount(t.getFailCount());
            dto.setLastStatus(t.getStatus());
            dto.setLastDataDate(t.getDataDate());
            dto.setLastEndTime(t.getEndTime());
            if (t.getStartTime() != null && t.getEndTime() != null) {
                dto.setLastDurationMs(java.time.Duration.between(t.getStartTime(), t.getEndTime()).toMillis());
            }
        }
        return dto;
    }

    /**
     * 考核计算页面数据列表：分页查询 KPI 方案级计算记录（PERF_KPI_CALC_LOG），
     * 支持按数据日期 + KPI方案编码过滤，按 id 倒序（最近在前）.
     *
     * @param dataDate   数据日期（可空）
     * @param schemeCode KPI 方案编码（可空）
     * @param pageNo     页码（&ge;1）
     * @param pageSize   每页条数（1..100）
     * @return 分页结果
     */
    /**
     * 考核计算记录中的最大数据日期（供前端默认选中并展示最新一日的数据列表）.
     *
     * @return 最大 data_date；无记录时返回 null
     */
    public LocalDate getLatestLogDataDate() {
        return kpiCalcLogMapper.selectMaxDataDate();
    }

    public com.bank.branch.platform.common.web.PageResult<
            com.bank.branch.platform.performance.controller.dto.PerfKpiCalcLogDTO> pageLogs(
            LocalDate dataDate, String schemeCode, int pageNo, int pageSize) {
        int safeNo = Math.max(1, pageNo);
        int safeSize = Math.min(100, Math.max(1, pageSize));
        LambdaQueryWrapper<PerfKpiCalcLog> w = new LambdaQueryWrapper<>();
        if (dataDate != null) {
            w.eq(PerfKpiCalcLog::getDataDate, dataDate);
        }
        if (StringUtils.hasText(schemeCode)) {
            w.eq(PerfKpiCalcLog::getSchemeCode, schemeCode.trim());
        }
        Long total = kpiCalcLogMapper.selectCount(w);
        // selectCount 后再加排序 + LIMIT，避免污染 count 查询
        w.orderByDesc(PerfKpiCalcLog::getId)
         .last("LIMIT " + ((safeNo - 1) * safeSize) + ", " + safeSize);
        List<com.bank.branch.platform.performance.controller.dto.PerfKpiCalcLogDTO> dtos =
                kpiCalcLogMapper.selectList(w).stream().map(this::toLogDto).toList();
        enrichTriggerByNames(dtos);
        return com.bank.branch.platform.common.web.PageResult.of(
                safeNo, safeSize, total == null ? 0L : total, dtos);
    }

    /**
     * 回填触发人中文姓名：triggerBy 为工号(username)，按 username 批量查 auth 用户取 displayName.
     * 自动触发(triggerBy 为空)不解析。名称解析失败仅展示工号，不阻塞列表。
     */
    private void enrichTriggerByNames(
            List<com.bank.branch.platform.performance.controller.dto.PerfKpiCalcLogDTO> dtos) {
        if (dtos.isEmpty()) {
            return;
        }
        List<String> usernames = dtos.stream()
                .map(com.bank.branch.platform.performance.controller.dto.PerfKpiCalcLogDTO::getTriggerBy)
                .filter(StringUtils::hasText).distinct().toList();
        if (usernames.isEmpty()) {
            return;
        }
        Map<String, String> nameByUsername = new java.util.HashMap<>();
        try {
            List<UserDTO> users = userApi.getUsersByUsernames(usernames);
            if (users != null) {
                for (UserDTO u : users) {
                    if (u != null && StringUtils.hasText(u.getUsername())) {
                        nameByUsername.put(u.getUsername(), u.getDisplayName());
                    }
                }
            }
        } catch (Exception ignore) {
            // 名称解析失败：仅展示工号
        }
        // trigger_by 历史上可能存 user_id(如 E40001) 而非工号(username)；按 username 未命中的再按 user_id 兜底，
        // 命中后用真实工号(username)覆盖展示值 triggerBy（仅改响应、不改落库）并回填姓名，保证前端展示「工号 + 姓名」
        java.util.Map<String, UserDTO> userById = new java.util.HashMap<>();
        List<String> unresolved = usernames.stream()
                .filter(k -> !nameByUsername.containsKey(k)).toList();
        if (!unresolved.isEmpty()) {
            try {
                List<UserDTO> byId = userApi.getUserByEmpIds(unresolved);
                if (byId != null) {
                    for (UserDTO u : byId) {
                        if (u != null && StringUtils.hasText(u.getEmpId())) {
                            userById.put(u.getEmpId(), u);
                        }
                    }
                }
            } catch (Exception ignore) {
                // user_id 兜底失败：仅展示工号
            }
        }
        for (com.bank.branch.platform.performance.controller.dto.PerfKpiCalcLogDTO d : dtos) {
            String tb = d.getTriggerBy();
            if (!StringUtils.hasText(tb)) {
                continue;
            }
            if (nameByUsername.containsKey(tb)) {
                d.setTriggerByName(nameByUsername.get(tb));
            } else if (userById.containsKey(tb)) {
                UserDTO u = userById.get(tb);
                if (StringUtils.hasText(u.getUsername())) {
                    d.setTriggerBy(u.getUsername()); // 用真实工号覆盖展示（落库不变）
                }
                d.setTriggerByName(u.getDisplayName());
            }
        }
    }

    /** PerfKpiCalcLog 实体 → 列表 DTO. */
    private com.bank.branch.platform.performance.controller.dto.PerfKpiCalcLogDTO toLogDto(PerfKpiCalcLog e) {
        com.bank.branch.platform.performance.controller.dto.PerfKpiCalcLogDTO d =
                new com.bank.branch.platform.performance.controller.dto.PerfKpiCalcLogDTO();
        d.setId(e.getId());
        d.setDataDate(e.getDataDate());
        d.setSchemeCode(e.getSchemeCode());
        d.setTriggerType(e.getTriggerType());
        d.setTriggerBy(e.getTriggerBy());
        d.setResult(e.getResult());
        d.setStartTime(e.getStartTime());
        d.setEndTime(e.getEndTime());
        d.setScoredCount(e.getScoredCount());
        d.setSkippedCount(e.getSkippedCount());
        d.setErrorMsg(e.getErrorMsg());
        return d;
    }

    /**
     * KPI 计算结果详情：分页查询某数据日期 + KPI方案下的计分明细（PERF_KPI_SCORE），
     * 按 维度（subject_type）/ 指标 / 对象 排序，供详情页展示.
     *
     * @param dataDate   数据日期（必填）
     * @param schemeCode KPI 方案编码（必填）
     * @param pageNo     页码（&ge;1）
     * @param pageSize   每页条数（1..100）
     * @return 分页结果
     */
    public com.bank.branch.platform.common.web.PageResult<
            com.bank.branch.platform.performance.controller.dto.PerfKpiScoreResultDTO> pageScores(
            LocalDate dataDate, String schemeCode, String metricCode, String subjectType, int pageNo, int pageSize) {
        int safeNo = Math.max(1, pageNo);
        int safeSize = Math.min(100, Math.max(1, pageSize));
        LambdaQueryWrapper<PerfKpiScore> w = new LambdaQueryWrapper<>();
        if (dataDate != null) {
            w.eq(PerfKpiScore::getDataDate, dataDate);
        }
        if (StringUtils.hasText(schemeCode)) {
            w.eq(PerfKpiScore::getSchemeCode, schemeCode.trim());
        }
        if (StringUtils.hasText(metricCode)) {
            w.eq(PerfKpiScore::getMetricCode, metricCode.trim());
        }
        if (StringUtils.hasText(subjectType)) {
            w.eq(PerfKpiScore::getSubjectType, subjectType.trim());
        }
        Long total = scoreMapper.selectCount(w);
        w.orderByAsc(PerfKpiScore::getMetricCode)
         .orderByAsc(PerfKpiScore::getSubjectType)
         .orderByAsc(PerfKpiScore::getSubjectId)
         .last("LIMIT " + ((safeNo - 1) * safeSize) + ", " + safeSize);
        List<com.bank.branch.platform.performance.controller.dto.PerfKpiScoreResultDTO> dtos =
                scoreMapper.selectList(w).stream().map(this::toScoreDto).toList();
        enrichScoreNames(dtos);
        return com.bank.branch.platform.common.web.PageResult.of(
                safeNo, safeSize, total == null ? 0L : total, dtos);
    }

    /**
     * 导出用：某数据日期 + 方案下的全部计分明细（PERF_KPI_SCORE 平铺记录，按 cap 上限），
     * 含指标名称 / 对象姓名回填，供「导出KPI明细数据」.
     *
     * @param dataDate    数据日期
     * @param schemeCode  方案编码
     * @param subjectType 维度过滤（可空）
     * @param cap         行数上限
     * @return 明细 DTO 列表
     */
    public List<com.bank.branch.platform.performance.controller.dto.PerfKpiScoreResultDTO> listScoresForExport(
            LocalDate dataDate, String schemeCode, String subjectType, int cap) {
        int safeCap = Math.max(1, cap);
        LambdaQueryWrapper<PerfKpiScore> w = new LambdaQueryWrapper<>();
        if (dataDate != null) {
            w.eq(PerfKpiScore::getDataDate, dataDate);
        }
        if (StringUtils.hasText(schemeCode)) {
            w.eq(PerfKpiScore::getSchemeCode, schemeCode.trim());
        }
        if (StringUtils.hasText(subjectType)) {
            w.eq(PerfKpiScore::getSubjectType, subjectType.trim());
        }
        // 考核计算(KPI_CALC)数据范围过滤
        KpiScopeFilter scope = resolveKpiScopeFilter();
        if (!scope.isScopeAll()) {
            boolean hasOrg = scope.getOrgCodes() != null && !scope.getOrgCodes().isEmpty();
            boolean hasEmp = scope.getEmpIds() != null && !scope.getEmpIds().isEmpty();
            if (!hasOrg && !hasEmp) {
                return List.of();
            }
            w.and(qw -> qw
                    .nested(hasOrg, n -> n.eq(PerfKpiScore::getSubjectType, "ORG")
                            .in(PerfKpiScore::getSubjectId, scope.getOrgCodes()))
                    .or(hasOrg && hasEmp)
                    .nested(hasEmp, n -> n.eq(PerfKpiScore::getSubjectType, "EMP")
                            .in(PerfKpiScore::getSubjectId, scope.getEmpIds())));
        }
        w.orderByAsc(PerfKpiScore::getSubjectType)
         .orderByAsc(PerfKpiScore::getSubjectId)
         .orderByAsc(PerfKpiScore::getMetricCode)
         .last("LIMIT " + safeCap);
        List<com.bank.branch.platform.performance.controller.dto.PerfKpiScoreResultDTO> dtos =
                scoreMapper.selectList(w).stream().map(this::toScoreDto).collect(java.util.stream.Collectors.toList());
        enrichScoreNames(dtos);
        return dtos;
    }

    /**
     * KPI 计算结果详情（按对象分组）：在 PERF_KPI_SCORE 中按 (数据日期, KPI编码) 下
     * 对 (对象ID, 对象类型) group by，每个对象一行——对象ID / 姓名 / 考核得分(该对象所有指标合计)，
     * 之后动态展开该方案所有指标（按指标名排序），每指标格含 实际值/目标值/基础值/完成率/得分.
     *
     * @param dataDate    数据日期（必填）
     * @param schemeCode  KPI 方案编码（必填）
     * @param subjectType 对象类型过滤（可空=全部维度）
     * @param pageNo      页码（&ge;1，按对象分页）
     * @param pageSize    每页对象数（1..100）
     * @return 分组分页结果（含指标列定义 + 对象行）
     */
    public KpiScoreGroupPageDTO pageScoreGroups(LocalDate dataDate, String schemeCode,
                                                String subjectType, int pageNo, int pageSize) {
        return groupPage(dataDate, schemeCode, subjectType,
                Math.max(1, pageNo), Math.min(100, Math.max(1, pageSize)));
    }

    /**
     * 导出用：按对象分组取数（不受每页 100 上限约束，按导出上限 cap 取首批对象）.
     *
     * @param cap 对象数上限（导出上限）
     */
    public KpiScoreGroupPageDTO exportScoreGroups(LocalDate dataDate, String schemeCode,
                                                  String subjectType, int cap) {
        return groupPage(dataDate, schemeCode, subjectType, 1, Math.max(1, cap));
    }

    /**
     * 解析 考核计算(KPI_CALC) 数据范围 → PERF_KPI_SCORE 的 subject 过滤.
     *
     * <p>ALL→不过滤；ORG_SUBTREE→本机构+下级（ORG 对象限这些机构、EMP 对象限其下属员工）；
     * ORG→仅本机构；SELF 及其它→仅本人员工。无 KPI_CALC 上下文（内部/测试）→不过滤。
     */
    private KpiScopeFilter resolveKpiScopeFilter() {
        DataScopeContext ctx = DataScopeContext.current();
        if (ctx == null || ctx.getBizType() != BizType.KPI_CALC || ctx.getScope() == null) {
            return KpiScopeFilter.all();
        }
        switch (ctx.getScope()) {
            case ALL:
                return KpiScopeFilter.all();
            case ORG_SUBTREE:
                return orgScopeFilter(ctx.getOrgSubtreeCodes());
            case ORG:
                return orgScopeFilter(ctx.getOrgCode() == null
                        ? java.util.Set.of() : java.util.Set.of(ctx.getOrgCode()));
            case SELF:
            case SELF_CREATED:
            case SELF_ASSIGNED:
            default:
                String emp = ctx.getEmpId();
                return KpiScopeFilter.of(emp == null ? List.of() : List.of(emp), List.of());
        }
    }

    /** 机构集合 → 范围：ORG 对象限这些机构码，EMP 对象限这些机构下属员工工号（经 UserApi 解析）. */
    private KpiScopeFilter orgScopeFilter(java.util.Set<String> orgCodes) {
        if (orgCodes == null || orgCodes.isEmpty()) {
            return KpiScopeFilter.of(List.of(), List.of());
        }
        java.util.LinkedHashSet<String> empIds = new java.util.LinkedHashSet<>();
        for (String oc : orgCodes) {
            try {
                List<String> es = userApi.getEmpIdsByOrg(oc);
                if (es != null) {
                    empIds.addAll(es);
                }
            } catch (Exception ignore) {
                // 单机构解析失败不影响其它
            }
        }
        return KpiScopeFilter.of(new java.util.ArrayList<>(empIds), new java.util.ArrayList<>(orgCodes));
    }

    private KpiScoreGroupPageDTO groupPage(LocalDate dataDate, String schemeCode,
                                           String subjectType, int safeNo, int safeSize) {
        String sc = StringUtils.hasText(schemeCode) ? schemeCode.trim() : null;
        String st = StringUtils.hasText(subjectType) ? subjectType.trim() : null;

        KpiScoreGroupPageDTO page = new KpiScoreGroupPageDTO();
        page.setPageNo(safeNo);
        page.setPageSize(safeSize);
        // 指标列：该方案指标，按指标名（中文）排序
        List<MetricOptionDTO> metricCols = new java.util.ArrayList<>(listSchemeMetrics(sc));
        // 选中维度时，仅保留该维度(base_dim)的指标列，过滤掉其他维度指标组
        if (st != null) {
            metricCols.removeIf(m -> {
                PerfMetricDef def = metricDefService.getByCodeOrNull(m.getMetricCode());
                return def == null || !st.equals(def.getBaseDim());
            });
        }
        metricCols.sort(java.util.Comparator.comparing(
                m -> m.getMetricName() == null ? "" : m.getMetricName(),
                java.text.Collator.getInstance(java.util.Locale.CHINA)));
        page.setMetrics(metricCols);

        if (dataDate == null || sc == null) {
            page.setRecords(List.of());
            page.setTotal(0L);
            return page;
        }
        // 考核计算(KPI_CALC)数据范围：本人 / 本机构+下级 / 全部
        KpiScopeFilter scope = resolveKpiScopeFilter();
        long total = scoreMapper.countSubjectGroups(dataDate, sc, st, scope);
        page.setTotal(total);
        if (total == 0) {
            page.setRecords(List.of());
            return page;
        }
        int offset = (safeNo - 1) * safeSize;
        List<KpiSubjectGroupRow> groups = scoreMapper.selectSubjectGroups(dataDate, sc, st, scope, offset, safeSize);
        if (groups.isEmpty()) {
            page.setRecords(List.of());
            return page;
        }
        // 当前页对象的全部指标计分行 → (对象类型|对象ID) → metricCode → 计分行
        List<PerfKpiScore> scoreRows = scoreMapper.selectByDateSchemeSubjects(dataDate, sc, groups);
        Map<String, Map<String, PerfKpiScore>> bySubject = new java.util.HashMap<>();
        for (PerfKpiScore r : scoreRows) {
            bySubject.computeIfAbsent(subjectKey(r.getSubjectType(), r.getSubjectId()), k -> new java.util.HashMap<>())
                    .put(r.getMetricCode(), r);
        }
        List<KpiScoreGroupRowDTO> records = new java.util.ArrayList<>(groups.size());
        for (KpiSubjectGroupRow g : groups) {
            KpiScoreGroupRowDTO row = new KpiScoreGroupRowDTO();
            row.setSubjectId(g.getSubjectId());
            row.setSubjectType(g.getSubjectType());
            row.setTotalScore(g.getTotalScore());
            Map<String, PerfKpiScore> byMetric =
                    bySubject.getOrDefault(subjectKey(g.getSubjectType(), g.getSubjectId()), Map.of());
            Map<String, KpiScoreMetricCellDTO> cells = new java.util.HashMap<>();
            for (MetricOptionDTO mo : metricCols) {
                PerfKpiScore s = byMetric.get(mo.getMetricCode());
                if (s == null) {
                    continue;
                }
                KpiScoreMetricCellDTO cell = new KpiScoreMetricCellDTO();
                cell.setActual(s.getActualValue());
                cell.setTarget(s.getTargetValue());
                cell.setBase(s.getBaseValue());
                cell.setScore(s.getScore());
                cell.setCompleteRate(calcCompleteRate(s.getActualValue(), s.getTargetValue(), s.getBaseValue()));
                cells.put(mo.getMetricCode(), cell);
            }
            row.setMetrics(cells);
            records.add(row);
        }
        enrichGroupNames(records);
        page.setRecords(records);
        return page;
    }

    /** (对象类型|对象ID) 复合键. */
    private static String subjectKey(String type, String id) {
        return (type == null ? "" : type) + "|" + (id == null ? "" : id);
    }

    /** 完成率(%) = (实际值-基础值)/目标值*100；目标值为 0/空或实际值空 → null，保留 2 位. */
    private BigDecimal calcCompleteRate(BigDecimal actual, BigDecimal target, BigDecimal base) {
        if (actual == null || target == null || target.signum() == 0) {
            return null;
        }
        BigDecimal b = base == null ? BigDecimal.ZERO : base;
        return actual.subtract(b)
                .divide(target, 6, java.math.RoundingMode.HALF_UP)
                .multiply(BigDecimal.valueOf(100))
                .setScale(2, java.math.RoundingMode.HALF_UP);
    }

    /** 回填分组行的对象姓名（EMP→员工姓名；ORG→机构名称且对象ID替换为业务机构号 dept_no）. */
    private void enrichGroupNames(List<KpiScoreGroupRowDTO> rows) {
        if (rows.isEmpty()) {
            return;
        }
        List<String> empIds = rows.stream().filter(d -> "EMP".equals(d.getSubjectType()))
                .map(KpiScoreGroupRowDTO::getSubjectId).filter(StringUtils::hasText).distinct().toList();
        Map<String, String> empNames = new java.util.HashMap<>();
        if (!empIds.isEmpty()) {
            List<UserDTO> users = userApi.getUsersByUsernames(empIds);
            if (users != null) {
                for (UserDTO u : users) {
                    if (u != null && StringUtils.hasText(u.getUsername())) {
                        empNames.put(u.getUsername(), u.getDisplayName());
                    }
                }
            }
        }
        Map<String, String> orgDeptNos = new java.util.HashMap<>();
        Map<String, String> orgNames = new java.util.HashMap<>();
        rows.stream().filter(d -> "ORG".equals(d.getSubjectType()))
                .map(KpiScoreGroupRowDTO::getSubjectId).filter(StringUtils::hasText).distinct().forEach(code -> {
                    try {
                        OrgDTO org = orgApi.getOrg(code);
                        if (org != null) {
                            if (StringUtils.hasText(org.getDeptNo())) {
                                orgDeptNos.put(code, org.getDeptNo());
                            }
                            orgNames.put(code, org.getOrgName());
                        }
                    } catch (Exception ignore) {
                        // 机构查询异常 → 名称留空
                    }
                });
        for (KpiScoreGroupRowDTO d : rows) {
            if ("EMP".equals(d.getSubjectType())) {
                d.setSubjectName(empNames.get(d.getSubjectId()));
            } else if ("ORG".equals(d.getSubjectType())) {
                String orig = d.getSubjectId();
                d.setSubjectName(orgNames.get(orig));
                String deptNo = orgDeptNos.get(orig);
                if (StringUtils.hasText(deptNo)) {
                    d.setSubjectId(deptNo);
                }
            }
        }
    }

    /**
     * KPI 计算结果详情页"指标"下拉：仅返回该 KPI 方案配置的指标（编号 + 名称）.
     *
     * @param schemeCode KPI 方案编码
     * @return 指标下拉项列表（方案无指标/方案不存在时返回空）
     */
    public List<com.bank.branch.platform.performance.controller.dto.MetricOptionDTO> listSchemeMetrics(String schemeCode) {
        if (!StringUtils.hasText(schemeCode)) {
            return List.of();
        }
        PerfKpiScheme scheme = schemeMapper.selectBySchemeCode(schemeCode.trim());
        if (scheme == null) {
            return List.of();
        }
        java.util.LinkedHashMap<String, com.bank.branch.platform.performance.controller.dto.MetricOptionDTO> map =
                new java.util.LinkedHashMap<>();
        for (PerfKpiItem it : itemMapper.selectBySchemeId(scheme.getId())) {
            String code = it.getMetricCode();
            if (!StringUtils.hasText(code) || map.containsKey(code)) {
                continue;
            }
            PerfMetricDef def = metricDefService.getByCodeOrNull(code);
            map.put(code, new com.bank.branch.platform.performance.controller.dto.MetricOptionDTO(
                    code, def == null ? code : def.getMetricName()));
        }
        return new java.util.ArrayList<>(map.values());
    }

    /**
     * 回填结果明细的 指标名称 / 对象名称（EMP→员工姓名，ORG→机构名称；CUST 不解析）.
     */
    private void enrichScoreNames(List<com.bank.branch.platform.performance.controller.dto.PerfKpiScoreResultDTO> dtos) {
        if (dtos.isEmpty()) {
            return;
        }
        Map<String, String> metricNames = new java.util.HashMap<>();
        dtos.stream().map(d -> d.getMetricCode()).filter(StringUtils::hasText).distinct().forEach(code -> {
            PerfMetricDef def = metricDefService.getByCodeOrNull(code);
            if (def != null) {
                metricNames.put(code, def.getMetricName());
            }
        });
        // 员工姓名（EMP）：subject_id = 工号（PT_USER.username），按 username 批量查 auth 用户取中文姓名
        List<String> empIds = dtos.stream().filter(d -> "EMP".equals(d.getSubjectType()))
                .map(d -> d.getSubjectId()).filter(StringUtils::hasText).distinct().toList();
        Map<String, String> empNames = new java.util.HashMap<>();
        if (!empIds.isEmpty()) {
            List<UserDTO> users = userApi.getUsersByUsernames(empIds);
            if (users != null) {
                for (UserDTO u : users) {
                    if (u != null && StringUtils.hasText(u.getUsername())) {
                        empNames.put(u.getUsername(), u.getDisplayName());
                    }
                }
            }
        }
        // 机构（ORG）：subject_id = 内部机构编码（EXT_ORG_INFO.org_code），列表对象列展示业务机构号 dept_no + 机构名称
        Map<String, String> orgDeptNos = new java.util.HashMap<>();
        Map<String, String> orgNames = new java.util.HashMap<>();
        dtos.stream().filter(d -> "ORG".equals(d.getSubjectType()))
                .map(d -> d.getSubjectId()).filter(StringUtils::hasText).distinct().forEach(code -> {
                    try {
                        OrgDTO org = orgApi.getOrg(code);
                        if (org != null) {
                            if (StringUtils.hasText(org.getDeptNo())) {
                                orgDeptNos.put(code, org.getDeptNo());
                            }
                            orgNames.put(code, org.getOrgName());
                        }
                    } catch (Exception ignore) {
                        // 机构不存在/查询异常 → 名称留空，前端只显示编号
                    }
                });
        for (com.bank.branch.platform.performance.controller.dto.PerfKpiScoreResultDTO d : dtos) {
            d.setMetricName(metricNames.get(d.getMetricCode()));
            if ("EMP".equals(d.getSubjectType())) {
                d.setSubjectName(empNames.get(d.getSubjectId()));
            } else if ("ORG".equals(d.getSubjectType())) {
                // 先按原始内部编码取机构名，再把对象列编码替换为业务机构号（dept_no），未解析到则保留内部编码
                String origCode = d.getSubjectId();
                d.setSubjectName(orgNames.get(origCode));
                String deptNo = orgDeptNos.get(origCode);
                if (StringUtils.hasText(deptNo)) {
                    d.setSubjectId(deptNo);
                }
            }
        }
    }

    /** PerfKpiScore 实体 → 结果明细 DTO. */
    private com.bank.branch.platform.performance.controller.dto.PerfKpiScoreResultDTO toScoreDto(PerfKpiScore e) {
        com.bank.branch.platform.performance.controller.dto.PerfKpiScoreResultDTO d =
                new com.bank.branch.platform.performance.controller.dto.PerfKpiScoreResultDTO();
        d.setId(e.getId());
        d.setDataDate(e.getDataDate());
        d.setSchemeCode(e.getSchemeCode());
        d.setSubjectType(e.getSubjectType());
        d.setMetricCode(e.getMetricCode());
        d.setSubjectId(e.getSubjectId());
        d.setActualValue(e.getActualValue());
        d.setWeight(e.getWeight());
        d.setTargetValue(e.getTargetValue());
        d.setBaseValue(e.getBaseValue());
        d.setScore(e.getScore());
        return d;
    }

    /**
     * 计算单个 KPI 方案的全部指标项 × 全部对象，结果 upsert 到 PERF_KPI_SCORE.
     *
     * @param scheme   KPI 方案
     * @param dataDate 数据日期
     * @return 本方案计分对象数 / 跳过项数统计
     */
    private SchemeStat calcOneScheme(PerfKpiScheme scheme, LocalDate dataDate) {
        // 计算结果落库前先删除该数据日期+该方案的旧计分明细，避免上一轮残留脏数据（重算时全量替换）
        int deleted = scoreMapper.deleteByDateAndScheme(dataDate, scheme.getSchemeCode());
        if (deleted > 0) {
            log.info("【KPI分值计算】方案 {} 数据日期 {} 落库前清理旧计分明细 {} 条",
                    scheme.getSchemeCode(), dataDate, deleted);
        }
        List<PerfKpiItem> items = itemMapper.selectBySchemeId(scheme.getId());
        if (items == null || items.isEmpty()) {
            return new SchemeStat(0, 0);
        }
        // 方案关联的目标方案（perf_target_plan.kpi_scheme_id = scheme.id）：
        // 仅取 状态=启用(ACTIVE) 且 数据日期落在 [start_date, end_date] 区间内的目标方案
        List<PerfTargetPlan> plans = targetPlanMapper.selectByCondition(scheme.getId(), "ACTIVE", null, 0, 1000)
                .stream()
                .filter(p -> isDataDateInPlanRange(dataDate, p))
                .toList();

        int scored = 0;
        int skipped = 0;
        for (PerfKpiItem item : items) {
            String metricCode = item.getMetricCode();
            PerfMetricDef def = metricDefService.getByCodeOrNull(metricCode);
            if (def == null) {
                throw new PerfException(PerfErrorCode.METRIC_NOT_FOUND, metricCode);
            }
            Integer slot = def.getValSlot();
            if (slot == null || slot < 1 || slot > 400) {
                throw new PerfException(PerfErrorCode.METRIC_CALC_LOGIC_INVALID,
                        "指标 " + metricCode + " 未分配合法 val_slot，无法计分");
            }
            String baseDim = def.getBaseDim();
            List<SubjectSlotValueRow> rows = loadSubjectValues(baseDim, dataDate, slot);
            if (rows == null) {
                // 维度无关型（base_dim 非 EMP/ORG/CUST）：无对象集合，跳过该指标
                log.info("【KPI分值计算】方案={} 指标={} base_dim={} 非主体维度，跳过",
                        scheme.getSchemeCode(), metricCode, baseDim);
                skipped++;
                continue;
            }
            boolean hasSqlExpr = StringUtils.hasText(item.getSqlExpr());
            boolean hasFormula = StringUtils.hasText(item.getFormula());
            if (!hasSqlExpr && !hasFormula) {
                // 既无 SQL 表达式也无计分公式：跳过该指标项（记录告警），不中断整个任务
                log.warn("【KPI分值计算】方案={} 指标={} 未配置 SQL 表达式/计分公式，跳过该项",
                        scheme.getSchemeCode(), metricCode);
                skipped++;
                continue;
            }

            BigDecimal weight = item.getWeight();
            // 构建数据集（需求 4/7）：KPI指标配置 ⟕ 指标结果数据 ⟕ 目标值，逐对象一行
            // 列：指标 / 数据日期 / 对象id / 指标维度 / 权重 / 计分上限 / 计分下限 / 实际值 / 目标值 / 基础值
            List<KpiScoreRow> dataset = new ArrayList<>(rows.size());
            for (SubjectSlotValueRow row : rows) {
                TargetBase tb = lookupTargetBase(plans, baseDim, row.getSubjectId(), metricCode, dataDate);
                dataset.add(new KpiScoreRow(metricCode, dataDate, row.getSubjectId(), baseDim,
                        weight, item.getMaxScore(), item.getMinScore(), row.getValue(), tb.target, tb.base));
            }
            // 逐行计算并 upsert：优先用 SQL 表达式（需求 5），缺失时回退计分公式（兼容历史方案）
            for (KpiScoreRow dr : dataset) {
                BigDecimal score = hasSqlExpr
                        ? evalScoreBySql(item.getSqlExpr(), dr)
                        : formulaService.evalScore(item.getFormula(), dr.actual(), dr.target(), dr.base(),
                                dr.weight(), dr.minScore(), dr.maxScore());
                upsertScore(dataDate, scheme.getSchemeCode(), metricCode, baseDim, dr.objId(),
                        dr.actual(), dr.weight(), dr.target(), dr.base(), score);
                scored++;
            }
        }
        return new SchemeStat(scored, skipped);
    }

    /**
     * 按指标维度从对应结果宽表取某日某 slot 的全部对象值（最新版本）.
     *
     * @return 对象值列表；{@code base_dim} 非 EMP/ORG/CUST 时返回 {@code null}（维度无关型）
     */
    private List<SubjectSlotValueRow> loadSubjectValues(String baseDim, LocalDate dataDate, Integer slot) {
        if (baseDim == null) {
            return null;
        }
        return switch (baseDim) {
            case "EMP" -> empIndexResultMapper.selectLatestSlotValuesByDate(dataDate, slot);
            case "ORG" -> orgIndexResultMapper.selectLatestSlotValuesByDate(dataDate, slot);
            case "CUST" -> custIndexResultMapper.selectLatestSlotValuesByDate(dataDate, slot);
            default -> null;
        };
    }

    /**
     * 匹配目标值 / 基础值：遍历方案关联目标方案，按对象 + 指标 + 周期键命中第一条 perf_target_value.
     *
     * <p>对象值相同才算匹配（subject_type = base_dim、subject_id = 对象、metric_code = 指标）。
     * 周期键按各目标方案的 target_cycle 由数据日期派生（YEAR→yyyy，QUARTER→yyyyQn）。
     * 未匹配到目标值默认 0，未匹配到基础值默认 0。CUST 维度目标管理不覆盖，直接默认 0。
     */
    /**
     * 数据日期是否落在目标方案的起止日期区间内（含端点；某端点为空则该侧不限制）.
     *
     * @param dataDate 计算数据日期
     * @param plan     目标方案
     * @return true=在区间内（或方案未设置对应边界）
     */
    private boolean isDataDateInPlanRange(LocalDate dataDate, PerfTargetPlan plan) {
        if (plan == null) {
            return false;
        }
        LocalDate start = plan.getStartDate();
        LocalDate end = plan.getEndDate();
        if (start != null && dataDate.isBefore(start)) {
            return false;
        }
        if (end != null && dataDate.isAfter(end)) {
            return false;
        }
        return true;
    }

    private TargetBase lookupTargetBase(List<PerfTargetPlan> plans, String baseDim,
                                        String subjectId, String metricCode, LocalDate dataDate) {
        if (plans == null || plans.isEmpty() || (!"EMP".equals(baseDim) && !"ORG".equals(baseDim))) {
            return new TargetBase(BigDecimal.ZERO, BigDecimal.ZERO);
        }
        for (PerfTargetPlan plan : plans) {
            String cycleKey = deriveCycleKey(plan.getTargetCycle(), dataDate);
            PerfTargetValue tv = targetValueMapper.selectByUniqueKey(
                    plan.getId(), baseDim, subjectId, cycleKey, metricCode);
            if (tv != null) {
                BigDecimal target = tv.getTargetValue() == null ? BigDecimal.ZERO : tv.getTargetValue();
                BigDecimal base = tv.getBaseValue() == null ? BigDecimal.ZERO : tv.getBaseValue();
                return new TargetBase(target, base);
            }
        }
        return new TargetBase(BigDecimal.ZERO, BigDecimal.ZERO);
    }

    /**
     * 按目标周期由数据日期派生周期键，对齐 perf_target_value.cycle_key 取值（如 2026 / 2026Q2）.
     *
     * @param targetCycle 目标周期 YEAR / QUARTER
     * @param dataDate    数据日期
     * @return 周期键
     */
    String deriveCycleKey(String targetCycle, LocalDate dataDate) {
        int year = dataDate.getYear();
        if ("QUARTER".equalsIgnoreCase(targetCycle)) {
            int quarter = (dataDate.getMonthValue() - 1) / 3 + 1;
            return year + "Q" + quarter;
        }
        // YEAR 及缺省
        return String.valueOf(year);
    }

    /** 组装并 upsert 一条 KPI 计分明细. */
    private void upsertScore(LocalDate dataDate, String schemeCode, String metricCode, String subjectType,
                             String subjectId, BigDecimal actual, BigDecimal weight,
                             BigDecimal target, BigDecimal base, BigDecimal score) {
        PerfKpiScore s = new PerfKpiScore();
        s.setDataDate(dataDate);
        s.setSchemeCode(schemeCode);
        s.setMetricCode(metricCode);
        s.setSubjectType(subjectType);
        s.setSubjectId(subjectId);
        s.setActualValue(actual);
        s.setWeight(weight);
        s.setTargetValue(target);
        s.setBaseValue(base);
        s.setScore(score);
        scoreMapper.upsert(s);
    }

    /** 落一条 KPI 方案级计算记录（成功/异常各一条）；异常信息截断 2000. */
    private void insertSchemeLog(LocalDate dataDate, String schemeCode, String triggerType, String triggerBy,
                                 LocalDateTime startTime, String result, int scored, int skipped,
                                 String errorMsg, String taskId) {
        PerfKpiCalcLog calcLog = new PerfKpiCalcLog();
        calcLog.setDataDate(dataDate);
        calcLog.setSchemeCode(schemeCode);
        calcLog.setTriggerType(triggerType);
        calcLog.setTriggerBy(StringUtils.hasText(triggerBy) ? triggerBy.trim() : null);
        calcLog.setStartTime(startTime);
        calcLog.setEndTime(LocalDateTime.now());
        calcLog.setResult(result);
        calcLog.setScoredCount(scored);
        calcLog.setSkippedCount(skipped);
        calcLog.setErrorMsg(errorMsg != null && errorMsg.length() > 2000 ? errorMsg.substring(0, 2000) : errorMsg);
        calcLog.setTaskId(taskId);
        kpiCalcLogMapper.insert(calcLog);
    }

    /** 更新任务终态（状态 + 结束时间 + 错误原因，截断 5000）. */
    private void finishTask(PerfMetricCalcTask task, String status, String errorMsg) {
        task.setStatus(status);
        task.setEndTime(LocalDateTime.now());
        if (errorMsg != null) {
            task.setErrorMsg(errorMsg.length() > 5000 ? errorMsg.substring(0, 5000) : errorMsg);
        }
        taskMapper.updateById(task);
    }

    /**
     * 用 KPI 指标项的 SQL 表达式逐行计算单个对象的 KPI 得分（需求 5）.
     *
     * <p>把数据集行的各列作为命名参数绑定：{@code :metricCode/:dataDate/:objId/:baseDim/:weight/
     * :maxScore/:minScore/:actual/:target/:base}，执行表达式（结果列只需 {@code kpi_value}）。
     * 对象id 不从 SQL 取（由 {@code dr.objId()} 落库），取首行 kpi_value 作为该对象得分。
     *
     * @param sqlExpr KPI 计分 SQL 表达式
     * @param dr      数据集行
     * @return 该对象 KPI 得分（SQL 未返回行时为 null）
     */
    private BigDecimal evalScoreBySql(String sqlExpr, KpiScoreRow dr) {
        Map<String, Object> params = new HashMap<>();
        params.put("metricCode", dr.metricCode());
        params.put("dataDate", dr.dataDate());
        params.put("objId", dr.objId());
        params.put("baseDim", dr.baseDim());
        params.put("weight", dr.weight());
        params.put("maxScore", dr.maxScore());
        params.put("minScore", dr.minScore());
        params.put("actual", dr.actual());
        params.put("target", dr.target());
        params.put("base", dr.base());
        // 对象id 不从 SQL 取（由 dr.objId() 落库），SQL 只需返回 kpi_value
        return sqlExecutor.executeScore(sqlExpr, params, SQL_EXPR_TIMEOUT);
    }

    /** 单方案统计：计分对象数 / 跳过指标项数. */
    private record SchemeStat(int scored, int skipped) {
    }

    /** 目标值 / 基础值二元组. */
    private record TargetBase(BigDecimal target, BigDecimal base) {
    }

    /**
     * KPI 计分数据集行（需求 4）：KPI指标配置 ⟕ 指标结果数据 ⟕ 目标值 的一行.
     * 列 = 指标 / 数据日期 / 对象id / 指标维度 / 权重 / 计分上限 / 计分下限 / 实际值 / 目标值 / 基础值。
     */
    private record KpiScoreRow(String metricCode, LocalDate dataDate, String objId, String baseDim,
                               BigDecimal weight, BigDecimal maxScore, BigDecimal minScore,
                               BigDecimal actual, BigDecimal target, BigDecimal base) {
    }
}
