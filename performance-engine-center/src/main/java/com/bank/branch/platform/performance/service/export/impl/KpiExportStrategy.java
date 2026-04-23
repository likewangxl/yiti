package com.bank.branch.platform.performance.service.export.impl;

import com.bank.branch.platform.performance.entity.PerfExportTask;
import com.bank.branch.platform.performance.mapper.KpiResultMapper;
import com.bank.branch.platform.performance.service.export.ExportStrategy;
import io.minio.MinioClient;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * KPI 结果导出策略（V1.2 Task Q6.2 Red 占位）.
 *
 * <p>Red 阶段仅提供 exportType 路由字符串与 execute 占位实现，
 * Green 阶段完成：params 解析 + 数据查询 + easyexcel 写 Excel + 上传 MinIO。
 */
@Slf4j
@Component
public class KpiExportStrategy implements ExportStrategy {

    @SuppressWarnings("unused")
    private final KpiResultMapper kpiResultMapper;
    @SuppressWarnings("unused")
    private final MinioClient minioClient;
    @SuppressWarnings("unused")
    private final String minioBucketName;

    public KpiExportStrategy(KpiResultMapper kpiResultMapper,
                             MinioClient minioClient,
                             String minioBucketName) {
        this.kpiResultMapper = kpiResultMapper;
        this.minioClient = minioClient;
        this.minioBucketName = minioBucketName;
    }

    @Override
    public String exportType() {
        return "KPI";
    }

    @Override
    public int execute(PerfExportTask task) {
        throw new UnsupportedOperationException("Q6.2 Green 交付");
    }
}
