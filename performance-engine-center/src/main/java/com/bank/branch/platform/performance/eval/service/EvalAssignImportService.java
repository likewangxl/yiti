package com.bank.branch.platform.performance.eval.service;

import com.alibaba.excel.EasyExcel;
import com.bank.branch.platform.auth.api.UserApi;
import com.bank.branch.platform.governance.api.DictApi;
import com.bank.branch.platform.governance.api.dto.DictItemDTO;
import com.bank.branch.platform.performance.enums.PerfErrorCode;
import com.bank.branch.platform.performance.eval.dto.EvalAssignImportResultDTO;
import com.bank.branch.platform.performance.eval.dto.EvalAssignImportRow;
import com.bank.branch.platform.performance.eval.entity.EvalAssignBatch;
import com.bank.branch.platform.performance.eval.entity.EvalAssignItem;
import com.bank.branch.platform.performance.eval.mapper.EvalAssignBatchMapper;
import com.bank.branch.platform.performance.eval.mapper.EvalAssignItemMapper;
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

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 待处理任务（评价任务）Excel 导入服务（异步化）。
 *
 * <p>导入由「同步原子」改为「异步 + 前端轮询」：接口线程仅 {@link #parseRows} 同步解析 +
 * {@link #createImportingBatch} 建 IMPORTING(3) 批次并立即返回 batchId；
 * 逐行校验入库挪到 {@link #processImport} 后台异步执行，前端轮询批次状态获取结果。</p>
 *
 * <p>校验规则与错误码完全保留：双方「员工编号」填登录用户名(PT_USER.USER_NAME)且为系统有效员工
 * + 权重标签命中字典 + 评价类型命中字典 + 文件内配对不重复，all-or-none（任一行错误则一条不写，
 * 失败明细以 JSON 写入批次 ERROR_SUMMARY）。截止时间随上传单独传入，作用于整批。</p>
 */
@Slf4j
@Service
public class EvalAssignImportService {

    /** 权重标签字典类型。 */
    private static final String DICT_WEIGHT_TAG = "EVAL_WEIGHT_TAG";
    /** 评价类型字典类型。 */
    private static final String DICT_SCORE_TYPE = "EVAL_SCORE_TYPE";

    /** 批次状态：草稿（全部通过、待人工确认发布）。 */
    private static final int STATUS_DRAFT = 2;
    /** 批次状态：处理中（接口受理即置此态）。 */
    private static final int STATUS_IMPORTING = 3;
    /** 批次状态：导入失败（校验未过 / 后台异常 / 超时补偿）。 */
    private static final int STATUS_FAILED = 4;

    /**
     * 单次 batchInsert 的最大明细条数：超过则分多批写入，避免单条 INSERT 语句过大触发
     * MySQL {@code max_allowed_packet} 上限。默认 1000，可经 {@code application.yml}
     * 的 {@code perf.eval.import.batch-insert-size} 覆盖（不写死）。
     */
    @Value("${perf.eval.import.batch-insert-size:1000}")
    private int batchInsertSize = 1000;

    /**
     * 失败时写入 ERROR_SUMMARY 的行级错误明细封顶条数：防 MEDIUMTEXT 撑爆。
     * 默认 500，可经 {@code application.yml} 的 {@code perf.eval.import.error-keep} 覆盖（不写死）。
     */
    @Value("${perf.eval.import.error-keep:500}")
    private int errorKeep = 500;

    private final UserApi userApi;
    private final DictApi dictApi;
    private final EvalAssignBatchMapper batchMapper;
    private final EvalAssignItemMapper itemMapper;
    private final ObjectMapper objectMapper;
    /** 成功路径「批量插明细 + 更新草稿状态」同一事务（显式事务模板，规避 @Async 自调用代理失效）。 */
    private final TransactionTemplate txTemplate;

    public EvalAssignImportService(UserApi userApi, DictApi dictApi,
                                   EvalAssignBatchMapper batchMapper,
                                   EvalAssignItemMapper itemMapper,
                                   ObjectMapper objectMapper,
                                   PlatformTransactionManager transactionManager) {
        this.userApi = userApi;
        this.dictApi = dictApi;
        this.batchMapper = batchMapper;
        this.itemMapper = itemMapper;
        this.objectMapper = objectMapper;
        this.txTemplate = new TransactionTemplate(transactionManager);
    }

    /**
     * 同步解析上传的 Excel 为内存行集（接口线程调用）。
     *
     * <p>文件空/格式错在此快速反馈，仍走原错误码（{@link PerfErrorCode#EVAL_IMPORT_FILE_EMPTY}
     * / {@link PerfErrorCode#EVAL_IMPORT_FILE_INVALID}），不进入异步处理。</p>
     *
     * @param file 上传的 .xlsx 文件
     * @return 解析后的行集
     */
    public List<EvalAssignImportRow> parseRows(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new PerfException(PerfErrorCode.EVAL_IMPORT_FILE_EMPTY);
        }
        try {
            return EasyExcel.read(file.getInputStream())
                    .head(EvalAssignImportRow.class)
                    .sheet()
                    .doReadSync();
        } catch (Exception e) {
            log.warn("[EvalAssignImportService.parseRows] 解析失败: {}", e.getMessage());
            throw new PerfException(PerfErrorCode.EVAL_IMPORT_FILE_INVALID, e.getMessage());
        }
    }

    /**
     * 建 IMPORTING(3) 批次并独立事务提交，使 batchId 立即对前端轮询可见。
     *
     * <p>用 {@code REQUIRES_NEW} 独立提交：与后续异步处理彻底解耦，即便异步线程尚未启动，
     * 前端也能据 batchId 轮询到「处理中」批次。</p>
     *
     * @param taskType 待处理任务类型（EVAL/REWARD）
     * @param taskName 任务名称
     * @param deadline 打分截止时间
     * @param createBy 创建人工号
     * @return 新建批次的主键
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW, rollbackFor = Exception.class)
    public Long createImportingBatch(String taskType, String taskName,
                                     LocalDateTime deadline, String createBy) {
        EvalAssignBatch batch = new EvalAssignBatch();
        batch.setTaskType(taskType);
        batch.setBatchName(taskName);
        batch.setSource("IMPORT");
        batch.setDeadline(deadline);
        batch.setStatus(STATUS_IMPORTING);
        batch.setCreateBy(createBy);
        batch.setCreateTime(LocalDateTime.now());
        batchMapper.insert(batch);
        log.info("[EvalAssignImportService.createImportingBatch] 受理导入 batchId={} taskType={} createBy={}",
                batch.getBatchId(), taskType, createBy);
        return batch.getBatchId();
    }

    /**
     * 异步逐行校验 + 入库（后台线程执行，不阻塞接口）。
     *
     * <p>流程：逐行业务校验（完全复用既有规则）→ 有错误则批次置 STATUS=4 + ERROR_SUMMARY(前 N 条 JSON)
     * + TOTAL_ROWS，明细一条不写（天然 all-or-none）；全通过则同一事务批量插明细 + 批次置
     * STATUS=2(草稿) + IMPORTED_COUNT + TOTAL_ROWS；兜底 catch 真异常 → 独立更新批次置
     * STATUS=4 + ERROR_SUMMARY(异常摘要)。</p>
     *
     * @param batchId  已建好的 IMPORTING 批次 ID
     * @param rows     解析后的行集
     * @param taskType 待处理任务类型（仅日志）
     * @param taskName 任务名称（仅日志）
     * @param deadline 打分截止时间
     * @param createBy 创建人工号（仅日志）
     */
    @Async("evalImportExecutor")
    public void processImport(Long batchId, List<EvalAssignImportRow> rows, String taskType,
                              String taskName, LocalDateTime deadline, String createBy) {
        int total = rows == null ? 0 : rows.size();
        try {
            ValidationOutcome outcome = validate(rows, deadline);
            if (!outcome.errors.isEmpty()) {
                // 行级错误：天然 all-or-none，批次置失败，明细一条不写
                markFailed(batchId, buildErrorSummary(outcome.errors), total);
                log.info("[EvalAssignImportService.processImport] batchId={} 校验失败 {} 条错误（共 {} 行）",
                        batchId, outcome.errors.size(), total);
                return;
            }
            persistSuccess(batchId, outcome.parsed, total);
            log.info("[EvalAssignImportService.processImport] batchId={} 导入成功 {} 条", batchId, outcome.parsed.size());
        } catch (Exception e) {
            // 兜底真异常：独立更新批次为失败，不向上抛出（异步线程无调用方接收）
            log.error("[EvalAssignImportService.processImport] batchId={} 导入异常", batchId, e);
            markFailed(batchId, buildExceptionSummary(e), total);
        }
    }

    /**
     * 全通过路径：批量插明细 + 更新批次草稿状态，同一事务（一起成功/回滚）。
     *
     * @param batchId 批次 ID
     * @param parsed  待入库明细
     * @param total   解析总行数
     */
    void persistSuccess(Long batchId, List<EvalAssignItem> parsed, int total) {
        txTemplate.executeWithoutResult(status -> {
            for (EvalAssignItem item : parsed) {
                item.setBatchId(batchId);
            }
            // 分批写入：单批不超过 batchInsertSize，规避单条 INSERT 过大触发 max_allowed_packet
            for (int from = 0; from < parsed.size(); from += batchInsertSize) {
                int to = Math.min(from + batchInsertSize, parsed.size());
                itemMapper.batchInsert(parsed.subList(from, to));
            }
            EvalAssignBatch batch = batchMapper.selectById(batchId);
            batch.setStatus(STATUS_DRAFT); // 草稿：需管理员确认发布后才变为 ACTIVE(0)
            batch.setImportedCount(parsed.size());
            batch.setTotalRows(total);
            batchMapper.updateById(batch);
        });
    }

    /**
     * 失败路径：批次置 STATUS=4 + ERROR_SUMMARY + TOTAL_ROWS（独立单条 UPDATE，不被业务事务牵连）。
     *
     * @param batchId      批次 ID
     * @param errorSummary 错误明细 JSON（或异常摘要）
     * @param total        解析总行数
     */
    void markFailed(Long batchId, String errorSummary, int total) {
        EvalAssignBatch batch = batchMapper.selectById(batchId);
        if (batch == null) {
            log.warn("[EvalAssignImportService.markFailed] batchId={} 不存在，跳过置失败", batchId);
            return;
        }
        batch.setStatus(STATUS_FAILED);
        batch.setErrorSummary(errorSummary);
        batch.setTotalRows(total);
        batchMapper.updateById(batch);
    }

    /**
     * 逐行校验（完全复用既有规则），不写库。
     *
     * @param rows     解析后的行
     * @param deadline 打分截止时间
     * @return 校验产物：errors 非空表示 all-or-none 失败，否则 parsed 为待入库明细
     */
    private ValidationOutcome validate(List<EvalAssignImportRow> rows, LocalDateTime deadline) {
        if (rows == null || rows.isEmpty()) {
            throw new PerfException(PerfErrorCode.EVAL_IMPORT_FILE_EMPTY);
        }
        if (deadline == null) {
            throw new PerfException(PerfErrorCode.EVAL_TASK_END_TIME_INVALID, (Object) null);
        }

        // 1. 字典：权重标签合法集合（同时接受标签/编码），评价类型 文本→编码 映射
        Set<String> validWeights = new HashSet<>();
        for (DictItemDTO d : dictApi.getDictItems(DICT_WEIGHT_TAG)) {
            validWeights.add(d.getDictLabel());
            validWeights.add(d.getDictCode());
        }
        Map<String, String> scoreTypeMap = new HashMap<>();
        for (DictItemDTO d : dictApi.getDictItems(DICT_SCORE_TYPE)) {
            scoreTypeMap.put(d.getDictLabel(), d.getDictCode());
            scoreTypeMap.put(d.getDictCode(), d.getDictCode());
        }

        // 2. 员工有效性：Excel「员工编号」列填写的是登录用户名（PT_USER.USER_NAME），
        //    只按 USERNAME 校验（不再接受 USER_ID），构建 用户名→规范USER_ID 映射。
        //    入库仍归一为 USER_ID，下游「我的待处理任务」按 USER_ID 匹配当前登录人。
        Set<String> allNames = new HashSet<>();
        for (EvalAssignImportRow r : rows) {
            if (r.getBeEvalUserId() != null && !r.getBeEvalUserId().trim().isEmpty()) {
                allNames.add(r.getBeEvalUserId().trim());
            }
            if (r.getEvalUserId() != null && !r.getEvalUserId().trim().isEmpty()) {
                allNames.add(r.getEvalUserId().trim());
            }
        }
        // 用户名→规范USER_ID 映射：走轻量批量查询（单次/分片 IN，仅取 USERNAME/USER_ID），
        // 规避 getUsersByUsernames 的 N+1；导入只需存在性 + 工号归一。
        Map<String, String> nameToUserId = allNames.isEmpty()
                ? new HashMap<>()
                : userApi.mapUsernamesToEmpId(new ArrayList<>(allNames));
        Set<String> existingNames = nameToUserId.keySet();

        // 3. 逐行校验
        List<EvalAssignImportResultDTO.RowError> errors = new ArrayList<>();
        List<EvalAssignItem> parsed = new ArrayList<>();
        Set<String> seenPairs = new HashSet<>();

        for (int i = 0; i < rows.size(); i++) {
            int rowNo = i + 1;
            EvalAssignImportRow r = rows.get(i);
            String beId = trim(r.getBeEvalUserId());
            String evId = trim(r.getEvalUserId());

            if (beId.isEmpty()) {
                errors.add(err(rowNo, "被打分员工编号不能为空"));
                continue;
            }
            if (evId.isEmpty()) {
                errors.add(err(rowNo, "打分员工编号不能为空"));
                continue;
            }
            if (!existingNames.contains(beId)) {
                errors.add(err(rowNo, "被打分员工用户名不存在：" + beId));
                continue;
            }
            if (!existingNames.contains(evId)) {
                errors.add(err(rowNo, "打分员工用户名不存在：" + evId));
                continue;
            }
            // 输入是 USERNAME，规范化为真正的 USER_ID 后再存储（下游按 USER_ID 匹配打分人）
            beId = nameToUserId.getOrDefault(beId, beId);
            evId = nameToUserId.getOrDefault(evId, evId);
            // 权重标签：必填且命中字典
            String weight = trim(r.getWeightTag());
            if (weight.isEmpty()) {
                errors.add(err(rowNo, "权重标签不能为空"));
                continue;
            }
            if (!validWeights.contains(weight)) {
                errors.add(err(rowNo, "权重标签不存在：" + weight));
                continue;
            }
            // 评价类型：必填且命中字典，映射为编码
            String scoreTypeText = trim(r.getScoreTypeText());
            if (scoreTypeText.isEmpty()) {
                errors.add(err(rowNo, "评价类型不能为空"));
                continue;
            }
            String scoreType = scoreTypeMap.get(scoreTypeText);
            if (scoreType == null) {
                errors.add(err(rowNo, "评价类型不存在：" + scoreTypeText));
                continue;
            }
            // 文件内配对去重
            String pairKey = evId + "-" + beId;
            if (!seenPairs.add(pairKey)) {
                errors.add(err(rowNo, "打分人与被打分人组合在文件内重复"));
                continue;
            }

            EvalAssignItem item = new EvalAssignItem();
            item.setEvalUserId(evId);
            item.setEvalUserName(trim(r.getEvalUserName()));
            item.setEvalUserTag(trim(r.getEvalUserTag()));
            item.setEvalUserDept(trim(r.getEvalUserDept()));
            item.setBeEvalUserId(beId);
            item.setBeEvalUserName(trim(r.getBeEvalUserName()));
            item.setBeEvalDept(trim(r.getBeEvalDept()));
            item.setGroupDept(trim(r.getGroupDept()));
            item.setBeEvalTag(trim(r.getBeEvalTag()));
            item.setWeightTag(weight);
            item.setScoreType(scoreType);
            item.setSubmitted(0);
            parsed.add(item);
        }
        return new ValidationOutcome(errors, parsed);
    }

    /**
     * 把行级错误（前 N 条 + 总错误数）序列化为 ERROR_SUMMARY JSON。
     *
     * @param errors 全部行级错误
     * @return JSON 字符串：{@code {"total":<总数>,"truncated":<是否截断>,"errors":[{row,message}...]}}
     */
    private String buildErrorSummary(List<EvalAssignImportResultDTO.RowError> errors) {
        int total = errors.size();
        List<EvalAssignImportResultDTO.RowError> kept = total > errorKeep ? errors.subList(0, errorKeep) : errors;
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("total", total);
        payload.put("truncated", total > errorKeep);
        payload.put("errors", kept);
        return toJson(payload, "{\"total\":" + total + ",\"errors\":[]}");
    }

    /**
     * 把后台真异常摘要序列化为 ERROR_SUMMARY JSON（行号 0 占位，便于前端统一用错误表格展示）。
     *
     * @param e 异常
     * @return JSON 字符串
     */
    private String buildExceptionSummary(Exception e) {
        String msg = "导入异常：" + (e.getMessage() == null ? e.getClass().getSimpleName() : e.getMessage());
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("total", 1);
        payload.put("truncated", false);
        payload.put("errors", List.of(new EvalAssignImportResultDTO.RowError(0, msg)));
        return toJson(payload, "{\"total\":1,\"errors\":[]}");
    }

    /** 用项目现有 Jackson ObjectMapper 序列化，失败时返回兜底串（避免置失败动作再被序列化异常打断）。 */
    private String toJson(Object payload, String fallback) {
        try {
            return objectMapper.writeValueAsString(payload);
        } catch (Exception ex) {
            log.warn("[EvalAssignImportService.toJson] errorSummary 序列化失败: {}", ex.getMessage());
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
        private final List<EvalAssignItem> parsed;

        private ValidationOutcome(List<EvalAssignImportResultDTO.RowError> errors, List<EvalAssignItem> parsed) {
            this.errors = errors;
            this.parsed = parsed;
        }
    }
}
