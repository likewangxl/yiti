package com.bank.branch.platform.report.service.screen;

import com.bank.branch.platform.report.dto.req.ScreenDataReqDTO;
import com.bank.branch.platform.report.dto.req.ScreenDatasourceSaveReqDTO;
import com.bank.branch.platform.report.dto.req.ScreenTryRunReqDTO;
import com.bank.branch.platform.report.dto.resp.ScreenDataRespDTO;
import com.bank.branch.platform.report.dto.resp.ScreenDatasourceRespDTO;
import com.bank.branch.platform.report.dto.resp.ScreenKpiSchemeRespDTO;

import java.util.List;

/**
 * 大屏数据源管理服务.
 */
public interface ScreenDatasourceService {

    /** 列表（可按能力标签/名称关键字过滤） */
    List<ScreenDatasourceRespDTO> list(String dsType, String keyword);

    /** 新建（返回 id）；WIDE_TABLE 保存时翻译槽位快照 */
    Long save(ScreenDatasourceSaveReqDTO req);

    /** 更新 */
    void update(Long id, ScreenDatasourceSaveReqDTO req);

    /** 删除（被区块引用时 RPT-43007 拒绝） */
    void delete(Long id);

    /** 配置态试跑（LIMIT 10，高危审计） */
    ScreenDataRespDTO tryRun(ScreenTryRunReqDTO req);

    /** KPI 方案下拉（仅 ACTIVE，KPI_DETAIL 数据源配置用） */
    List<ScreenKpiSchemeRespDTO> listKpiSchemes();

    /** 运行时统一取数（Controller 唯一入口，内部完成实体加载） */
    ScreenDataRespDTO queryData(ScreenDataReqDTO req);
}
