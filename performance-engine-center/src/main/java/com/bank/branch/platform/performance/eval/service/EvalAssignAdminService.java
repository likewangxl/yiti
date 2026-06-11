package com.bank.branch.platform.performance.eval.service;

import com.bank.branch.platform.common.web.PageResult;
import com.bank.branch.platform.performance.enums.PerfErrorCode;
import com.bank.branch.platform.performance.eval.entity.EvalAssignBatch;
import com.bank.branch.platform.performance.eval.entity.EvalAssignItem;
import com.bank.branch.platform.performance.eval.mapper.EvalAssignBatchMapper;
import com.bank.branch.platform.performance.eval.mapper.EvalAssignItemMapper;
import com.bank.branch.platform.performance.exception.PerfException;
import lombok.extern.slf4j.Slf4j;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 待处理任务批次管理端服务。
 *
 * <p>提供批次列表、详情（含分页明细）、草稿发布、Excel 导出等管理端能力。</p>
 */
@Slf4j
@Service
public class EvalAssignAdminService {

    private static final int DEFAULT_PAGE_SIZE = 20;

    private final EvalAssignBatchMapper batchMapper;
    private final EvalAssignItemMapper itemMapper;

    @Autowired
    public EvalAssignAdminService(EvalAssignBatchMapper batchMapper,
                                   EvalAssignItemMapper itemMapper) {
        this.batchMapper = batchMapper;
        this.itemMapper = itemMapper;
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
        List<EvalAssignItem> items = itemMapper.selectByBatchId(batchId, 0, 10000);

        try (Workbook wb = new XSSFWorkbook()) {
            Sheet sheet = wb.createSheet("评价明细");
            Row header = sheet.createRow(0);
            String[] cols = {"打分人工号", "打分人姓名", "打分人标签", "打分人部门",
                    "被打分人工号", "被打分人姓名", "被打分人标签", "被打分人部门",
                    "权重标签", "评价类型", "分数", "提交状态", "提交时间"};
            for (int i = 0; i < cols.length; i++) {
                header.createCell(i).setCellValue(cols[i]);
            }
            DateTimeFormatter dtf = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
            for (int i = 0; i < items.size(); i++) {
                EvalAssignItem it = items.get(i);
                Row row = sheet.createRow(i + 1);
                row.createCell(0).setCellValue(it.getEvalUserId());
                row.createCell(1).setCellValue(it.getEvalUserName() == null ? "" : it.getEvalUserName());
                row.createCell(2).setCellValue(it.getEvalUserTag() == null ? "" : it.getEvalUserTag());
                row.createCell(3).setCellValue(it.getEvalUserDept() == null ? "" : it.getEvalUserDept());
                row.createCell(4).setCellValue(it.getBeEvalUserId());
                row.createCell(5).setCellValue(it.getBeEvalUserName() == null ? "" : it.getBeEvalUserName());
                row.createCell(6).setCellValue(it.getBeEvalTag() == null ? "" : it.getBeEvalTag());
                row.createCell(7).setCellValue(it.getBeEvalDept() == null ? "" : it.getBeEvalDept());
                row.createCell(8).setCellValue(it.getWeightTag() == null ? "" : it.getWeightTag());
                row.createCell(9).setCellValue(it.getScoreType() == null ? "" : it.getScoreType());
                row.createCell(10).setCellValue(it.getScore() == null ? "" : String.valueOf(it.getScore()));
                row.createCell(11).setCellValue(it.getSubmitted() != null && it.getSubmitted() == 1 ? "已提交" : "未提交");
                row.createCell(12).setCellValue(it.getSubmitTime() == null ? "" : it.getSubmitTime().format(dtf));
            }
            ByteArrayOutputStream bos = new ByteArrayOutputStream();
            wb.write(bos);
            return bos.toByteArray();
        } catch (IOException e) {
            log.error("[EvalAssignAdminService.exportItems] 导出失败 batchId={}", batchId, e);
            throw new PerfException(PerfErrorCode.EVAL_IMPORT_FILE_INVALID, "Excel 生成失败");
        }
    }
}
