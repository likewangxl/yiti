package com.bank.branch.platform.report.service;

import com.bank.branch.platform.common.web.PageResult;
import com.bank.branch.platform.report.entity.RptFreeReportBatch;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.util.Map;

/**
 * KPI/积分自由报表服务。
 * <p>支持 Excel 导入动态列数据 + 按批次查询 + 数据权限过滤 + 原文件下载/删除。</p>
 */
public interface FreeReportService {

    /**
     * 导入 Excel（同 reportName 覆盖旧数据）。
     *
     * @param reportName 报表名称（用于同名覆盖判断）
     * @param file       Excel 文件
     * @param empId      导入人工号
     * @param empName    导入人姓名
     * @return 批次 ID
     */
    String importExcel(String reportName, MultipartFile file, String empId, String empName);

    /**
     * 查询批次数据（分页 + 姓名搜索 + 数据权限）。
     *
     * @param batchId  批次 ID
     * @param keyword  姓名/工号搜索
     * @param scopeEmpId 数据权限：SELF 时传当前 empId，ALL/ORG_SUBTREE 传 null
     * @param scopeOrgCodes 数据权限：ORG_SUBTREE 时传机构代码列表，SELF/ALL 传 null
     * @param pageNo   页码
     * @param pageSize 每页
     * @return 行数据（含 col_1/col_2 + dataJson 解析后的 Map）
     */
    PageResult<Map<String, Object>> queryData(String batchId, String keyword,
                                               String empNo, String empName,
                                               String rowMode, String selfEmpNo, String selfName,
                                               List<String> scopeOrgCodes,
                                               int pageNo, int pageSize);

    /**
     * 获取批次的列定义。
     */
    List<Map<String, String>> getColumns(String batchId);

    /**
     * 查询导入批次列表（支持时间筛选）。
     * @param includeDisabled true=含禁用文件（操作人）；false=仅启用文件（其余角色）
     */
    List<RptFreeReportBatch> listBatches(String keyword, java.time.LocalDate dateFrom, java.time.LocalDate dateTo,
                                        boolean includeDisabled);

    /** 获取批次状态（SUCCESS / DISABLED），批次不存在返回 null */
    String getBatchStatus(String batchId);

    /**
     * 导出按行级数据范围过滤后的 Excel（下载用，与 /data 同一套 rowMode 过滤，避免下载泄露全表）。
     * @return xlsx 字节流
     */
    byte[] exportFilteredExcel(String batchId, String rowMode, String selfEmpNo, String selfName,
                               java.util.List<String> orgCodes);

    /** 更新批次状态（禁用/启用） */
    void updateBatchStatus(String batchId, String status);

    /**
     * 获取原始文件下载 URL（MinIO 预签名）。
     */
    String getDownloadUrl(String batchId);

    /** 获取批次原始文件名 */
    String getBatchFileName(String batchId);

    /** 获取批次关联的 file_object_id，供下载入口直接调 FileApi.getFilePath 读流（绕开 governance 通用下载 RBAC） */
    String getBatchFileObjectKey(String batchId);

    /**
     * 删除批次 + 关联行数据 + MinIO 文件。
     */
    void deleteBatch(String batchId);
}
