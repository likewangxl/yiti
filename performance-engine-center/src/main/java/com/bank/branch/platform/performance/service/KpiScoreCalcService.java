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

            // 3. 逐方案计算（fail-fast：任一方案计算抛异常即停止整个任务）
            int schemeSuccess = 0;
            int totalScored = 0;
            int totalSkipped = 0;
            for (PerfKpiScheme scheme : schemes) {
                // 每个方案落一条方案级记录（成功/异常各记一条）；异常仍 fail-fast 中止整任务
                LocalDateTime schemeStart = LocalDateTime.now();
                try {
                    SchemeStat stat = calcOneScheme(scheme, dataDate);
                    schemeSuccess++;
                    totalScored += stat.scored;
                    totalSkipped += stat.skipped;
                    insertSchemeLog(dataDate, scheme.getSchemeCode(), normalizedTrigger, triggerBy,
                            schemeStart, "SUCCESS", stat.scored, stat.skipped, null, taskId);
                } catch (Exception schemeEx) {
                    insertSchemeLog(dataDate, scheme.getSchemeCode(), normalizedTrigger, triggerBy,
                            schemeStart, "FAILED", 0, 0, schemeEx.getMessage(), taskId);
                    // 失败方案数记 1（fail-fast）；成功/跳过保留已处理量，供任务级统计
                    task.setTotalCount(schemes.size());
                    task.setSuccessCount(schemeSuccess);
                    task.setFailCount(1);
                    task.setSkipCount(totalSkipped);
                    throw schemeEx;
                }
            }

            task.setTotalCount(schemes.size());
            task.setSuccessCount(schemeSuccess);
            task.setSkipCount(totalSkipped);
            finishTask(task, "SUCCESS", null);
            log.info("========== 【KPI分值计算】完成 taskId={}, 方案={}, 计分对象={}, 跳过项={} ==========",
                    taskId, schemeSuccess, totalScored, totalSkipped);
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
        return com.bank.branch.platform.common.web.PageResult.of(
                safeNo, safeSize, total == null ? 0L : total, dtos);
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
            if (!StringUtils.hasText(item.getFormula())) {
                // 未配置计分公式：跳过该指标项（记录告警），不中断整个任务
                log.warn("【KPI分值计算】方案={} 指标={} 未配置计分公式，跳过该项",
                        scheme.getSchemeCode(), metricCode);
                skipped++;
                continue;
            }

            BigDecimal weight = item.getWeight();
            for (SubjectSlotValueRow row : rows) {
                String subjectId = row.getSubjectId();
                BigDecimal actual = row.getValue();
                TargetBase tb = lookupTargetBase(plans, baseDim, subjectId, metricCode, dataDate);
                BigDecimal score = formulaService.evalScore(
                        item.getFormula(), actual, tb.target, tb.base, weight,
                        item.getMinScore(), item.getMaxScore());
                upsertScore(dataDate, scheme.getSchemeCode(), metricCode, baseDim, subjectId,
                        actual, weight, tb.target, tb.base, score);
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

    /** 单方案统计：计分对象数 / 跳过指标项数. */
    private record SchemeStat(int scored, int skipped) {
    }

    /** 目标值 / 基础值二元组. */
    private record TargetBase(BigDecimal target, BigDecimal base) {
    }
}
