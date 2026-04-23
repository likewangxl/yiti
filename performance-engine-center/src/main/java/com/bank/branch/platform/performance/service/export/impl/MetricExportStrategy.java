package com.bank.branch.platform.performance.service.export.impl;

import com.bank.branch.platform.performance.entity.PerfExportTask;
import com.bank.branch.platform.performance.mapper.EmpIndexResultMapper;
import com.bank.branch.platform.performance.mapper.OrgIndexResultMapper;
import com.bank.branch.platform.performance.mapper.PerfMetricDefMapper;
import com.bank.branch.platform.performance.service.export.ExportStrategy;
import io.minio.MinioClient;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * 指标宽表结果导出策略（V1.2 Task Q6.3 Red 占位）.
 *
 * <p>Red 阶段仅提供 exportType 路由与 execute 占位；
 * Green 阶段完成 params 解析 + slot 查询 + easyexcel 写 + 上传 MinIO。
 */
@Slf4j
@Component
public class MetricExportStrategy implements ExportStrategy {

    @SuppressWarnings("unused")
    private final PerfMetricDefMapper metricDefMapper;
    @SuppressWarnings("unused")
    private final EmpIndexResultMapper empIndexResultMapper;
    @SuppressWarnings("unused")
    private final OrgIndexResultMapper orgIndexResultMapper;
    @SuppressWarnings("unused")
    private final MinioClient minioClient;
    @SuppressWarnings("unused")
    private final String minioBucketName;

    public MetricExportStrategy(PerfMetricDefMapper metricDefMapper,
                                EmpIndexResultMapper empIndexResultMapper,
                                OrgIndexResultMapper orgIndexResultMapper,
                                MinioClient minioClient,
                                String minioBucketName) {
        this.metricDefMapper = metricDefMapper;
        this.empIndexResultMapper = empIndexResultMapper;
        this.orgIndexResultMapper = orgIndexResultMapper;
        this.minioClient = minioClient;
        this.minioBucketName = minioBucketName;
    }

    @Override
    public String exportType() {
        return "METRIC";
    }

    @Override
    public int execute(PerfExportTask task) {
        throw new UnsupportedOperationException("Q6.3 Green 交付");
    }
}
