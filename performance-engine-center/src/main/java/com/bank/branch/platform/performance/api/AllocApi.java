package com.bank.branch.platform.performance.api;

import com.bank.branch.platform.performance.api.dto.AllocAdjustPreviewItemDTO;
import com.bank.branch.platform.performance.api.dto.AllocSummaryDTO;
import com.bank.branch.platform.performance.api.dto.AllocVersionDTO;
import com.bank.branch.platform.performance.api.dto.CustAllocRelationDTO;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 客户业绩分配关系查询 API (10 方法, V1.0 全部实现).
 *
 * <p>职责边界:
 * <ul>
 *   <li>只读查询当前或历史分配关系, 不提供任何写操作</li>
 *   <li>所有写操作 (新增/调整分配) 通过内部 AllocAdjustService 经工作流审批 (V1.2 交付)</li>
 *   <li>数据源为 cust_alloc_relation 表 + sys_control 版本控制</li>
 * </ul>
 *
 * <p>调用方: customer-marketing-center / report-analytics-center / business-application-center.
 *
 * <p>调用约束: 所有方法为只读同步调用, P95 < 50 ms; 批量上限 500.
 */
public interface AllocApi {

    /* ==================== 基础查询 ==================== */

    /**
     * 查询客户当前有效的分配关系.
     *
     * @param custId   客户 ID, 不可为 null
     * @param bizKind  业务种类, null 表示全部
     * @return 分配关系列表, 可能为空列表, 不会返回 null
     */
    List<CustAllocRelationDTO> getCurrentAllocations(String custId, String bizKind);

    /**
     * 查询客户在指定日期的分配关系 (历史快照).
     */
    List<CustAllocRelationDTO> getAllocationHistory(String custId, LocalDate asOfDate);

    /**
     * 查询某员工名下当前负责的客户列表 (当前有效分配).
     */
    List<CustAllocRelationDTO> listCustomersByEmp(String empId, String bizKind);

    /* ==================== 批量查询 ==================== */

    /**
     * 批量查询多个客户的当前分配关系 (避免 N+1).
     *
     * @param custIds 客户 ID 集合, 不可为空, 上限 500
     * @return Key=custId, Value=该客户的分配关系列表; 缺失客户不在 Map 中
     */
    Map<String, List<CustAllocRelationDTO>> batchGetCurrentAllocations(Set<String> custIds, String bizKind);

    /**
     * 批量查询多个员工名下的客户数汇总.
     *
     * @param empIds  员工工号集合, 上限 500
     * @return Key=empId, Value=客户数 (去重后)
     */
    Map<String, Long> countCustomersByEmps(Set<String> empIds);

    /**
     * 批量查询多个员工的分配关系汇总 (供 report 聚合报表).
     *
     * @param empIds   员工工号集合, 上限 500
     * @param asOfDate 截止日期, null 表示当前最新
     */
    List<AllocSummaryDTO> batchSummaryByEmps(Set<String> empIds, String bizKind, LocalDate asOfDate);

    /* ==================== 快速判定 ==================== */

    /**
     * 判断某员工对某客户是否存在当前有效的分配关系.
     *
     * @param bizKind 业务种类, null 表示任一业务种类匹配即视为有效
     */
    boolean hasAllocation(String empId, String custId, String bizKind);

    /**
     * 统计某员工当前负责的客户总数.
     */
    long countCustomersOfEmp(String empId, String bizKind);

    /* ==================== 版本查询 ==================== */

    /**
     * 查询当前最新的分配关系版本号.
     *
     * @param bizKind 业务种类 (V1.0 可为 null)
     * @return 版本信息, 若无任何版本返回 null
     */
    AllocVersionDTO getLatestAllocVersion(String bizKind);

    /**
     * 查询某时间点生效的分配关系版本号.
     */
    AllocVersionDTO getAllocVersionAt(String bizKind, LocalDate asOfDate);

    /* ==================== 原业绩分配预览（调整申请页面） ==================== */

    /**
     * 查询客户「原业绩分配」预览：分别取「按规则分配(RULE)」与「按账号分配(ACCOUNT)」两个维度下
     * <b>审批通过(APPROVED)的最后一条</b>分配关系调整申请，关联其调整明细返回。
     *
     * <p>数据源为 {@code PERF_ALLOC_ADJUST_APPLY} + {@code PERF_ALLOC_ADJUST_ITEM}（非
     * {@code cust_alloc_relation}），供审批/新增调整申请页面的「原业绩分配」模块展示。
     * 每项已按员工工号补全 username / 中文姓名 / 机构号 / 机构名称。
     *
     * @param custNo 客户编号（业务编号，内部解析为客户主键后匹配 apply.cust_id）
     * @return 预览项列表，可能为空列表，不会返回 null
     */
    List<AllocAdjustPreviewItemDTO> getLastApprovedAllocPreview(String custNo);
}
