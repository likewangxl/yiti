package com.bank.branch.platform.report.service;

import com.bank.branch.platform.report.dto.resp.SavedQueryDetailRespDTO;
import com.bank.branch.platform.report.dto.resp.SavedQuerySummaryDTO;

import java.util.List;

/**
 * 查询方案保存服务.
 *
 * <p>职责：
 * <ul>
 *   <li>每用户最多 10 条方案（超限自动删最旧）</li>
 *   <li>仅查/改/删本人方案（DataScope SELF）</li>
 *   <li>PUT 走乐观锁 version 字段</li>
 * </ul>
 */
public interface SavedQueryService {

    /**
     * 列表查询当前用户的方案，可按 dim 筛选.
     *
     * @param dim EMP/ORG/CUST，null 表示不过滤
     * @return 方案概要列表
     */
    List<SavedQuerySummaryDTO> listMine(String dim);

    /**
     * 获取单个方案详情（仅本人）.
     *
     * @param id 方案 ID
     * @return 详情
     */
    SavedQueryDetailRespDTO getDetail(String id);
}
