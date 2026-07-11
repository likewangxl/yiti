package com.bank.branch.platform.performance.eval.service;

import com.bank.branch.platform.auth.api.UserApi;
import com.bank.branch.platform.common.web.PageResult;
import com.bank.branch.platform.performance.enums.PerfErrorCode;
import com.bank.branch.platform.performance.eval.entity.EvalAssignBatch;
import com.bank.branch.platform.performance.eval.entity.EvalRewardItem;
import com.bank.branch.platform.performance.eval.mapper.EvalAssignBatchMapper;
import com.bank.branch.platform.performance.eval.mapper.EvalRewardItemMapper;
import com.bank.branch.platform.performance.exception.PerfException;
import lombok.extern.slf4j.Slf4j;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.xssf.streaming.SXSSFWorkbook;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 奖励分配（REWARD）批次管理端服务。
 *
 * <p>提供 REWARD 批次列表、详情（含分页明细）、Excel 导出。发布(草稿→ACTIVE)复用
 * {@link EvalAssignAdminService#publishBatch(Long)}（仅改批次状态，与 item 表无关）。</p>
 */
@Slf4j
@Service
public class EvalRewardAdminService {

    /** 导出分页拉取每页行数（流式导出）。 */
    private static final int EXPORT_PAGE_SIZE = 5000;

    private final EvalAssignBatchMapper batchMapper;
    private final EvalRewardItemMapper itemMapper;
    private final UserApi userApi;

    @Autowired
    public EvalRewardAdminService(EvalAssignBatchMapper batchMapper,
                                  EvalRewardItemMapper itemMapper,
                                  UserApi userApi) {
        this.batchMapper = batchMapper;
        this.itemMapper = itemMapper;
        this.userApi = userApi;
    }

    /**
     * 分页查询 REWARD 导入批次列表。
     *
     * @param status   批次状态（null=全部）
     * @param keyword  关键词（匹配批次ID/创建人）
     * @param page     页码，1-based
     * @param pageSize 每页条数
     * @return 分页结果（含 itemCount）
     */
    public PageResult<EvalAssignBatch> pageBatches(Integer status, String keyword, int page, int pageSize) {
        int offset = (page - 1) * pageSize;
        List<EvalAssignBatch> records = itemMapper.selectRewardBatchesByCondition(status, keyword, offset, pageSize);
        long total = itemMapper.countRewardBatchesByCondition(status, keyword);
        PageResult<EvalAssignBatch> result = new PageResult<>();
        result.setRecords(records);
        result.setTotal(total);
        result.setPageNo(page);
        result.setPageSize(pageSize);
        return result;
    }

    /**
     * 查询批次详情（含分页明细，分配人工号反查 username）。
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
        List<EvalRewardItem> items = itemMapper.selectByBatchId(batchId, offset, pageSize);
        long total = itemMapper.countByBatchId(batchId);
        fillAssignerUsernames(items);

        Map<String, Object> result = new HashMap<>();
        result.put("batch", batch);
        PageResult<EvalRewardItem> itemPage = new PageResult<>();
        itemPage.setRecords(items);
        itemPage.setTotal(total);
        itemPage.setPageNo(page);
        itemPage.setPageSize(pageSize);
        result.put("items", itemPage);
        return result;
    }

    /**
     * 为明细批量填充分配人登录名（PT_USER.username）。
     *
     * <p>明细持有的是 USER_ID，详情页需展示登录名。收集去重 USER_ID 后单次反查，避免逐行 N+1。</p>
     */
    private void fillAssignerUsernames(List<EvalRewardItem> items) {
        if (items == null || items.isEmpty()) {
            return;
        }
        Set<String> ids = new LinkedHashSet<>();
        for (EvalRewardItem it : items) {
            if (it.getAssignUserId() != null && !it.getAssignUserId().isEmpty()) {
                ids.add(it.getAssignUserId());
            }
        }
        if (ids.isEmpty()) {
            return;
        }
        Map<String, String> idToUsername = userApi.mapEmpIdsToUsername(new ArrayList<>(ids));
        for (EvalRewardItem it : items) {
            it.setAssignUserUsername(idToUsername.getOrDefault(it.getAssignUserId(), it.getAssignUserId()));
        }
    }

    /**
     * 导出批次明细为 Excel（流式）。
     *
     * @param batchId 批次ID
     * @return Excel 字节流
     */
    public byte[] exportItems(Long batchId) {
        EvalAssignBatch batch = batchMapper.selectById(batchId);
        if (batch == null) {
            throw new PerfException(PerfErrorCode.EVAL_ASSIGN_ITEM_NOT_FOUND, batchId);
        }
        // 一次性反查工号：去重分配人 USER_ID → USERNAME，后续逐行 O(1) 内存查找
        List<String> assignerIds = itemMapper.selectDistinctAssignerIdsByBatch(batchId);
        Map<String, String> idToUsername = (assignerIds == null || assignerIds.isEmpty())
                ? Collections.emptyMap()
                : userApi.mapEmpIdsToUsername(assignerIds);
        DateTimeFormatter dtf = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
        SXSSFWorkbook wb = new SXSSFWorkbook(100);
        try {
            Sheet sheet = wb.createSheet("奖励分配明细");
            Row header = sheet.createRow(0);
            String[] cols = {"被分配人工号", "被分配人姓名", "部门名称", "原始值", "兑现值",
                    "分配合计", "分配人工号", "分配值", "提交状态", "提交时间"};
            for (int i = 0; i < cols.length; i++) {
                header.createCell(i).setCellValue(cols[i]);
            }
            int rowIdx = 1;
            int offset = 0;
            while (true) {
                List<EvalRewardItem> items = itemMapper.selectByBatchId(batchId, offset, EXPORT_PAGE_SIZE);
                if (items == null || items.isEmpty()) {
                    break;
                }
                for (EvalRewardItem it : items) {
                    Row row = sheet.createRow(rowIdx++);
                    String assigner = idToUsername.getOrDefault(it.getAssignUserId(), it.getAssignUserId());
                    row.createCell(0).setCellValue(it.getBeAssignedUserId() == null ? "" : it.getBeAssignedUserId());
                    row.createCell(1).setCellValue(it.getBeAssignedUserName() == null ? "" : it.getBeAssignedUserName());
                    row.createCell(2).setCellValue(it.getDeptName() == null ? "" : it.getDeptName());
                    row.createCell(3).setCellValue(it.getOriginalValue() == null ? "" : it.getOriginalValue().toPlainString());
                    row.createCell(4).setCellValue(it.getCashValue() == null ? "" : it.getCashValue().toPlainString());
                    row.createCell(5).setCellValue(it.getAssignTotal() == null ? "" : it.getAssignTotal().toPlainString());
                    row.createCell(6).setCellValue(assigner == null ? "" : assigner);
                    row.createCell(7).setCellValue(it.getAssignValue() == null ? "" : it.getAssignValue().toPlainString());
                    row.createCell(8).setCellValue(it.getSubmitted() != null && it.getSubmitted() == 1 ? "已提交" : "未提交");
                    row.createCell(9).setCellValue(it.getSubmitTime() == null ? "" : it.getSubmitTime().format(dtf));
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
            log.error("[EvalRewardAdminService.exportItems] 导出失败 batchId={}", batchId, e);
            throw new PerfException(PerfErrorCode.EVAL_IMPORT_FILE_INVALID, "Excel 生成失败");
        } finally {
            wb.dispose();
            try {
                wb.close();
            } catch (IOException ignore) {
                // 关闭异常不影响已生成字节流
            }
        }
    }
}
