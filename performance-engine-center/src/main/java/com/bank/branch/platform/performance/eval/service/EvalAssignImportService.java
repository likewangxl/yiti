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
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 待处理任务（评价任务）Excel 导入服务。
 *
 * <p>同步、原子：全部行校验通过才建批次并批量入库；任一行错误则一条都不写（all-or-none），返回行级错误明细。</p>
 * <p>校验：双方「员工编号」填登录用户名(PT_USER.USER_NAME)且为系统有效员工（部门/标签不校验，入库归一为 USER_ID）
 * + 权重标签命中字典 + 评价类型命中字典 + 文件内配对不重复。
 * 截止时间随上传单独传入，作用于整批。</p>
 */
@Slf4j
@Service
public class EvalAssignImportService {

    /** 权重标签字典类型。 */
    private static final String DICT_WEIGHT_TAG = "EVAL_WEIGHT_TAG";
    /** 评价类型字典类型。 */
    private static final String DICT_SCORE_TYPE = "EVAL_SCORE_TYPE";

    /**
     * 单次 batchInsert 的最大明细条数：超过则分多批写入，避免单条 INSERT 语句过大触发
     * MySQL {@code max_allowed_packet} 上限。默认 1000，可经 {@code application.yml}
     * 的 {@code perf.eval.import.batch-insert-size} 覆盖（不写死）。
     */
    @Value("${perf.eval.import.batch-insert-size:1000}")
    private int batchInsertSize = 1000;

    private final UserApi userApi;
    private final DictApi dictApi;
    private final EvalAssignBatchMapper batchMapper;
    private final EvalAssignItemMapper itemMapper;

    public EvalAssignImportService(UserApi userApi, DictApi dictApi,
                                   EvalAssignBatchMapper batchMapper,
                                   EvalAssignItemMapper itemMapper) {
        this.userApi = userApi;
        this.dictApi = dictApi;
        this.batchMapper = batchMapper;
        this.itemMapper = itemMapper;
    }

    /**
     * 解析并导入 Excel。
     *
     * @param file     上传的 .xlsx 文件
     * @param taskType 待处理任务类型（EVAL/REWARD）
     * @param taskName 任务名称（必填）
     * @param deadline 打分截止时间
     * @param createBy 创建人工号
     * @return 导入结果
     */
    public EvalAssignImportResultDTO importExcel(MultipartFile file, String taskType,
                                                 String taskName, LocalDateTime deadline, String createBy) {
        if (file == null || file.isEmpty()) {
            throw new PerfException(PerfErrorCode.EVAL_IMPORT_FILE_EMPTY);
        }
        List<EvalAssignImportRow> rows;
        try {
            rows = EasyExcel.read(file.getInputStream())
                    .head(EvalAssignImportRow.class)
                    .sheet()
                    .doReadSync();
        } catch (Exception e) {
            log.warn("[EvalAssignImportService.importExcel] 解析失败: {}", e.getMessage());
            throw new PerfException(PerfErrorCode.EVAL_IMPORT_FILE_INVALID, e.getMessage());
        }
        return importRows(rows, taskType, taskName, deadline, createBy);
    }

    /**
     * 校验全部行并原子入库。
     *
     * @param rows     解析后的行
     * @param taskType 待处理任务类型（EVAL/REWARD）
     * @param taskName 任务名称
     * @param deadline 打分截止时间
     * @param createBy 创建人工号
     * @return 导入结果
     */
    @Transactional(rollbackFor = Exception.class)
    public EvalAssignImportResultDTO importRows(List<EvalAssignImportRow> rows, String taskType,
                                                String taskName, LocalDateTime deadline, String createBy) {
        EvalAssignImportResultDTO result = new EvalAssignImportResultDTO();
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
        //    入库仍归一为 USER_ID，下游「我的待处理任务」按 USER_ID 匹配当前登录人（CurrentUserApi.getCurrentEmpId）。
        Set<String> allNames = new HashSet<>();
        for (EvalAssignImportRow r : rows) {
            if (r.getBeEvalUserId() != null && !r.getBeEvalUserId().trim().isEmpty()) {
                allNames.add(r.getBeEvalUserId().trim());
            }
            if (r.getEvalUserId() != null && !r.getEvalUserId().trim().isEmpty()) {
                allNames.add(r.getEvalUserId().trim());
            }
        }
        // 用户名→规范USER_ID 映射：走轻量批量查询（单次/分片 IN，仅取 USERNAME/USER_ID，
        // 不逐人装配机构/角色等 DTO，规避 getUsersByUsernames 的 N+1；导入只需存在性 + 工号归一）。
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
            String pairKey = evId + "" + beId;
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
            item.setBeEvalTag(trim(r.getBeEvalTag()));
            item.setWeightTag(weight);
            item.setScoreType(scoreType);
            item.setSubmitted(0);
            parsed.add(item);
        }

        // 4. 任一行错误 → 整体不入库
        if (!errors.isEmpty()) {
            result.setSuccess(false);
            result.setImportedCount(0);
            result.setErrors(errors);
            return result;
        }

        // 5. 全部通过 → 建批次 + 批量插明细
        EvalAssignBatch batch = new EvalAssignBatch();
        batch.setTaskType(taskType);
        batch.setBatchName(taskName);
        batch.setSource("IMPORT");
        batch.setDeadline(deadline);
        batch.setStatus(2); // 导入后置为 DRAFT，需管理员确认发布后才变为 ACTIVE(0)
        batch.setCreateBy(createBy);
        batch.setCreateTime(LocalDateTime.now());
        batchMapper.insert(batch);

        for (EvalAssignItem item : parsed) {
            item.setBatchId(batch.getBatchId());
        }
        // 分批写入：单批不超过 batchInsertSize，规避单条 INSERT 过大触发 max_allowed_packet
        for (int from = 0; from < parsed.size(); from += batchInsertSize) {
            int to = Math.min(from + batchInsertSize, parsed.size());
            itemMapper.batchInsert(parsed.subList(from, to));
        }

        result.setSuccess(true);
        result.setImportedCount(parsed.size());
        log.info("[EvalAssignImportService.importRows] 导入成功 batchId={} count={}", batch.getBatchId(), parsed.size());
        return result;
    }

    /** 设置单批写入上限（仅供测试覆盖默认配置）。 */
    void setBatchInsertSize(int batchInsertSize) {
        this.batchInsertSize = batchInsertSize;
    }

    private static String trim(String s) {
        return s == null ? "" : s.trim();
    }

    private static EvalAssignImportResultDTO.RowError err(int row, String msg) {
        return new EvalAssignImportResultDTO.RowError(row, msg);
    }
}
