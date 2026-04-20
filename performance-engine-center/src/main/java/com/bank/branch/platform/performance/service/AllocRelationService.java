package com.bank.branch.platform.performance.service;

import com.bank.branch.platform.auth.api.BizScopeApi;
import com.bank.branch.platform.auth.api.CurrentUserApi;
import com.bank.branch.platform.performance.api.dto.AllocSummaryDTO;
import com.bank.branch.platform.performance.api.dto.AllocVersionDTO;
import com.bank.branch.platform.performance.entity.CustAllocRelation;
import com.bank.branch.platform.performance.mapper.CustAllocRelationMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 客户业绩分配关系 Service（V1.0 只读）.
 *
 * <p>Task 5.2 骨架阶段：10 方法全部抛 {@link UnsupportedOperationException}，
 * 等 Task 5.2 绿阶段替换为真实实现.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AllocRelationService {

    private final CustAllocRelationMapper allocMapper;
    private final SysControlService sysControlService;
    private final CurrentUserApi currentUserApi;
    private final BizScopeApi bizScopeApi;
    private final RedisTemplate<String, Object> redisTemplate;

    /**
     * 查询客户当前有效的分配关系（entity 列表，facade 负责装配 DTO）.
     */
    @Transactional(readOnly = true)
    public List<CustAllocRelation> getCurrentAllocations(String custId, String bizKind) {
        throw new UnsupportedOperationException("Task 5.2 green 阶段实现");
    }

    /**
     * 查询客户在指定日期的分配关系（历史快照）.
     */
    @Transactional(readOnly = true)
    public List<CustAllocRelation> getAllocationHistory(String custId, LocalDate asOfDate) {
        throw new UnsupportedOperationException("Task 5.2 green 阶段实现");
    }

    /**
     * 查询某员工名下当前负责的客户列表（含数据范围过滤）.
     */
    @Transactional(readOnly = true)
    public List<CustAllocRelation> listCustomersByEmp(String empId, String bizKind) {
        throw new UnsupportedOperationException("Task 5.2 green 阶段实现");
    }

    /**
     * 批量查询多个客户的当前分配关系（Redis 缓存合并）.
     */
    @Transactional(readOnly = true)
    public Map<String, List<CustAllocRelation>> batchGetCurrentAllocations(Set<String> custIds, String bizKind) {
        throw new UnsupportedOperationException("Task 5.2 green 阶段实现");
    }

    /**
     * 批量查询多个员工名下的客户数汇总.
     */
    @Transactional(readOnly = true)
    public Map<String, Long> countCustomersByEmps(Set<String> empIds, String bizKind) {
        throw new UnsupportedOperationException("Task 5.2 green 阶段实现");
    }

    /**
     * 批量查询多个员工的分配关系汇总（供 report 聚合报表）.
     */
    @Transactional(readOnly = true)
    public List<AllocSummaryDTO> batchSummaryByEmps(Set<String> empIds, String bizKind, LocalDate asOfDate) {
        throw new UnsupportedOperationException("Task 5.2 green 阶段实现");
    }

    /**
     * 判断某员工对某客户是否存在当前有效的分配关系.
     */
    @Transactional(readOnly = true)
    public boolean hasAllocation(String empId, String custId, String bizKind) {
        throw new UnsupportedOperationException("Task 5.2 green 阶段实现");
    }

    /**
     * 统计某员工当前负责的客户总数.
     */
    @Transactional(readOnly = true)
    public long countCustomersOfEmp(String empId, String bizKind) {
        throw new UnsupportedOperationException("Task 5.2 green 阶段实现");
    }

    /**
     * 查询当前最新的分配关系版本号（派生自 sys_control CUST 维度）.
     */
    @Transactional(readOnly = true)
    public AllocVersionDTO getLatestAllocVersion(String bizKind) {
        throw new UnsupportedOperationException("Task 5.2 green 阶段实现");
    }

    /**
     * 查询某时间点生效的分配关系版本号.
     */
    @Transactional(readOnly = true)
    public AllocVersionDTO getAllocVersionAt(String bizKind, LocalDate asOfDate) {
        throw new UnsupportedOperationException("Task 5.2 green 阶段实现");
    }
}
