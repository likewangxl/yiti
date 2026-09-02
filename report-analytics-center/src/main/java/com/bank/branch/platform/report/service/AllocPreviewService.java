package com.bank.branch.platform.report.service;

import com.bank.branch.platform.performance.api.AllocApi;
import com.bank.branch.platform.performance.api.dto.AllocAdjustPreviewItemDTO;
import com.bank.branch.platform.report.controller.dto.AllocPreviewRespDTO;
import com.bank.branch.platform.report.controller.dto.AllocPreviewRespDTO.AllocItem;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

/**
 * 业绩调整分配预览服务（原业绩分配）.
 *
 * <p>只读委托：调用 perf 的 {@link AllocApi#getLastApprovedAllocPreview(String, String)}，
 * 从 {@code CUST_ALLOC_RELATION.is_original='2'} 候选中按来源批次取最新一批全部关系，装配为前端展示 DTO。
 * report 模块不直接访问 performance 私有表，严格走公开 *Api（架构规约跨模块红线）。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AllocPreviewService {

    private final AllocApi allocApi;

    /**
     * 查询分配预览数据（原业绩分配）.
     *
     * @param custNo   客户编号
     * @param allocDim 当前申请分配维度（ACCOUNT 只在 ACCOUNT 候选中取最新批次；RULE/null 在 RULE+ACCOUNT 候选中取整体最新批次）
     * @return 预览结果（含原业绩分配列表）
     */
    public AllocPreviewRespDTO preview(String custNo, String allocDim) {
        AllocPreviewRespDTO resp = new AllocPreviewRespDTO();

        List<AllocAdjustPreviewItemDTO> previewItems = allocApi.getLastApprovedAllocPreview(custNo, allocDim);
        List<AllocItem> allocList = new ArrayList<>();
        if (previewItems != null) {
            for (AllocAdjustPreviewItemDTO src : previewItems) {
                AllocItem item = new AllocItem();
                item.setAcctNo(src.getAccountNo());
                item.setAllocDim(src.getAllocDim());
                item.setUsername(src.getUsername());
                item.setEmpChnName(src.getEmpChnName());
                item.setOrgCode(src.getOrgCode());
                item.setOrgName(src.getOrgName());
                item.setRatio(src.getRatio() != null ? src.getRatio().toPlainString() : null);
                allocList.add(item);
            }
        }

        resp.setAllocList(allocList);
        resp.setHasData(!allocList.isEmpty());
        return resp;
    }
}
