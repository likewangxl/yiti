package com.bank.branch.platform.performance.eval.service;

import com.alibaba.excel.EasyExcel;
import com.bank.branch.platform.auth.api.UserApi;
import com.bank.branch.platform.performance.enums.PerfErrorCode;
import com.bank.branch.platform.performance.eval.dto.EvalAssignImportResultDTO;
import com.bank.branch.platform.performance.eval.dto.EvalRewardImportRow;
import com.bank.branch.platform.performance.eval.entity.EvalAssignBatch;
import com.bank.branch.platform.performance.eval.entity.EvalRewardItem;
import com.bank.branch.platform.performance.eval.mapper.EvalAssignBatchMapper;
import com.bank.branch.platform.performance.eval.mapper.EvalRewardItemMapper;
import com.bank.branch.platform.performance.exception.PerfException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.multipart.MultipartFile;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 奖励分配（REWARD）Excel 导入服务（异步 + 前端轮询，镜像 EvalAssignImportService）。
 *
 * <p>接口线程仅 {@link #parseRows} 同步解析 + {@link #createImportingBatch} 建 IMPORTING(3)
 * REWARD 批次并立即返回 batchId；逐行校验入库挪到 {@link #processImport} 后台异步执行。</p>
 *
 * <p>校验：分配人工号填登录名(PT_USER.USER_NAME)且系统有效（归一 USER_ID）；被分配人工号仅非空
 * 快照不校验；原始值/兑现值/分配合计可解析 BigDecimal，分配合计>0；同(分配人+部门)组分配合计一致；
 * all-or-none（任一行错误则一条不写，失败明细 JSON 写批次 ERROR_SUMMARY）。</p>
 */
@Slf4j
@Service
public class EvalRewardImportService {

    /** 批次状态：草稿（全部通过、待人工确认发布）。 */
    private static final int STATUS_DRAFT = 2;
    /** 批次状态：处理中（接口受理即置此态）。 */
    private static final int STATUS_IMPORTING = 3;
    /** 批次状态：导入失败。 */
    private static final int STATUS_FAILED = 4;
    /** 待处理任务类型：奖励分配。 */
    private static final String TASK_TYPE_REWARD = "REWARD";

    /** 单次 batchInsert 的最大明细条数（可经 application.yml 覆盖）。 */
    @Value("${perf.eval.import.batch-insert-size:1000}")
    private int batchInsertSize = 1000;
    /** 失败时写入 ERROR_SUMMARY 的行级错误明细封顶条数（可经 application.yml 覆盖）。 */
    @Value("${perf.eval.import.error-keep:500}")
    private int errorKeep = 500;

    private final UserApi userApi;
    private final EvalAssignBatchMapper batchMapper;
    private final EvalRewardItemMapper itemMapper;
    private final ObjectMapper objectMapper;
    /** 成功路径「批量插明细 + 更新草稿状态」同一事务（显式事务模板，规避 @Async 自调用代理失效）。 */
    private final TransactionTemplate txTemplate;

    public EvalRewardImportService(UserApi userApi,
                                   EvalAssignBatchMapper batchMapper,
                                   EvalRewardItemMapper itemMapper,
                                   ObjectMapper objectMapper,
                                   PlatformTransactionManager transactionManager) {
        this.userApi = userApi;
        this.batchMapper = batchMapper;
        this.itemMapper = itemMapper;
        this.objectMapper = objectMapper;
        this.txTemplate = new TransactionTemplate(transactionManager);
    }

    /**
     * 同步解析上传的 Excel 为内存行集（接口线程调用）。
     *
     * @param file 上传的 .xlsx 文件
     * @return 解析后的行集
     */
    public List<EvalRewardImportRow> parseRows(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new PerfException(PerfErrorCode.EVAL_IMPORT_FILE_EMPTY);
        }
        try {
            return EasyExcel.read(file.getInputStream())
                    .head(EvalRewardImportRow.class)
                    .sheet()
                    .doReadSync();
        } catch (Exception e) {
            log.warn("[EvalRewardImportService.parseRows] 解析失败: {}", e.getMessage());
            throw new PerfException(PerfErrorCode.EVAL_IMPORT_FILE_INVALID, e.getMessage());
        }
    }

    /**
     * 建 IMPORTING(3) REWARD 批次并独立事务提交，使 batchId 立即对前端轮询可见。
     *
     * @param taskName 任务名称
     * @param deadline 分配截止时间
     * @param createBy 创建人工号
     * @return 新建批次主键
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW, rollbackFor = Exception.class)
    public Long createImportingBatch(String taskName, LocalDateTime deadline, String createBy) {
        EvalAssignBatch batch = new EvalAssignBatch();
        batch.setTaskType(TASK_TYPE_REWARD);
        batch.setBatchName(taskName);
        batch.setSource("IMPORT");
        batch.setDeadline(deadline);
        batch.setStatus(STATUS_IMPORTING);
        batch.setCreateBy(createBy);
        batch.setCreateTime(LocalDateTime.now());
        batchMapper.insert(batch);
        log.info("[EvalRewardImportService.createImportingBatch] 受理导入 batchId={} createBy={}",
                batch.getBatchId(), createBy);
        return batch.getBatchId();
    }

    /**
     * 异步逐行校验 + 入库（后台线程执行，不阻塞接口）。
     *
     * @param batchId  已建好的 IMPORTING 批次 ID
     * @param rows     解析后的行集
     * @param taskName 任务名称（仅日志）
     * @param deadline 分配截止时间
     * @param createBy 创建人工号（仅日志）
     */
    @Async("evalImportExecutor")
    public void processImport(Long batchId, List<EvalRewardImportRow> rows,
                              String taskName, LocalDateTime deadline, String createBy) {
        int total = rows == null ? 0 : rows.size();
        try {
            ValidationOutcome outcome = validate(rows, deadline);
            if (!outcome.errors.isEmpty()) {
                markFailed(batchId, buildErrorSummary(outcome.errors), total);
                log.info("[EvalRewardImportService.processImport] batchId={} 校验失败 {} 条错误（共 {} 行）",
                        batchId, outcome.errors.size(), total);
                return;
            }
            persistSuccess(batchId, outcome.parsed, total);
            log.info("[EvalRewardImportService.processImport] batchId={} 导入成功 {} 条", batchId, outcome.parsed.size());
        } catch (Exception e) {
            log.error("[EvalRewardImportService.processImport] batchId={} 导入异常", batchId, e);
            markFailed(batchId, buildExceptionSummary(e), total);
        }
    }

    /** 全通过路径：批量插明细 + 更新批次草稿状态，同一事务（一起成功/回滚）。 */
    void persistSuccess(Long batchId, List<EvalRewardItem> parsed, int total) {
        txTemplate.executeWithoutResult(status -> {
            for (EvalRewardItem item : parsed) {
                item.setBatchId(batchId);
            }
            for (int from = 0; from < parsed.size(); from += batchInsertSize) {
                int to = Math.min(from + batchInsertSize, parsed.size());
                itemMapper.batchInsert(parsed.subList(from, to));
            }
            EvalAssignBatch batch = batchMapper.selectById(batchId);
            batch.setStatus(STATUS_DRAFT);
            batch.setImportedCount(parsed.size());
            batch.setTotalRows(total);
            batchMapper.updateById(batch);
        });
    }

    /** 失败路径：批次置 STATUS=4 + ERROR_SUMMARY + TOTAL_ROWS（独立单条 UPDATE）。 */
    void markFailed(Long batchId, String errorSummary, int total) {
        EvalAssignBatch batch = batchMapper.selectById(batchId);
        if (batch == null) {
            log.warn("[EvalRewardImportService.markFailed] batchId={} 不存在，跳过置失败", batchId);
            return;
        }
        batch.setStatus(STATUS_FAILED);
        batch.setErrorSummary(errorSummary);
        batch.setTotalRows(total);
        batchMapper.updateById(batch);
    }

    /**
     * 逐行 + 跨行校验，不写库。
     *
     * @param rows     解析后的行
     * @param deadline 分配截止时间
     * @return 校验产物：errors 非空表示 all-or-none 失败，否则 parsed 为待入库明细
     */
    private ValidationOutcome validate(List<EvalRewardImportRow> rows, LocalDateTime deadline) {
        if (rows == null || rows.isEmpty()) {
            throw new PerfException(PerfErrorCode.EVAL_IMPORT_FILE_EMPTY);
        }
        if (deadline == null) {
            throw new PerfException(PerfErrorCode.EVAL_TASK_END_TIME_INVALID, (Object) null);
        }

        // 分配人工号有效性：填登录名(PT_USER.USER_NAME)，批量归一为 USER_ID
        Set<String> allAssigners = new HashSet<>();
        for (EvalRewardImportRow r : rows) {
            String a = trim(r.getAssignUserId());
            if (!a.isEmpty()) {
                allAssigners.add(a);
            }
        }
        Map<String, String> nameToUserId = allAssigners.isEmpty()
                ? new HashMap<>()
                : userApi.mapUsernamesToEmpId(new ArrayList<>(allAssigners));
        Set<String> existing = nameToUserId.keySet();

        List<EvalAssignImportResultDTO.RowError> errors = new ArrayList<>();
        List<EvalRewardItem> parsed = new ArrayList<>();
        // 跨行分组合计一致性：key = 归一后分配人 + '' + 部门 → 首个 assign_total
        Map<String, BigDecimal> groupTotal = new HashMap<>();

        for (int i = 0; i < rows.size(); i++) {
            int rowNo = i + 1;
            EvalRewardImportRow r = rows.get(i);
            String beId = trim(r.getBeAssignedUserId());
            String assignName = trim(r.getAssignUserId());
            String dept = trim(r.getDeptName());

            if (beId.isEmpty()) {
                errors.add(err(rowNo, "被分配人工号不能为空"));
                continue;
            }
            if (assignName.isEmpty()) {
                errors.add(err(rowNo, "分配人工号不能为空"));
                continue;
            }
            if (!existing.contains(assignName)) {
                errors.add(err(rowNo, "分配人工号不存在：" + assignName));
                continue;
            }
            BigDecimal total = r.getAssignTotal();
            if (total == null) {
                errors.add(err(rowNo, "分配合计不能为空或非法"));
                continue;
            }
            if (total.compareTo(BigDecimal.ZERO) <= 0) {
                errors.add(err(rowNo, "分配合计必须大于0"));
                continue;
            }
            String assignUserId = nameToUserId.getOrDefault(assignName, assignName);
            // 跨行：同(分配人+部门)组 分配合计必须一致
            String groupKey = assignUserId + "" + dept;
            BigDecimal seen = groupTotal.get(groupKey);
            if (seen == null) {
                groupTotal.put(groupKey, total);
            } else if (seen.compareTo(total) != 0) {
                errors.add(err(rowNo, "同部门分配合计不一致：" + dept));
                continue;
            }

            EvalRewardItem item = new EvalRewardItem();
            item.setAssignUserId(assignUserId);
            item.setBeAssignedUserId(beId);
            item.setBeAssignedUserName(trim(r.getBeAssignedUserName()));
            item.setDeptName(dept);
            item.setOriginalValue(r.getOriginalValue());
            item.setCashValue(r.getCashValue());
            item.setAssignTotal(total);
            item.setAssignValue(null);
            item.setSubmitted(0);
            parsed.add(item);
        }
        return new ValidationOutcome(errors, parsed);
    }

    private String buildErrorSummary(List<EvalAssignImportResultDTO.RowError> errors) {
        int total = errors.size();
        List<EvalAssignImportResultDTO.RowError> kept = total > errorKeep ? errors.subList(0, errorKeep) : errors;
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("total", total);
        payload.put("truncated", total > errorKeep);
        payload.put("errors", kept);
        return toJson(payload, "{\"total\":" + total + ",\"errors\":[]}");
    }

    private String buildExceptionSummary(Exception e) {
        String msg = "导入异常：" + (e.getMessage() == null ? e.getClass().getSimpleName() : e.getMessage());
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("total", 1);
        payload.put("truncated", false);
        payload.put("errors", List.of(new EvalAssignImportResultDTO.RowError(0, msg)));
        return toJson(payload, "{\"total\":1,\"errors\":[]}");
    }

    private String toJson(Object payload, String fallback) {
        try {
            return objectMapper.writeValueAsString(payload);
        } catch (Exception ex) {
            log.warn("[EvalRewardImportService.toJson] errorSummary 序列化失败: {}", ex.getMessage());
            return fallback;
        }
    }

    /** 设置单批写入上限（仅供测试覆盖默认配置）。 */
    void setBatchInsertSize(int batchInsertSize) {
        this.batchInsertSize = batchInsertSize;
    }

    /** 设置 ERROR_SUMMARY 行级错误封顶条数（仅供测试覆盖默认配置）。 */
    void setErrorKeep(int errorKeep) {
        this.errorKeep = errorKeep;
    }

    private static String trim(String s) {
        return s == null ? "" : s.trim();
    }

    private static EvalAssignImportResultDTO.RowError err(int row, String msg) {
        return new EvalAssignImportResultDTO.RowError(row, msg);
    }

    /** 逐行校验产物。 */
    private static final class ValidationOutcome {
        private final List<EvalAssignImportResultDTO.RowError> errors;
        private final List<EvalRewardItem> parsed;

        private ValidationOutcome(List<EvalAssignImportResultDTO.RowError> errors, List<EvalRewardItem> parsed) {
            this.errors = errors;
            this.parsed = parsed;
        }
    }
}
