package com.bank.branch.platform.redengine.service;

import com.bank.branch.platform.redengine.api.dto.ReTaskExportReqDTO;
import com.bank.branch.platform.redengine.api.dto.ReTaskExportRespDTO;

/** 任务详情异步 ZIP/Excel 导出服务。 */
public interface ReTaskExportService {

    /** 创建一个幂等导出作业并异步提交。 */
    ReTaskExportRespDTO createExport(Long taskId, ReTaskExportReqDTO request, String operatorId);

    /** 查询导出作业状态；查询前需重新校验任务数据范围。 */
    ReTaskExportRespDTO getStatus(String exportId, String operatorId);

    /** 授权读取已完成的 ZIP 产物。 */
    byte[] download(String exportId, String operatorId);
}
