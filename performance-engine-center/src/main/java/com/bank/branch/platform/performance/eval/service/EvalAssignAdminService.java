package com.bank.branch.platform.performance.eval.service;

import com.bank.branch.platform.auth.api.UserApi;
import com.bank.branch.platform.common.web.PageResult;
import com.bank.branch.platform.governance.api.DictApi;
import com.bank.branch.platform.performance.enums.PerfErrorCode;
import com.bank.branch.platform.performance.eval.entity.EvalAssignBatch;
import com.bank.branch.platform.performance.eval.entity.EvalAssignItem;
import com.bank.branch.platform.performance.eval.mapper.EvalAssignBatchMapper;
import com.bank.branch.platform.performance.eval.mapper.EvalAssignItemMapper;
import com.bank.branch.platform.performance.exception.PerfException;
import lombok.extern.slf4j.Slf4j;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.xssf.streaming.SXSSFWorkbook;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 待处理任务批次管理端服务。
 *
 * <p>提供批次列表、详情（含分页明细）、草稿发布、Excel 导出等管理端能力。</p>
 */
@Slf4j
@Service
public class EvalAssignAdminService {

    private static final int DEFAULT_PAGE_SIZE = 20;
    /** 评价类型字典类型（导出时编码→中文名称反查）。 */
    private static final String DICT_SCORE_TYPE = "EVAL_SCORE_TYPE";
    /** 导出分页拉取每页行数（流式导出，避免一次性把全量明细读进内存）。 */
    private static final int EXPORT_PAGE_SIZE = 5000;

    private final EvalAssignBatchMapper batchMapper;
    private final EvalAssignItemMapper itemMapper;
    private final DictApi dictApi;
    private final UserApi userApi;

    @Autowired
    public EvalAssignAdminService(EvalAssignBatchMapper batchMapper,
                                   EvalAssignItemMapper itemMapper,
                                   DictApi dictApi,
                                   UserApi userApi) {
        this.batchMapper = batchMapper;
        this.itemMapper = itemMapper;
        this.dictApi = dictApi;
        this.userApi = userApi;
    }

    /**
     * 分页查询导入批次列表。
     *
     * @param status   批次状态（null=全部，0=ACTIVE，1=CLOSED，2=DRAFT）
     * @param keyword  关键词（匹配批次ID/创建人）
     * @param page     页码，1-based
     * @param pageSize 每页条数
     * @return 分页结果（含 itemCount）
     */
    public PageResult<EvalAssignBatch> pageBatches(Integer status, String keyword, int page, int pageSize) {
        int offset = (page - 1) * pageSize;
        List<EvalAssignBatch> records = itemMapper.selectBatchesByCondition(status, keyword, offset, pageSize);
        long total = itemMapper.countBatchesByCondition(status, keyword);
        PageResult<EvalAssignBatch> result = new PageResult<>();
        result.setRecords(records);
        result.setTotal(total);
        result.setPageNo(page);
        result.setPageSize(pageSize);
        return result;
    }

    /**
     * 查询批次详情（含分页明细）。
     *
     * @param batchId  批次ID
     * @param page     明细页码，1-based
     * @param pageSize 明细每页条数
     * @return Map 含 batch 和 items 分页
     */
    public Map<String, Object> getBatchDetail(Long batchId, int page, int pageSize) {
        EvalAssignBatch batch = batchMapper.selectById(batchId);
        if (batch == null) {
            throw new PerfException(PerfErrorCode.EVAL_ASSIGN_ITEM_NOT_FOUND, batchId);
        }
        int offset = (page - 1) * pageSize;
        List<EvalAssignItem> items = itemMapper.selectByBatchId(batchId, offset, pageSize);
        long total = itemMapper.countByBatchId(batchId);

        // 明细里 eval_user_id / be_eval_user_id 存的是 USER_ID，详情需展示登录名(工号 PT_USER.username)。
        // 跨模块禁止直接 JOIN pt_user，统一走 auth 的 UserApi 按 USER_ID 批量反查。
        fillUsernames(items);

        Map<String, Object> result = new HashMap<>();
        result.put("batch", batch);
        PageResult<EvalAssignItem> itemPage = new PageResult<>();
        itemPage.setRecords(items);
        itemPage.setTotal(total);
        itemPage.setPageNo(page);
        itemPage.setPageSize(pageSize);
        result.put("items", itemPage);
        return result;
    }

    /**
     * 为明细批量填充打分人/被打分人登录名（PT_USER.username，即工号）。
     *
     * <p>明细持有的是 USER_ID，详情页需展示登录名。收集去重 USER_ID 后单次 {@link UserApi#getUserByEmpIds(List)}
     * 反查，避免逐行 N+1；查不到的 USER_ID（如已删除用户）回退为原 USER_ID 兜底展示。</p>
     *
     * @param items 待填充的明细列表
     */
    private void fillUsernames(List<EvalAssignItem> items) {
        if (items == null || items.isEmpty()) {
            return;
        }
        Set<String> userIds = new LinkedHashSet<>();
        for (EvalAssignItem it : items) {
            if (it.getEvalUserId() != null && !it.getEvalUserId().isEmpty()) {
                userIds.add(it.getEvalUserId());
            }
            if (it.getBeEvalUserId() != null && !it.getBeEvalUserId().isEmpty()) {
                userIds.add(it.getBeEvalUserId());
            }
        }
        if (userIds.isEmpty()) {
            return;
        }
        // 轻量批量反查（单次/分片 IN，仅取 USER_ID/USERNAME 两列，不逐人装配 DTO）
        Map<String, String> idToUsername = userApi.mapEmpIdsToUsername(new ArrayList<>(userIds));
        for (EvalAssignItem it : items) {
            it.setEvalUserUsername(idToUsername.getOrDefault(it.getEvalUserId(), it.getEvalUserId()));
            it.setBeEvalUserUsername(idToUsername.getOrDefault(it.getBeEvalUserId(), it.getBeEvalUserId()));
        }
    }

    /**
     * 确认发布草稿批次 → ACTIVE(0)。
     *
     * @param batchId 批次ID
     * @return 更新后的批次
     */
    @Transactional(rollbackFor = Exception.class)
    public EvalAssignBatch publishBatch(Long batchId) {
        EvalAssignBatch batch = batchMapper.selectById(batchId);
        if (batch == null) {
            throw new PerfException(PerfErrorCode.EVAL_ASSIGN_ITEM_NOT_FOUND, batchId);
        }
        if (batch.getStatus() == null || batch.getStatus() != 2) {
            throw new PerfException(PerfErrorCode.EVAL_BATCH_NOT_DRAFT);
        }
        batch.setStatus(0);
        batchMapper.updateById(batch);
        log.info("[EvalAssignAdminService.publishBatch] 发布成功 batchId={}", batchId);
        return batch;
    }

    /**
     * 导出批次明细为 Excel。
     *
     * @param batchId 批次ID
     * @return Excel 字节流
     */
    public byte[] exportItems(Long batchId) {
        EvalAssignBatch batch = batchMapper.selectById(batchId);
        if (batch == null) {
            throw new PerfException(PerfErrorCode.EVAL_ASSIGN_ITEM_NOT_FOUND, batchId);
        }
        // 1. 一次性反查工号：明细行可达 20 万，但去重人数有限（打分人∪被打分人），
        //    先取去重 USER_ID（一条带索引查询），再走轻量分片 IN（mapEmpIdsToUsername）拿 USER_ID→USERNAME，
        //    后续逐行 O(1) 内存查找，避免逐行 N+1 与全量入内存。
        List<String> userIds = itemMapper.selectDistinctUserIdsByBatch(batchId);
        Map<String, String> idToUsername = (userIds == null || userIds.isEmpty())
                ? java.util.Collections.emptyMap()
                : userApi.mapEmpIdsToUsername(userIds);
        // 评价类型字典 label 缓存：NUM/GRADE 仅两种，逐行复用避免 20 万次字典查询
        Map<String, String> scoreTypeLabelCache = new HashMap<>();

        DateTimeFormatter dtf = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
        // SXSSF 流式写：rowAccessWindowSize=100，内存仅保留最近 100 行，写满落盘，支撑 20 万行不 OOM
        SXSSFWorkbook wb = new SXSSFWorkbook(100);
        try {
            Sheet sheet = wb.createSheet("评价明细");
            Row header = sheet.createRow(0);
            String[] cols = {"打分人工号", "打分人姓名", "打分人标签", "打分人部门",
                    "被打分人工号", "被打分人姓名", "被打分人标签", "被打分人部门",
                    "权重标签", "评价类型", "分数", "提交状态", "提交时间"};
            for (int i = 0; i < cols.length; i++) {
                header.createCell(i).setCellValue(cols[i]);
            }
            // 2. 分页拉取明细流式写出（每页 EXPORT_PAGE_SIZE 行），避免一次性把 20 万行读进内存
            int rowIdx = 1;
            int offset = 0;
            while (true) {
                List<EvalAssignItem> items = itemMapper.selectByBatchId(batchId, offset, EXPORT_PAGE_SIZE);
                if (items == null || items.isEmpty()) {
                    break;
                }
                for (EvalAssignItem it : items) {
                    Row row = sheet.createRow(rowIdx++);
                    // 工号列展示 PT_USER.USERNAME（查不到回退原 USER_ID 兜底）
                    String scorerName = idToUsername.getOrDefault(it.getEvalUserId(), it.getEvalUserId());
                    String targetName = idToUsername.getOrDefault(it.getBeEvalUserId(), it.getBeEvalUserId());
                    row.createCell(0).setCellValue(scorerName == null ? "" : scorerName);
                    row.createCell(1).setCellValue(it.getEvalUserName() == null ? "" : it.getEvalUserName());
                    row.createCell(2).setCellValue(it.getEvalUserTag() == null ? "" : it.getEvalUserTag());
                    row.createCell(3).setCellValue(it.getEvalUserDept() == null ? "" : it.getEvalUserDept());
                    row.createCell(4).setCellValue(targetName == null ? "" : targetName);
                    row.createCell(5).setCellValue(it.getBeEvalUserName() == null ? "" : it.getBeEvalUserName());
                    row.createCell(6).setCellValue(it.getBeEvalTag() == null ? "" : it.getBeEvalTag());
                    row.createCell(7).setCellValue(it.getBeEvalDept() == null ? "" : it.getBeEvalDept());
                    row.createCell(8).setCellValue(it.getWeightTag() == null ? "" : it.getWeightTag());
                    // 评价类型：DB 存字典编码（NUM/GRADE），导出按 EVAL_SCORE_TYPE 字典反查中文名（缓存复用）
                    String st = it.getScoreType();
                    row.createCell(9).setCellValue(st == null ? ""
                            : scoreTypeLabelCache.computeIfAbsent(st, k -> dictApi.getDictLabel(DICT_SCORE_TYPE, k)));
                    row.createCell(10).setCellValue(it.getScore() == null ? "" : String.valueOf(it.getScore()));
                    row.createCell(11).setCellValue(it.getSubmitted() != null && it.getSubmitted() == 1 ? "已提交" : "未提交");
                    row.createCell(12).setCellValue(it.getSubmitTime() == null ? "" : it.getSubmitTime().format(dtf));
                }
                if (items.size() < EXPORT_PAGE_SIZE) {
                    break;
                }
                offset += EXPORT_PAGE_SIZE;
            }
            ByteArrayOutputStream bos = new ByteArrayOutputStream();
            wb.write(bos);
            return bos.toByteArray();
        } catch (IOException e) {
            log.error("[EvalAssignAdminService.exportItems] 导出失败 batchId={}", batchId, e);
            throw new PerfException(PerfErrorCode.EVAL_IMPORT_FILE_INVALID, "Excel 生成失败");
        } finally {
            // SXSSF 必须 dispose 清理临时落盘文件，否则磁盘泄漏
            wb.dispose();
            try {
                wb.close();
            } catch (IOException ignore) {
                // 关闭异常不影响已生成的字节流
            }
        }
    }
}
