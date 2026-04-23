package com.bank.branch.platform.performance.service.export.impl;

import com.bank.branch.platform.performance.entity.PerfExportTask;
import com.bank.branch.platform.performance.mapper.KpiResultMapper;
import com.bank.branch.platform.performance.service.export.ExportStrategy;
import io.minio.MinioClient;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * KPI 明细导出策略（V1.2 Task Q6.3 Red 占位）.
 *
 * <p>逻辑：查询 KPI 结果，解析 detail_json 的 items 数组拆行，每个 item 一行。
 * Red 阶段仅提供 exportType 路由与 execute 占位；
 * Green 阶段完成 params 解析 + detail_json 拆分 + easyexcel 写 + 上传 MinIO。
 */
@Slf4j
@Component
public class DetailExportStrategy implements ExportStrategy {

    @SuppressWarnings("unused")
    private final KpiResultMapper kpiResultMapper;
    @SuppressWarnings("unused")
    private final MinioClient minioClient;
    @SuppressWarnings("unused")
    private final String minioBucketName;

    public DetailExportStrategy(KpiResultMapper kpiResultMapper,
                                MinioClient minioClient,
                                String minioBucketName) {
        this.kpiResultMapper = kpiResultMapper;
        this.minioClient = minioClient;
        this.minioBucketName = minioBucketName;
    }

    @Override
    public String exportType() {
        return "DETAIL";
    }

    @Override
    public int execute(PerfExportTask task) {
        throw new UnsupportedOperationException("Q6.3 Green 交付");
    }
}
