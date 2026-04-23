package com.bank.branch.platform.performance.service.export.impl;

import com.bank.branch.platform.performance.entity.PerfExportTask;
import com.bank.branch.platform.performance.mapper.CustAllocRelationMapper;
import com.bank.branch.platform.performance.service.export.ExportStrategy;
import io.minio.MinioClient;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * 分配关系导出策略（V1.2 Task Q6.3 Red 占位）.
 *
 * <p>Red 阶段仅提供 exportType 路由与 execute 占位；
 * Green 阶段完成 params 解析 + 分配关系查询 + easyexcel 写 + 上传 MinIO。
 */
@Slf4j
@Component
public class AllocExportStrategy implements ExportStrategy {

    @SuppressWarnings("unused")
    private final CustAllocRelationMapper allocRelationMapper;
    @SuppressWarnings("unused")
    private final MinioClient minioClient;
    @SuppressWarnings("unused")
    private final String minioBucketName;

    public AllocExportStrategy(CustAllocRelationMapper allocRelationMapper,
                               MinioClient minioClient,
                               String minioBucketName) {
        this.allocRelationMapper = allocRelationMapper;
        this.minioClient = minioClient;
        this.minioBucketName = minioBucketName;
    }

    @Override
    public String exportType() {
        return "ALLOC";
    }

    @Override
    public int execute(PerfExportTask task) {
        throw new UnsupportedOperationException("Q6.3 Green 交付");
    }
}
