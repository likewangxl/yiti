package com.bank.branch.platform.portal.service;

import com.alibaba.excel.EasyExcel;
import com.bank.branch.platform.common.web.exception.BizException;
import com.bank.branch.platform.portal.entity.ZhGuaranteeInfo;
import com.bank.branch.platform.portal.enums.PortalErrorCode;
import com.bank.branch.platform.portal.service.dto.GuaranteeExportRow;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.io.OutputStream;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.stream.Collectors;

/**
 * 担保信息导出服务（V1 同步导出，≤5000 行）。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class GuaranteeExportService {

    private static final long SYNC_EXPORT_THRESHOLD = 5000L;
    private static final DateTimeFormatter DT_FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private final GuaranteeService guaranteeService;

    /**
     * 导出担保信息到输出流。
     *
     * @param clientName    客户名称（可空，ids 为空时按此过滤）
     * @param ids           选中主键列表（可空，非空则仅导出选中）
     * @param output        输出流
     * @param operatorEmpId 操作人工号（日志用）
     */
    public void exportToStream(String clientName, List<Long> ids, OutputStream output, String operatorEmpId) {
        List<ZhGuaranteeInfo> entities = guaranteeService.listForExport(clientName, ids);
        if (entities.size() > SYNC_EXPORT_THRESHOLD) {
            throw new BizException(PortalErrorCode.EXPORT_ROWS_LIMIT_EXCEEDED.getCode(),
                    "V1 暂不支持异步导出，当前命中 " + entities.size() + " 行超过同步阈值 "
                            + SYNC_EXPORT_THRESHOLD + "，请增加过滤条件");
        }
        List<GuaranteeExportRow> rows = entities.stream().map(this::toRow).collect(Collectors.toList());
        EasyExcel.write(output, GuaranteeExportRow.class)
                .autoCloseStream(false)
                .sheet("担保信息")
                .doWrite(rows);
        log.info("[GuaranteeExport] operator={} exported {} rows", operatorEmpId, rows.size());
    }

    private GuaranteeExportRow toRow(ZhGuaranteeInfo e) {
        GuaranteeExportRow row = new GuaranteeExportRow();
        row.setClientName(e.getClientName());
        row.setNotionalAmount(e.getNotionalAmount());
        row.setOccupyNotionalAmount(e.getOccupyNotionalAmount());
        row.setUsableNominalSum(e.getUsableNominalSum());
        row.setLastExpire(e.getLastExpire());
        row.setUserName(e.getUserName());
        row.setCreateTime(e.getCreateTime() != null ? e.getCreateTime().format(DT_FMT) : "");
        row.setUpdateTime(e.getUpdateTime() != null ? e.getUpdateTime() : "");
        return row;
    }
}
