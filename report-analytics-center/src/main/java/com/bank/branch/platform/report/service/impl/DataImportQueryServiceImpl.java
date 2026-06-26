package com.bank.branch.platform.report.service.impl;

import com.bank.branch.platform.common.web.PageRequest;
import com.bank.branch.platform.common.web.PageResult;
import com.bank.branch.platform.common.web.exception.BizException;
import com.bank.branch.platform.report.dto.req.DataImportQueryReqDTO;
import com.bank.branch.platform.report.dto.resp.DataImportBatchVO;
import com.bank.branch.platform.report.dto.resp.DataImportDataVO;
import com.bank.branch.platform.report.entity.AmasDtImportDetail;
import com.bank.branch.platform.report.entity.AmasDtImportSup;
import com.bank.branch.platform.report.mapper.AmasDtImportDetailMapper;
import com.bank.branch.platform.report.mapper.AmasDtImportSupMapper;
import com.bank.branch.platform.report.service.DataImportQueryService;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.io.ByteArrayOutputStream;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 数据导入查询实现：只读。批次列表按 DT_BATCHNUM 聚合分页；
 * 批次数据由 sup（表头，按 DT_TITLE_SNO 排序、隐藏列 DT_ISSHOW=2 不展示）+ details（单元格）
 * 按 DT_FLAG 透视成行（DT_DETAILD_SNO 升序，保持行首次出现顺序）。
 * 查看走逻辑行 DT_FLAG 分页；导出整批生成 Excel（不分页）。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class DataImportQueryServiceImpl implements DataImportQueryService {

    private final AmasDtImportSupMapper supMapper;
    private final AmasDtImportDetailMapper detailMapper;

    @Override
    public PageResult<DataImportBatchVO> pageBatches(DataImportQueryReqDTO req, PageRequest page) {
        DataImportQueryReqDTO q = req == null ? new DataImportQueryReqDTO() : req;
        Page<DataImportBatchVO> p = new Page<>(page.getPageNo(), page.getPageSize());
        supMapper.pageBatches(p, q);
        return PageResult.of(page.getPageNo(), page.getPageSize(), p.getTotal(), p.getRecords());
    }

    @Override
    public DataImportDataVO batchData(String batchNum, PageRequest page, Map<String, String> filters) {
        DataImportDataVO vo = new DataImportDataVO();
        vo.setBatchNum(batchNum);
        vo.setColumns(new ArrayList<>());
        vo.setRows(new ArrayList<>());
        vo.setTotal(0L);
        if (!StringUtils.hasText(batchNum)) {
            return vo;
        }
        // 表头
        List<AmasDtImportSup> headers = visibleHeaders(batchNum);
        vo.setDataName(dataNameOf(headers));
        vo.setColumns(toColumns(headers));

        // 仅保留有效过滤项（列在可见表头内 + 关键字非空），避免无效列把结果清空
        Map<String, String> validFilters = sanitizeFilters(filters, headers);

        // 逻辑行分页（按列值过滤后）
        long total = detailMapper.countFlags(batchNum, validFilters);
        vo.setTotal(total);
        if (total == 0) {
            return vo;
        }
        int offset = (page.getPageNo() - 1) * page.getPageSize();
        List<String> flags = detailMapper.pageFlags(batchNum, validFilters, offset, page.getPageSize());
        if (!flags.isEmpty()) {
            vo.setRows(pivot(batchNum, flags));
        }
        return vo;
    }

    /** 过滤项清洗：仅保留可见列内、关键字去空格后非空的项. */
    private Map<String, String> sanitizeFilters(Map<String, String> filters, List<AmasDtImportSup> headers) {
        Map<String, String> valid = new LinkedHashMap<>();
        if (filters == null || filters.isEmpty()) {
            return valid;
        }
        Set<String> visibleKeys = new HashSet<>();
        for (AmasDtImportSup h : headers) {
            visibleKeys.add(h.getDtTitleNo());
        }
        for (Map.Entry<String, String> e : filters.entrySet()) {
            String k = e.getKey();
            String v = e.getValue();
            if (k != null && visibleKeys.contains(k) && StringUtils.hasText(v)) {
                valid.put(k, v.trim());
            }
        }
        return valid;
    }

    @Override
    public byte[] exportExcel(String batchNum) {
        List<AmasDtImportSup> headers = visibleHeaders(batchNum);
        List<Map<String, String>> columns = toColumns(headers);
        // 全部行（不分页、不过滤）：取所有 flag，按顺序透视
        List<String> flags = detailMapper.pageFlags(batchNum, null, 0, 1000000);
        List<Map<String, Object>> rows = flags.isEmpty() ? new ArrayList<>() : pivot(batchNum, flags);

        try (XSSFWorkbook wb = new XSSFWorkbook(); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            Sheet sheet = wb.createSheet("数据");
            Row header = sheet.createRow(0);
            for (int c = 0; c < columns.size(); c++) {
                header.createCell(c).setCellValue(columns.get(c).getOrDefault("label", ""));
            }
            int rIdx = 1;
            for (Map<String, Object> r : rows) {
                Row row = sheet.createRow(rIdx++);
                for (int c = 0; c < columns.size(); c++) {
                    Object v = r.get(columns.get(c).get("key"));
                    row.createCell(c).setCellValue(v != null ? v.toString() : "");
                }
            }
            wb.write(out);
            return out.toByteArray();
        } catch (Exception ex) {
            log.warn("[DataImport] 导出 Excel 失败 batchNum={} err={}", batchNum, ex.getMessage());
            throw new BizException("RPT-50001", "导出 Excel 失败: " + ex.getMessage());
        }
    }

    /** 取批次的可见表头（DT_ISSHOW != 2），按列序升序. */
    private List<AmasDtImportSup> visibleHeaders(String batchNum) {
        LambdaQueryWrapper<AmasDtImportSup> hw = new LambdaQueryWrapper<>();
        hw.eq(AmasDtImportSup::getDtBatchnum, batchNum)
          .and(c -> c.isNull(AmasDtImportSup::getDtIsshow).or().ne(AmasDtImportSup::getDtIsshow, "2"))
          .orderByAsc(AmasDtImportSup::getDtTitleSno);
        return supMapper.selectList(hw);
    }

    private String dataNameOf(List<AmasDtImportSup> headers) {
        return !headers.isEmpty() && StringUtils.hasText(headers.get(0).getDtName())
                ? headers.get(0).getDtName() : null;
    }

    private List<Map<String, String>> toColumns(List<AmasDtImportSup> headers) {
        List<Map<String, String>> columns = new ArrayList<>(headers.size());
        for (AmasDtImportSup h : headers) {
            Map<String, String> col = new LinkedHashMap<>();
            col.put("key", h.getDtTitleNo());
            col.put("label", h.getDtTitleName() != null ? h.getDtTitleName() : h.getDtTitleNo());
            columns.add(col);
        }
        return columns;
    }

    /** 取指定 flags 的单元格并透视成行（行顺序按 flags 顺序）. */
    private List<Map<String, Object>> pivot(String batchNum, List<String> flags) {
        LambdaQueryWrapper<AmasDtImportDetail> dw = new LambdaQueryWrapper<>();
        dw.eq(AmasDtImportDetail::getDtBatchnum, batchNum)
          .in(AmasDtImportDetail::getDtFlag, flags)
          .orderByAsc(AmasDtImportDetail::getDtDetaildSno);
        List<AmasDtImportDetail> cells = detailMapper.selectList(dw);

        Map<String, Map<String, Object>> rowMap = new LinkedHashMap<>();
        for (String flag : flags) {
            rowMap.put(flag, new LinkedHashMap<>());
        }
        for (AmasDtImportDetail cell : cells) {
            String flag = cell.getDtFlag() != null ? cell.getDtFlag() : "";
            Map<String, Object> row = rowMap.get(flag);
            if (row != null && cell.getDtTitleNo() != null) {
                row.put(cell.getDtTitleNo(), cell.getDtDetails());
            }
        }
        return new ArrayList<>(rowMap.values());
    }
}
