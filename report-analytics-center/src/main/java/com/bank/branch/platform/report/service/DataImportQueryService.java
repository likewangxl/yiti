package com.bank.branch.platform.report.service;

import com.bank.branch.platform.common.web.PageRequest;
import com.bank.branch.platform.common.web.PageResult;
import com.bank.branch.platform.report.dto.req.DataImportQueryReqDTO;
import com.bank.branch.platform.report.dto.resp.DataImportBatchVO;
import com.bank.branch.platform.report.dto.resp.DataImportDataVO;

import java.util.Map;

/**
 * 数据导入查询服务（只读，历史数据查询 - 数据导入查询）。
 * <p>列表来自 amas_dt_import_sup 按批次聚合；批次数据由 sup（表头）+ details（单元格）透视而成。</p>
 */
public interface DataImportQueryService {

    /** 分页查询导入批次列表（创建时间倒序，顶部查询项过滤）. */
    PageResult<DataImportBatchVO> pageBatches(DataImportQueryReqDTO req, PageRequest page);

    /**
     * 取某批次的透视数据（动态表头 + 当前页数据行 + 总行数；查看弹框分页用）.
     *
     * @param filters 列值模糊过滤 {DT_TITLE_NO -> 关键字}，按整行 AND 匹配；为空则不过滤
     */
    DataImportDataVO batchData(String batchNum, PageRequest page, Map<String, String> filters);

    /** 导出整个批次为 Excel（全部行，不分页）. */
    byte[] exportExcel(String batchNum);
}
