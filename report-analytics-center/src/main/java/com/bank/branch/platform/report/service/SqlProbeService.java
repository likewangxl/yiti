package com.bank.branch.platform.report.service;

import com.bank.branch.platform.common.web.PageRequest;
import com.bank.branch.platform.common.web.PageResult;
import com.bank.branch.platform.report.dto.req.SqlProbeExportReqDTO;
import com.bank.branch.platform.report.dto.req.SqlProbeExecuteReqDTO;
import com.bank.branch.platform.report.dto.resp.SchemaWhitelistRespDTO;
import com.bank.branch.platform.report.dto.resp.SqlProbeExecuteRespDTO;
import com.bank.branch.platform.report.dto.resp.SqlProbeExportFileDTO;
import com.bank.branch.platform.report.dto.resp.SqlProbeExportTaskRespDTO;
import com.bank.branch.platform.report.dto.resp.SqlProbeHistoryRespDTO;

import java.util.List;

/**
 * SQL 探查服务（D 章 4 接口，Task M4.2.1 + M4.3.x）.
 *
 * <p>能力清单（plan L2620-L2891）：
 * <ul>
 *   <li>D.1 {@link #execute(SqlProbeExecuteReqDTO)} — 执行受控 SQL（角色 R_BACK_TECH + 双写审计）</li>
 *   <li>D.2 {@link #queryHistory(PageRequest)} — 分页查本人历史</li>
 *   <li>D.3 {@link #getHistoryDetail(String)} — 历史详情</li>
 *   <li>D.4 {@link #getSchemaWhitelist()} — 白名单展示</li>
 * </ul>
 */
public interface SqlProbeService {

    /**
     * D.1 执行 SQL 探查.
     *
     * @param req 入参（含 sql + remark + exportCount）
     * @return 执行结果（含 historyId / rows / columns / executionTimeMs）
     */
    SqlProbeExecuteRespDTO execute(SqlProbeExecuteReqDTO req);

    /**
     * D.2 分页查询本人历史.
     *
     * @param page 分页参数
     * @return 分页历史
     */
    PageResult<SqlProbeHistoryRespDTO> queryHistory(PageRequest page);

    /**
     * D.3 单条历史详情（仅本人可查）.
     *
     * @param id 历史 ID
     * @return 详情
     */
    SqlProbeHistoryRespDTO getHistoryDetail(String id);

    /**
     * D.4 白名单展示.
     *
     * @return 白名单 + 禁用关键字 + 上限配置
     */
    SchemaWhitelistRespDTO getSchemaWhitelist();

    /**
     * D.5 创建 SQL 探查「异步下载」任务.
     *
     * <p>访问控制交由菜单授权；SQL 校验标准化通过后插入 RUNNING 任务并提交后台线程执行，
     * 立即返回任务 ID，前端凭此轮询任务列表。成品上传治理中心 OBS，FILE_CONTENT 仅保存兼容引用。</p>
     *
     * @param req 入参（含 sql + remark）
     * @return 任务 ID
     */
    String createExport(SqlProbeExportReqDTO req);

    /**
     * D.6 查询本人 SQL 探查导出任务列表（按创建时间 DESC，最多近 N 条；不含文件内容）.
     *
     * @return 任务列表
     */
    List<SqlProbeExportTaskRespDTO> listExportTasks();

    /**
     * D.7 下载导出文件：校验归属（仅本人）+ 状态（成功）后取出 xlsx 字节.
     *
     * @param taskId 任务 ID
     * @return 文件名 + 字节内容
     */
    SqlProbeExportFileDTO getExportFile(String taskId);
}
