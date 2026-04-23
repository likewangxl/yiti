package com.bank.branch.platform.performance.service;

import com.bank.branch.platform.performance.entity.PerfMetricDef;
import com.bank.branch.platform.performance.entity.PerfRunTask;
import com.bank.branch.platform.performance.enums.PerfErrorCode;
import com.bank.branch.platform.performance.exception.PerfException;
import com.bank.branch.platform.performance.mapper.PerfRunTaskMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * 历史回算服务（V1.1 Task P7.1）.
 *
 * <p>职责：按日期范围 × 指标码批量回算，对每个 {@code (date, metricCode)}
 * 独立调用 {@link MetricCalcService#calcMetric}。维护一个 {@code taskType=RECALC}
 * 的父级 {@code perf_run_task} 记录聚合状态，子任务 ID 列表记录在父任务
 * 的 {@code result_preview_json} 字段。
 *
 * <p><strong>架构决策（V1.1 简化）</strong>：本实现 <em>不</em> 在
 * {@code perf_run_task} DDL 中加入 {@code parent_id} 字段（V1.2 规划）。
 * 为支持"父子任务查询"需求，临时方案是将子 taskId 列表以 JSON 数组形式
 * 写入父 task 的 {@code result_preview_json}——调用方需要解析该 JSON
 * 才能拿到子任务清单。待 V1.2 引入 parent_id 正式字段后，本 Service 的
 * 逻辑会简化为"子任务 insert 时直接写 parent_id"。
 *
 * <p><strong>错误聚合策略</strong>：单个 {@code (date, metricCode)} 计算
 * 失败 <em>不</em> 中断整个回算流程；失败计数累计到父 task 的
 * {@code remark}，父 task 终态选择规则：
 * <ul>
 *   <li>全部成功 → {@code SUCCESS}</li>
 *   <li>全部失败 → {@code FAILED}</li>
 *   <li>部分成功部分失败 → {@code PARTIAL}</li>
 *   <li>空执行（metricCodes 为空且无 ACTIVE 指标）→ {@code SUCCESS}</li>
 * </ul>
 *
 * <p><strong>事务边界</strong>：与 {@link MetricCalcService} 一致——
 * <em>不</em> 开最外层 {@code @Transactional}。各子 task 独立连接写 run_task，
 * 父 task 的状态更新也独立提交，保证失败路径仍可追溯。
 *
 * <p><strong>日期范围限制</strong>：为防止误操作批量回算多年数据，
 * 限制单次回算日期跨度 ≤ 365 天；超过抛 {@link PerfErrorCode#VALIDATION_FAILED}.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class HistoryRecalcService {

    /** 单次回算日期范围上限（含）—— 防止误操作批量回算多年数据. */
    private static final long MAX_RANGE_DAYS = 365L;

    private final MetricCalcService metricCalcService;
    private final MetricDefService metricDefService;
    private final PerfRunTaskMapper perfRunTaskMapper;

    /**
     * 按日期范围回算指定指标.
     *
     * @param startDate   起始日期（含，必填）
     * @param endDate     截止日期（含，必填；{@code startDate <= endDate}）
     * @param metricCodes 指标编码列表（null 或空列表表示"所有 ACTIVE 指标"）
     * @param version     数据版本（必填）
     * @param reason      回算原因（审计用途，必填）
     * @param operator    发起人 emp_id（必填）
     * @return 父级 {@code perf_run_task} 主键 ID
     * @throws PerfException {@link PerfErrorCode#VALIDATION_FAILED} 参数非法 /
     *                       日期范围超限；子任务抛的业务异常被聚合统计，不中断回算
     */
    public String recalc(LocalDate startDate, LocalDate endDate, List<String> metricCodes,
                         String version, String reason, String operator) {
        // 1. 参数校验
        validate(startDate, endDate, version, reason, operator);

        // 2. 解析指标集合：null/空 → 查全部 ACTIVE 指标
        List<String> effectiveMetricCodes = resolveMetricCodes(metricCodes);

        // 3. 建立日期列表（闭区间 [start, end]）
        List<LocalDate> dates = buildDateList(startDate, endDate);

        // 4. 插入父 run_task（PENDING → RUNNING）
        String parentTaskId = generateTaskId();
        insertParentTask(parentTaskId, startDate, endDate, version, operator, reason,
                effectiveMetricCodes.size(), dates.size());
        perfRunTaskMapper.updateStatus(parentTaskId, "RUNNING", null);

        // 5. 外层循环：日期；内层循环：指标——调用 MetricCalcService.calcMetric
        List<String> childTaskIds = new ArrayList<>();
        int successCount = 0;
        int failureCount = 0;
        List<String> failureDetails = new ArrayList<>();

        for (LocalDate date : dates) {
            for (String metricCode : effectiveMetricCodes) {
                try {
                    String childTaskId = metricCalcService.calcMetric(metricCode, date, version);
                    childTaskIds.add(childTaskId);
                    successCount++;
                } catch (PerfException pe) {
                    // 错误聚合不中断：记录失败细节供父 task remark
                    failureCount++;
                    String detail = String.format("%s@%s: [%s] %s",
                            metricCode, date, pe.getErrorCode().getCode(), safeMsg(pe.getMessage()));
                    failureDetails.add(detail);
                    log.warn("[HistoryRecalc] 子任务失败 metric={}, date={}: {}",
                            metricCode, date, pe.getMessage());
                } catch (Exception ex) {
                    failureCount++;
                    String detail = String.format("%s@%s: [UNEXPECTED] %s",
                            metricCode, date, safeMsg(ex.getMessage()));
                    failureDetails.add(detail);
                    log.error("[HistoryRecalc] 子任务非预期异常 metric={}, date={}", metricCode, date, ex);
                }
            }
        }

        // 6. 持久化 childTaskIds 到父 task 的 result_preview_json（V1.1 P8 Task C.1）
        //    用手工拼接 JSON 数组避免额外引入 ObjectMapper 依赖；childTaskId 由 UUID 生成，
        //    字符集合法，不含 " \ 等需转义字符。
        //    终态更新前执行，保证即使后续 updateStatus 抛异常 result_preview_json 也已落库。
        String childTaskIdsJson = toJsonArray(childTaskIds);
        perfRunTaskMapper.updateResultPreviewJson(parentTaskId, childTaskIdsJson);

        // 7. 聚合父 task 终态
        String finalStatus = resolveFinalStatus(successCount, failureCount);
        String aggregatedMsg = buildAggregatedMessage(successCount, failureCount, failureDetails);
        perfRunTaskMapper.updateStatus(parentTaskId, finalStatus, aggregatedMsg);

        log.info("[HistoryRecalc] parentTaskId={}, 总计 success={}, failed={}, 子任务数={}, 终态={}",
                parentTaskId, successCount, failureCount, childTaskIds.size(), finalStatus);
        return parentTaskId;
    }

    /** 将 childTaskIds 转为 JSON 字符串数组（手工拼接；childTaskId 来自 UUID，无需转义）. */
    private static String toJsonArray(List<String> childTaskIds) {
        if (childTaskIds == null || childTaskIds.isEmpty()) {
            return "[]";
        }
        StringBuilder sb = new StringBuilder();
        sb.append('[');
        for (int i = 0; i < childTaskIds.size(); i++) {
            if (i > 0) {
                sb.append(',');
            }
            sb.append('"').append(escapeJsonString(childTaskIds.get(i))).append('"');
        }
        sb.append(']');
        return sb.toString();
    }

    /** 参数校验：非空 + 日期区间合法 + 范围 ≤ 365 天. */
    private void validate(LocalDate startDate, LocalDate endDate, String version,
                          String reason, String operator) {
        if (startDate == null) {
            throw new PerfException(PerfErrorCode.VALIDATION_FAILED, "startDate 不能为空");
        }
        if (endDate == null) {
            throw new PerfException(PerfErrorCode.VALIDATION_FAILED, "endDate 不能为空");
        }
        if (startDate.isAfter(endDate)) {
            throw new PerfException(PerfErrorCode.VALIDATION_FAILED,
                    "startDate 不能晚于 endDate: " + startDate + " > " + endDate);
        }
        long days = ChronoUnit.DAYS.between(startDate, endDate) + 1L;
        if (days > MAX_RANGE_DAYS) {
            throw new PerfException(PerfErrorCode.VALIDATION_FAILED,
                    "回算日期范围超过上限 " + MAX_RANGE_DAYS + " 天: 实际 " + days + " 天");
        }
        if (version == null || version.isBlank()) {
            throw new PerfException(PerfErrorCode.VALIDATION_FAILED, "version 不能为空");
        }
        if (reason == null || reason.isBlank()) {
            throw new PerfException(PerfErrorCode.VALIDATION_FAILED, "reason 不能为空");
        }
        if (operator == null || operator.isBlank()) {
            throw new PerfException(PerfErrorCode.VALIDATION_FAILED, "operator 不能为空");
        }
    }

    /** 解析指标集合：null/空 → 调 listActiveMetrics；否则使用入参（调用侧保证代码存在由 MetricCalcService 兜底校验）. */
    private List<String> resolveMetricCodes(List<String> requested) {
        if (requested == null || requested.isEmpty()) {
            List<PerfMetricDef> active = metricDefService.listActiveMetrics(null, null);
            if (active == null || active.isEmpty()) {
                return new ArrayList<>();
            }
            List<String> codes = new ArrayList<>(active.size());
            for (PerfMetricDef def : active) {
                codes.add(def.getMetricCode());
            }
            return codes;
        }
        return new ArrayList<>(requested);
    }

    /** 生成日期列表（闭区间 [start, end]）. */
    private List<LocalDate> buildDateList(LocalDate start, LocalDate end) {
        List<LocalDate> list = new ArrayList<>();
        LocalDate cursor = start;
        while (!cursor.isAfter(end)) {
            list.add(cursor);
            cursor = cursor.plusDays(1);
        }
        return list;
    }

    /** 构造父级 run_task 并插入（PENDING 状态）. */
    private void insertParentTask(String taskId, LocalDate start, LocalDate end,
                                  String version, String operator, String reason,
                                  int metricCount, int dateCount) {
        PerfRunTask parent = new PerfRunTask();
        parent.setId(taskId);
        parent.setTaskType("RECALC");
        parent.setTaskKey("RECALC_" + start + "_" + end);
        parent.setDataDate(start);
        parent.setDataVersion(version);
        parent.setStatus("PENDING");
        parent.setStartedBy(operator);
        parent.setStartTime(LocalDateTime.now());
        parent.setParamsJson(String.format(
                "{\"startDate\":\"%s\",\"endDate\":\"%s\",\"metricCount\":%d,\"dateCount\":%d,\"reason\":\"%s\"}",
                start, end, metricCount, dateCount, escapeJsonString(reason)));
        perfRunTaskMapper.insert(parent);
    }

    /** 根据成功/失败计数解析终态. */
    private String resolveFinalStatus(int successCount, int failureCount) {
        if (failureCount == 0) {
            return "SUCCESS";
        }
        if (successCount == 0) {
            return "FAILED";
        }
        return "PARTIAL";
    }

    /** 构造父 task 的聚合 remark（截断到 2000 字符）. */
    private String buildAggregatedMessage(int successCount, int failureCount, List<String> failureDetails) {
        StringBuilder sb = new StringBuilder();
        sb.append("success=").append(successCount).append(", failed=").append(failureCount);
        if (!failureDetails.isEmpty()) {
            sb.append("; details: ");
            int remain = 1800 - sb.length();
            for (String detail : failureDetails) {
                if (sb.length() + detail.length() + 2 > remain) {
                    sb.append("...(more)");
                    break;
                }
                sb.append(detail).append("; ");
            }
        }
        String msg = sb.toString();
        if (msg.length() > 2000) {
            msg = msg.substring(0, 2000);
        }
        return msg;
    }

    private static String safeMsg(String raw) {
        if (raw == null) {
            return "";
        }
        return raw.length() > 200 ? raw.substring(0, 200) : raw;
    }

    private static String escapeJsonString(String raw) {
        if (raw == null) {
            return "";
        }
        return raw.replace("\\", "\\\\").replace("\"", "\\\"");
    }

    private static String generateTaskId() {
        return UUID.randomUUID().toString().replace("-", "");
    }
}
