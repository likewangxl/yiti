package com.bank.branch.platform.performance.facade;

import com.bank.branch.platform.performance.api.AllocApi;
import com.bank.branch.platform.performance.api.dto.AllocSummaryDTO;
import com.bank.branch.platform.performance.api.dto.AllocVersionDTO;
import com.bank.branch.platform.performance.api.dto.CustAllocRelationDTO;
import com.bank.branch.platform.performance.entity.CustAllocRelation;
import com.bank.branch.platform.performance.facade.assembler.AllocAssembler;
import com.bank.branch.platform.performance.service.AllocRelationService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 客户业绩分配关系查询对外 API 实现.
 *
 * <p>V1.0 契约 (spec §5.2.7): **10 方法全部 V1.0 实现**, 无 UOE 占位.
 *
 * <p>职责: 纯 Facade 编排 —— 调 {@link AllocRelationService} + {@link AllocAssembler}
 * 做 Entity → DTO 装配, 不做额外业务逻辑 / 权限校验 / 缓存维护.
 *
 * <p>缓存策略: **Facade 层不加 {@code @Cacheable}**.
 * <ul>
 *   <li>Plan Task 5.3 (L1564) 钦定 V1.0 不实现 evict (V1.2 才做), 只做 TTL 自然过期</li>
 *   <li>Service 层已实现缓存合并 ({@code perf:alloc:cust:{custId}:{bizKind}} TTL 5 min 自然过期),
 *       Facade 再加一层缓存将形成双层语义混乱 (TTL 错位时可能返回不一致数据)</li>
 *   <li>{@code getLatestAllocVersion} / {@code getAllocVersionAt} 委托 Service 派生自
 *       {@code SysControlService.getCurrentVersion("CUST")}, 已有上游缓存支撑</li>
 *   <li>V1.2 工作流调整审批落地后, 统一重构缓存 evict, 届时再评估 Facade 是否加缓存</li>
 * </ul>
 *
 * <p>{@link #countCustomersByEmps(Set)} 签名无 {@code bizKind} 入参, Facade 实现统一传
 * {@code null} 给 Service (任一业务种类匹配即计入), 与 AllocApi 04 契约文档一致。
 *
 * <p>消费方 (V1.0): customer-marketing-center / report-analytics-center /
 * business-application-center.
 *
 * <p>调用约束: 所有方法为只读同步调用, P95 &lt; 50 ms; 批量上限 500.
 */
@Service
@RequiredArgsConstructor
public class AllocApiImpl implements AllocApi {

    private final AllocRelationService allocRelationService;
    private final com.bank.branch.platform.performance.service.adjust.AllocAdjustPreviewService allocAdjustPreviewService;

    // ==================== 基础查询 ====================

    /**
     * 查询客户当前有效的分配关系.
     *
     * @param custId  客户 ID
     * @param bizKind 业务种类, null 表示全部
     * @return 分配关系 DTO 列表, 可能为空列表, 不会返回 null
     */
    @Override
    public List<CustAllocRelationDTO> getCurrentAllocations(String custId, String bizKind) {
        return AllocAssembler.toDtoList(allocRelationService.getCurrentAllocations(custId, bizKind));
    }

    /**
     * 查询客户在指定日期的分配关系 (历史快照).
     */
    @Override
    public List<CustAllocRelationDTO> getAllocationHistory(String custId, LocalDate asOfDate) {
        return AllocAssembler.toDtoList(allocRelationService.getAllocationHistory(custId, asOfDate));
    }

    /**
     * 查询某员工名下当前负责的客户列表 (Service 层已应用数据范围过滤).
     */
    @Override
    public List<CustAllocRelationDTO> listCustomersByEmp(String empId, String bizKind) {
        return AllocAssembler.toDtoList(allocRelationService.listCustomersByEmp(empId, bizKind));
    }

    // ==================== 批量查询 ====================

    /**
     * 批量查询多个客户的当前分配关系.
     *
     * <p>Service 层返回 {@code Map<custId, List<CustAllocRelation>>} (已做缓存合并 + 空列表
     * 穿透防护), Facade 负责逐 entry 将 Entity 列表装配为 DTO 列表, 保留所有 key (含空列表).
     *
     * @param custIds 客户 ID 集合, 上限 500
     * @param bizKind 业务种类, null 表示全部
     * @return Map&lt;custId, List&lt;CustAllocRelationDTO&gt;&gt;
     */
    @Override
    public Map<String, List<CustAllocRelationDTO>> batchGetCurrentAllocations(Set<String> custIds, String bizKind) {
        Map<String, List<CustAllocRelation>> raw = allocRelationService.batchGetCurrentAllocations(custIds, bizKind);
        if (raw == null || raw.isEmpty()) {
            return new HashMap<>(0);
        }
        Map<String, List<CustAllocRelationDTO>> result = new HashMap<>(raw.size());
        for (Map.Entry<String, List<CustAllocRelation>> e : raw.entrySet()) {
            result.put(e.getKey(), AllocAssembler.toDtoList(e.getValue()));
        }
        return result;
    }

    /**
     * 批量查询多个员工名下的客户数汇总.
     *
     * <p>AllocApi 签名无 bizKind, Facade 统一传 {@code null} 给 Service (任一业务种类匹配).
     *
     * @param empIds 员工工号集合, 上限 500
     * @return Map&lt;empId, 客户数 (去重后)&gt;
     */
    @Override
    public Map<String, Long> countCustomersByEmps(Set<String> empIds) {
        return allocRelationService.countCustomersByEmps(empIds, null);
    }

    /**
     * 批量查询多个员工的分配关系汇总 (供 report 聚合报表).
     *
     * <p>Service 已直接返回 {@link AllocSummaryDTO}, Facade 透传即可。
     */
    @Override
    public List<AllocSummaryDTO> batchSummaryByEmps(Set<String> empIds, String bizKind, LocalDate asOfDate) {
        return allocRelationService.batchSummaryByEmps(empIds, bizKind, asOfDate);
    }

    // ==================== 快速判定 ====================

    /**
     * 判断某员工对某客户是否存在当前有效的分配关系.
     */
    @Override
    public boolean hasAllocation(String empId, String custId, String bizKind) {
        return allocRelationService.hasAllocation(empId, custId, bizKind);
    }

    /**
     * 统计某员工当前负责的去重客户数.
     */
    @Override
    public long countCustomersOfEmp(String empId, String bizKind) {
        return allocRelationService.countCustomersOfEmp(empId, bizKind);
    }

    // ==================== 版本查询 ====================

    /**
     * 查询当前最新的分配关系版本号.
     */
    @Override
    public AllocVersionDTO getLatestAllocVersion(String bizKind) {
        return allocRelationService.getLatestAllocVersion(bizKind);
    }

    /**
     * 查询某时间点生效的分配关系版本号.
     */
    @Override
    public AllocVersionDTO getAllocVersionAt(String bizKind, LocalDate asOfDate) {
        return allocRelationService.getAllocVersionAt(bizKind, asOfDate);
    }

    // ==================== 原业绩分配预览（调整申请页面） ====================

    /**
     * 查询客户「原业绩分配」预览（RULE + ACCOUNT 各取审批通过的最后一条申请明细）.
     */
    @Override
    public List<com.bank.branch.platform.performance.api.dto.AllocAdjustPreviewItemDTO> getLastApprovedAllocPreview(String custNo) {
        return allocAdjustPreviewService.getLastApprovedAllocPreview(custNo);
    }
}
